package com.banking.transectionservice.service;

import com.banking.transectionservice.client.AccountServiceClient;
import com.banking.transectionservice.dto.TransactionResponse;
import com.banking.transectionservice.dto.TransferRequest;
import com.banking.transectionservice.entity.Transaction;
import com.banking.transectionservice.entity.TransactionStatus;
import com.banking.transectionservice.entity.TransactionType;
import com.banking.transectionservice.event.TransactionCompletedEvent;
import com.banking.transectionservice.event.TransactionInitiatedEvent;
import com.banking.transectionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String,Object>kafkaTemplate;
    private final RedisTemplate<String,String> redisTemplate;

    private static  final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static  final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static  final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static  final String FRAUD_DETECTED_TOPIC= "fraud.detected";

    public TransactionResponse verifyOTP(String transactionID, String otp) {
         log.info("Otp verification for transaction");

         Transaction transaction = transactionRepository.findById(transactionID)
                 .orElseThrow(()->new RuntimeException("transaction not found"));

        String otpKey= "verification:otp" + transactionID;
        String storedOtp= redisTemplate.opsForValue().get(otpKey);
        if(storedOtp==null){
            //
            log.info("otp expired for transaction id ");
            compensateTransaction(transaction,"OTP expired - transaction cancelled and amount refunded");
            return mapToResponse(transaction);
        }

        if(!storedOtp.equals(otp)){
            log.info("Wrong OTP - blocking account and refunding amount");
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction,
                    "Wrong OTP received - transaction cancelled account blocked for security");
        }

        // otp right
        log.info("OTP verified - completing transaction: {}",transactionID);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);

        return mapToResponse(transaction);

    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository.findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(transactionRepository.findById(transactionId)
                .orElseThrow(()-> new RuntimeException("Transaction not found")));
    }

    /**
     * SAGA STEP 1- initialize transaction
     * Deduct from sender via feign
     * save transfer as processing
     * publish event to kafka for fraud detection
     * @param request
     * @return
     */
    public TransactionResponse transfer(TransferRequest request){

        log.info("SAGA START - Transfer :{} -> {} amount {}", request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),request.getAmount());

        accountServiceClient.deductBalance(request.getSenderAccountNumber(),request.getAmount());

        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as processing :{}",savedTransaction.getId());

        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC,savedTransaction.getId(),event);
        log.info("SAGA STEP 2 transaction initiated event published; {}",savedTransaction.getId());

        return mapToResponse(savedTransaction);
    }


    private void compensateTransaction(Transaction transaction,String reason){
        log.info("SAGA COMPENSATION - refunding :{} amount:{}",transaction.getSenderAccountNumber(),transaction.getAmount());

        // credit money back to sender
        accountServiceClient.creditBalance(
                transaction.getSenderAccountNumber(),
                transaction.getAmount()
        );

        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason+
                "- SAGA Compensation executed , amount refunded at"+ LocalDateTime.now());

        transactionRepository.save(transaction);

        // publish refund event
        // notification service will alert user

        Map<String,Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId",transaction.getId());
        refundEvent.put("senderAccountNumber",transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason",reason);


        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC,transaction.getId(),refundEvent);

        log.info("SAGA compensation complete");

    }


private void blockAccountAndCompensate(Transaction transaction,String reason){
        // publish fraud .detected -> account service will blocked account
    Map<String , Object> fraudEvent = new HashMap<>();
    fraudEvent.put("transactionId", transaction.getId());
    fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());
    fraudEvent.put("reason",reason);
    kafkaTemplate.send(FRAUD_DETECTED_TOPIC,transaction.getSenderAccountNumber(),fraudEvent);
    log.warn("fraud.detected published - account :{} will be blocked, kindly contact to your bank",transaction.getSenderAccountNumber());

    // SAGA compensation refund
    compensateTransaction(transaction,reason);
    }


    private void completeTransaction(Transaction transaction){
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        Map<String, Object> event = new HashMap<>();
        event.put("transactionId", transaction.getId());
        event.put("senderAccountNumber", transaction.getSenderAccountNumber());
        event.put("receiverAccountNumber", transaction.getReceiverAccountNumber());
        event.put("amount", transaction.getAmount());
        event.put("description", transaction.getDescription());

        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC,transaction.getId(),event);

        log.info("SAGA completed transaction completed: {}", transaction.getId());

    }

    public  void processCleanResult(String transactionID){
        Transaction transaction = transactionRepository.findById(transactionID)
                .orElseThrow(()->new RuntimeException("transaction not found"));

        if(transaction.getStatus()!= TransactionStatus.PROCESSING){
            log.info("Transaction not processing{}",transactionID);
            return;
        }

        log.info("transaction completed fxn triger");
        completeTransaction(transaction);
    }



    private TransactionResponse mapToResponse(Transaction transaction) {
       TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setReferenceNumber(transaction.getReferenceNumber());
        response.setSenderAccountNumber(transaction.getSenderAccountNumber());
        response.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        response.setAmount(transaction.getAmount());
        response.setType(transaction.getType());
        response.setStatus(transaction.getStatus());
        response.setDescription(transaction.getDescription());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setCompletedAt(transaction.getCompletedAt());

        return response;

    }
}
