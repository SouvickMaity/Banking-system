# 🏦 Banking System — Microservices Architecture

A **scalable, event-driven banking system** built using **Spring Boot Microservices**, **Apache Kafka**, **Redis**, **Spring Cloud Gateway**, and **Razorpay**.

The system demonstrates real-world distributed-system concepts such as **microservices communication, event-driven architecture, Saga-style transaction compensation, fraud detection, OTP verification, rate limiting, and payment webhooks**.

---

## 🚀 Key Features

* 🏦 Account creation and management
* 💸 Account-to-account money transfer
* 🔐 OTP-based transaction verification
* ⚡ Redis-based OTP storage with expiration
* 🚦 API Gateway with rate limiting
* 🔄 Saga-style transaction workflow with compensation
* 📨 Kafka-based asynchronous communication
* 🛡️ Fraud detection service
* 🚫 Automatic account blocking for suspicious transactions
* 💰 Transaction refund/compensation
* 🔔 Event-driven notifications
* 💳 Razorpay payment integration
* 🔀 REST + OpenFeign service-to-service communication

---

# 🏗️ System Architecture

```text
                         ┌─────────────────────┐
                         │       Client        │
                         │   Frontend/Postman  │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │    API Gateway      │
                         │ Spring Cloud Gateway│
                         │                     │
                         │    Rate Limiting    │
                         └──────────┬──────────┘
                                    │
             ┌──────────────────────┼──────────────────────┐
             │                      │                      │
             ▼                      ▼                      ▼
     ┌───────────────┐      ┌───────────────┐      ┌───────────────┐
     │ Account       │      │ Transaction   │      │ Payment       │
     │ Service       │      │ Service       │      │ Service       │
     │               │      │               │      │               │
     │ Account DB    │      │ Transaction DB│      │ Payment DB    │
     └───────┬───────┘      └───────┬───────┘      └───────┬───────┘
             │                      │                       │
             │                      │                       │
             │                ┌─────▼─────┐                 │
             │                │   Kafka   │◄────────────────┘
             │                │  Broker   │
             │                └─────┬─────┘
             │                      │
             │          ┌───────────┼──────────────┐
             │          │           │              │
             ▼          ▼           ▼              ▼
       Account       Fraud       Notification    Payment
       Consumer      Detection     Service       Events
```

---

# 🔄 End-to-End Transaction Flow

Consider a customer transferring:

```text
Sender Account     : A100
Receiver Account   : B200
Transfer Amount    : ₹10,000
```

## 1️⃣ Client → API Gateway

The client sends a transfer request:

```http
POST /api/v1/transactions/transfer
```

The request first reaches the **Spring Cloud API Gateway**.

```text
Client
   │
   ▼
API Gateway
   │
   ├── Rate Limit Check
   │
   ▼
Transaction Service
```

---

## 2️⃣ API Gateway — Rate Limiting

The gateway identifies the client and checks whether the request exceeds the configured rate limit.

```text
Request
   │
   ▼
Rate Limiter
   │
   ├── Allowed ──────► Transaction Service
   │
   └── Rejected ─────► HTTP 429
```

This protects backend services from excessive requests.

---

# 3️⃣ Transaction Service

The Transaction Service receives the transfer request.

It first communicates with the Account Service using **OpenFeign**.

```text
Transaction Service
        │
        │ OpenFeign
        ▼
Account Service
```

The sender's balance is deducted.

```text
Sender Balance

₹50,000
   │
   │ - ₹10,000
   ▼
₹40,000
```

---

# 4️⃣ Create Transaction

After successfully deducting the sender's balance, the Transaction Service creates a transaction record.

Initial state:

```text
Transaction
-------------------------
Transaction ID : TX123
Sender          : A100
Receiver        : B200
Amount          : ₹10,000
Status          : PROCESSING
-------------------------
```

---

# 5️⃣ Publish Kafka Event

The Transaction Service publishes:

```text
transaction.initiated
```

to Apache Kafka.

```text
Transaction Service
        │
        │ transaction.initiated
        ▼
      Kafka
        │
        ▼
Fraud Detection Service
```

The HTTP request and fraud detection are therefore decoupled.

---

# 6️⃣ Fraud Detection

The Fraud Detection Service consumes:

```text
transaction.initiated
```

and evaluates the transaction.

```text
Kafka
  │
  ▼
Fraud Detection
  │
  ├── CLEAN
  │
  └── FRAUD
```

---

# ✅ Clean Transaction Flow

If the transaction is considered safe:

```text
CLEAN
  │
  ▼
Transaction Service
  │
  ▼
Status = COMPLETED
  │
  ▼
transaction.completed
  │
  ▼
Kafka
```

The `transaction.completed` event is consumed by multiple services.

```text
                    transaction.completed
                            │
              ┌─────────────┴─────────────┐
              │                           │
              ▼                           ▼
       Account Service             Notification Service
              │                           │
              ▼                           ▼
      Credit Receiver              Send Notifications
```

The receiver's balance is updated:

```text
Receiver Balance

₹20,000
   │
   │ + ₹10,000
   ▼
₹30,000
```

Final state:

```text
Sender   : ₹40,000
Receiver : ₹30,000
Status   : COMPLETED
```

---

# ❌ Fraudulent Transaction Flow

If the transaction is detected as suspicious:

```text
Fraud Detection
       │
       ▼
fraud.detected
       │
       ▼
Kafka
```

The Account Service consumes the event and blocks the account.

```text
Account
   │
   ▼
BLOCKED
```

But the sender's money was already deducted.

Therefore, the system performs a **compensating transaction**.

```text
Sender
₹40,000
   │
   │ + ₹10,000
   ▼
₹50,000
```

The transaction is marked as:

```text
FLAGGED
```

and a refund event can be published.

This demonstrates the **Saga pattern / compensating transaction approach**.

---

# 🔐 OTP Verification

Transactions can also involve OTP verification.

The OTP is temporarily stored in **Redis**.

```text
Transaction Service
        │
        ▼
      Redis
        │
        └── OTP + TTL
```

The client verifies the OTP using:

```http
POST /api/v1/transactions/{transactionId}/verify?otp=XXXX
```

### Correct OTP

```text
Correct OTP
     │
     ▼
Transaction COMPLETED
     │
     ▼
transaction.completed
```

### Wrong OTP

```text
Wrong OTP
    │
    ▼
fraud.detected
    │
    ├──► Block Account
    │
    └──► Compensate Transaction
```

### Expired OTP

```text
OTP Expired
     │
     ▼
Transaction Compensation
     │
     ▼
Refund Sender
```

Redis is useful here because OTPs are temporary data and can automatically expire using TTL.

---

# 📨 Kafka Event Architecture

The major events used by the system are:

```text
transaction.initiated
transaction.completed
transaction.refunded
fraud.detected
payment.completed
payment.failed
```

### Event Flow

```text
transaction.initiated
        │
        ▼
Fraud Detection


transaction.completed
        │
        ├──────────────► Account Service
        │                    │
        │                    ▼
        │               Credit Receiver
        │
        └──────────────► Notification Service
                             │
                             ▼
                       Send Notifications


fraud.detected
        │
        ├──────────────► Account Service
        │                    │
        │                    ▼
        │                Block Account
        │
        └──────────────► Compensation


transaction.refunded
        │
        ▼
Notification / Event Consumers
```

---

# 💳 Payment Flow

The Payment Service integrates with **Razorpay**.

```text
Client
   │
   │ Create Payment
   ▼
Payment Service
   │
   ▼
Razorpay
   │
   ▼
Create Order
   │
   ▼
Payment Service
   │
   ▼
Payment Database
   │
   ▼
Frontend
   │
   ▼
Razorpay Checkout
```

The payment is initially stored as:

```text
CREATED
```

---

# 🔗 Razorpay Webhook

After payment processing, Razorpay sends a webhook to the Payment Service.

```text
Razorpay
    │
    │ Webhook
    ▼
Payment Service
    │
    ├── payment.captured
    │
    └── payment.failed
```

For successful payments:

```text
payment.captured
       │
       ▼
Payment Service
       │
       ├── Update payment status
       ├── Store Razorpay payment ID
       └── Publish payment.completed
```

For failed payments:

```text
payment.failed
       │
       ▼
Payment Service
       │
       ▼
Status = FAILED
```

---

# 🔄 SAGA Pattern

The transaction workflow follows a Saga-style approach.

Instead of using one distributed database transaction:

```text
Transaction Service
        │
        ├── Deduct Sender
        │
        ├── Fraud Detection
        │
        ├── Complete Transaction
        │
        └── Credit Receiver
```

Each service manages its own operation.

If a later operation fails:

```text
Original Operation
       │
       ▼
Failure
       │
       ▼
Compensating Operation
       │
       ▼
Refund Sender
```

### Example

```text
₹50,000
   │
   │ Deduct ₹10,000
   ▼
₹40,000
   │
   │ Fraud detected
   ▼
Compensation
   │
   │ + ₹10,000
   ▼
₹50,000
```

This prevents the system from leaving the account in an inconsistent state.

---

# 🧩 Microservices

## 1. API Gateway

Responsibilities:

* Central entry point
* Request routing
* Rate limiting
* Service discovery/routing

---

## 2. Account Service

Responsibilities:

* Create accounts
* Retrieve account information
* Retrieve balance
* Debit account
* Credit account
* Block accounts

---

## 3. Transaction Service

Responsibilities:

* Create transactions
* Process transfers
* Manage transaction state
* OTP verification
* Publish Kafka events
* Saga compensation

---

## 4. Fraud Detection Service

Responsibilities:

* Consume transaction events
* Analyze transactions
* Detect suspicious activity
* Trigger fraud events

---

## 5. Notification Service

Responsibilities:

* Consume transaction events
* Generate transaction notifications
* Notify sender and receiver
* Handle fraud-related notifications

---

## 6. Payment Service

Responsibilities:

* Create Razorpay orders
* Track payment state
* Process Razorpay webhooks
* Publish payment events

---

# 🛠️ Tech Stack

| Technology             | Purpose                           |
| ---------------------- | --------------------------------- |
| Java                   | Programming Language              |
| Spring Boot            | Microservices                     |
| Spring Cloud Gateway   | API Gateway                       |
| Spring Cloud OpenFeign | Synchronous service communication |
| Apache Kafka           | Event-driven communication        |
| Redis                  | OTP / temporary data              |
| PostgreSQL / SQL       | Persistent data                   |
| Razorpay               | Payment Gateway                   |
| Docker                 | Containerization                  |
| Maven                  | Build Tool                        |
| REST API               | Service APIs                      |

---

# 📁 Project Structure

```text
Banking-system/
│
├── api-gateway/
│   ├── src/
│   └── pom.xml
│
├── account-service/
│   ├── src/
│   └── pom.xml
│
├── transection-service/
│   ├── src/
│   └── pom.xml
│
├── payment-service/
│   ├── src/
│   └── pom.xml
│
├── fraud-detection-service/
│   ├── src/
│   └── pom.xml
│
├── notification-service/
│   ├── src/
│   └── pom.xml
│
├── docker-compose.yml
│
└── README.md
```

---

# 🔌 Important APIs

## Account Service

```http
POST /api/v1/accounts
```

Create an account.

```http
GET /api/v1/accounts/{accountNumber}
```

Get account details.

```http
GET /api/v1/accounts/{accountNumber}/balance
```

Get account balance.

```http
PUT /api/v1/accounts/{accountNumber}/deduct
```

Deduct balance.

```http
PUT /api/v1/accounts/{accountNumber}/credit
```

Credit balance.

```http
PUT /api/v1/accounts/{accountNumber}/block
```

Block an account.

---

## Transaction Service

```http
POST /api/v1/transactions/transfer
```

Create a transfer.

```http
POST /api/v1/transactions/{transactionId}/verify?otp=XXXX
```

Verify transaction OTP.

---

# 🐳 Running the Project

## Prerequisites

Install:

* Java 17+
* Maven
* Docker
* Docker Compose
* PostgreSQL
* Redis
* Apache Kafka

---

## Clone Repository

```bash
git clone https://github.com/SouvickMaity/Banking-system.git

cd Banking-system
```

---

## Start Infrastructure

```bash
docker-compose up -d
```

This starts the required infrastructure services configured in the project.

---

## Start Microservices

Each service can be started independently.

For example:

```bash
cd api-gateway
mvn spring-boot:run
```

Then:

```bash
cd account-service
mvn spring-boot:run
```

```bash
cd transection-service
mvn spring-boot:run
```

```bash
cd fraud-detection-service
mvn spring-boot:run
```

```bash
cd notification-service
mvn spring-boot:run
```

```bash
cd payment-service
mvn spring-boot:run
```

---

# 🔍 Example Transaction

### Request

```http
POST /api/v1/transactions/transfer
Content-Type: application/json
```

```json
{
  "senderAccountNumber": "A100",
  "receiverAccountNumber": "B200",
  "amount": 10000,
  "description": "Fund Transfer"
}
```

### Internal Flow

```text
Client
  │
  ▼
API Gateway
  │
  │ Rate Limit
  ▼
Transaction Service
  │
  │ OpenFeign
  ▼
Account Service
  │
  │ Deduct
  ▼
Transaction Service
  │
  │ Save PROCESSING
  ▼
Kafka
  │
  │ transaction.initiated
  ▼
Fraud Detection
  │
  ├──────────── CLEAN
  │                │
  │                ▼
  │          COMPLETED
  │                │
  │                ▼
  │       transaction.completed
  │                │
  │        ┌───────┴────────┐
  │        ▼                ▼
  │    Account         Notification
  │    Service           Service
  │        │
  │        ▼
  │   Credit Receiver
  │
  └──────────── FRAUD
                   │
                   ▼
             Compensation
                   │
                   ▼
              Refund Sender
```

---

# 🎯 Distributed System Concepts Demonstrated

This project demonstrates practical implementation of:

### Microservices Architecture

Independent services with separate responsibilities.

### API Gateway

Single entry point for clients.

### Event-Driven Architecture

Kafka events decouple services.

### Saga Pattern

Compensating transactions handle failures across services.

### Asynchronous Processing

Fraud detection and notifications happen through Kafka consumers.

### Synchronous Communication

OpenFeign is used when an immediate response from another service is required.

### Redis

Used for temporary OTP storage and expiration.

### Rate Limiting

Protects APIs from excessive traffic.

### Webhooks

Razorpay asynchronously informs the Payment Service about payment status.

---

# 📊 Transaction State

A transaction can move through states such as:

```text
             ┌───────────────┐
             │   PROCESSING  │
             └───────┬───────┘
                     │
          ┌──────────┴──────────┐
          │                     │
          ▼                     ▼
     ┌───────────┐         ┌──────────┐
     │ COMPLETED │         │  FLAGGED │
     └───────────┘         └────┬─────┘
                                │
                                ▼
                           COMPENSATED
```

---

# 🔐 Failure Handling

The system considers failures such as:

* Insufficient balance
* Invalid OTP
* Expired OTP
* Fraud detection
* Account blocking
* Payment failure
* Downstream service failures

For operations where money has already been deducted, the system uses **compensation/refund logic** instead of relying on a distributed database transaction.

---

# 📈 Why Kafka?

Kafka allows services to communicate without tightly coupling them.

Without Kafka:

```text
Transaction Service
       │
       ├──► Fraud Service
       │
       ├──► Notification Service
       │
       └──► Account Service
```

With Kafka:

```text
Transaction Service
       │
       ▼
      Kafka
       │
       ├──► Fraud Service
       ├──► Account Service
       └──► Notification Service
```

This makes it easier to add new consumers without significantly changing the Transaction Service.

---

# 🔐 Why Redis?

Redis is used for temporary OTP storage.

```text
transactionId
      │
      ▼
Redis
      │
      ├── OTP
      └── TTL
```

The TTL automatically removes expired OTPs, making Redis suitable for short-lived authentication data.

---

# 🚀 Future Improvements

Possible improvements include:

* JWT/OAuth2 authentication
* Service discovery with Eureka
* Centralized configuration with Spring Cloud Config
* Distributed tracing with OpenTelemetry
* Prometheus + Grafana monitoring
* Dead Letter Topics for failed Kafka messages
* Kafka retry mechanisms
* Idempotent event processing
* Database-per-service enforcement
* Circuit breakers using Resilience4j
* Outbox Pattern for reliable event publishing
* Kubernetes deployment
* CI/CD pipeline
* Comprehensive integration tests

---

# 👨‍💻 Author

**Souvick Maity**

Computer Science & Engineering Student

---

## ⭐ Project Highlights

```text
Spring Boot Microservices
        +
Spring Cloud Gateway
        +
Apache Kafka
        +
Redis
        +
Saga Compensation
        +
Fraud Detection
        +
Razorpay Webhooks
        +
Event-Driven Architecture
```

A practical demonstration of building a **distributed banking system using modern backend and microservices technologies**.
