package com.banking.notificationservice.service;

//import lombok.extern.slf4j.Slf4j;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service

public class NotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationService.class);


    @KafkaListener(topics = "transaction.otp.generated")
    public void consumeOtpGenerated(@Payload Map<String,Object> payload){

        try{
            String accountNumber = (String) payload.get("accountNumber");
            String otp= (String) payload.get("otp");
            String transactionId= (String) payload.get("transactionId");
            String amount = payload.get("amount").toString();
            String reason = (String) payload.get("reason");

            sendAlert( accountNumber,
                    "TRANSACTION VERIFICATION REQUIRED",
                    String.format("Suspicious activity detected on your account. "
                            +"Reason: %s"+"A transaction of %s is pending verification."+
                            "Your OTP is: %s. Valid for 5 minutes"+
                            "If this wasn't you ignore this message.",
                            reason,amount,otp
                            )

            );

        }
        catch(Exception e){
            log.error("error sending otp notification:{}",e.getMessage());
        }
    }

    @KafkaListener(topics = "transaction.completed")
    public  void consumeTransactionCompleted(
            @Payload Map<String,Object> payload
    ){

        try{
            String senderAccount = (String) payload.get("senderAccountNumber");
            String receiverAccount = (String) payload.get("receiverAccountNumber");
            String amount = payload.get("amount").toString();

            sendAlert( senderAccount,
                    "DEBIT ALERT",
                    String.format(
                            "%s debited from account %s",
                            amount,senderAccount
                    ));

            sendAlert( receiverAccount,
                    "CREDIT ALERT",
                    String.format(
                            "%s credited in account %s",
                            amount,receiverAccount
                    ));
        }
        catch(Exception e){
            log.error("error sending transaction notification:{}",e.getMessage());
        }

    }

    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(
            @Payload Map<String,Object> payload
    ){

        try{
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");

            sendAlert( accountNumber,
                    " SUSPICIOUS ACTIVITY DETECTED",
                    String.format(
                            "Your  account %s has been blocked"+
                                    "Reason: %s"+
                                    "Please contact your bank immediately.",
                            accountNumber,reason
                    ));

        }
        catch(Exception e){
            log.error("error sending fraud alert:{}",e.getMessage());
        }

    }

    @KafkaListener(topics = "transaction.refunded")
    public void consumeRefunded(
            @Payload Map<String,Object> payload
    ){
        try{
            String senderAccount = (String) payload.get("senderAccountNumber");
            String amount = payload.get("amount").toString();
            String reason = (String) payload.get("reason");

            sendAlert( senderAccount,
                    "REFUND ALERT",
                    String.format(
                            "Your transaction was cancelled"+
                            "%s refund to account %s"+
                            "Reason:",
                            amount,senderAccount,reason
                    ));

        }
        catch(Exception e){
            log.error("error sending refund alert:{}",e.getMessage());
        }
    }

    @KafkaListener(topics = "payment.completed")
    public void consumePaymentCompleted(
            @Payload Map<String,Object> payload
    ){
        try{
            String accountNumber = (String) payload.get("accountNumber");
            String amount = payload.get("amount").toString();

            sendAlert( accountNumber,
                    "PAYMENT SUCCESSFUL",
                    String.format(
                            "Payment %s completed. "+
                            "Razorpay ID: %s",
                            amount,payload.get("razorpayPaymentId")
                    ));

        }
        catch(Exception e){
            log.error("error sending payment success notification:{}",e.getMessage());
        }
    }

    @KafkaListener(topics = "payment.failed")
    public void consumePaymentFailed(
            @Payload Map<String,Object> payload
    ){
        try{
            String accountNumber = (String) payload.get("accountNumber");
            String amount = payload.get("amount").toString();

            sendAlert( accountNumber,
                    "PAYMENT FAILED",
                    String.format(
                            "Your payment  %s was failed. ",
                            amount
                    ));

        }
        catch(Exception e){
            log.error("error sending payment failed notification:{}",e.getMessage());
        }
    }

    private void sendAlert(String accountNumber, String subject, String message){
         log.info("----------------------------------");
        log.info("Account: {}", accountNumber);
        log.info("Subject: {}", subject);
        log.info("Message: {}", message);
        log.info("----------------------------------");

    }
}
