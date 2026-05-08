# ConnectHub — Payment Service

Standalone microservice that handles Razorpay integration, subscription management, and plan enforcement for the ConnectHub platform.

---

## Port & Database

| Property | Value |
|----------|-------|
| Port | `8089` |
| Database | `connecthub_payment` (MySQL) |
| Eureka name | `PAYMENT-SERVICE` |

---

## Plans

| Feature | FREE (₹0/month) | PRO (₹199/month) |
|---------|:-:|:-:|
| Max Rooms | 5 | Unlimited |
| Max Members / Room | 50 | Unlimited |
| Max File Size | 5 MB | 100 MB |
| Message History | 30 days | Unlimited |
| Max Devices | 1 | Unlimited |
| Custom Room Avatar | ✗ | ✓ |
| Priority Notifications | ✗ | ✓ |
| Read Receipts | ✗ | ✓ |
| Message Reactions | ✗ | ✓ |

Plans are **auto-seeded** on first boot by `DataInitializer`. No manual SQL needed.

---

## API Endpoints

### Public (no auth required)
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/payments/health` | Health check |
| `GET` | `/api/payments/plans` | List FREE and PRO plans |
| `POST` | `/api/payments/webhook/razorpay` | Razorpay webhook (HMAC verified) |
| `GET` | `/api/payments/subscription/limits/{userId}` | Internal — used by other services |

### Authenticated (JWT required)
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/payments/create-order` | Create a Razorpay order for PRO |
| `POST` | `/api/payments/verify` | Verify payment + activate PRO |
| `GET` | `/api/payments/subscription/my` | Get own subscription details |
| `GET` | `/api/payments/subscription/limits` | Get own plan limits |
| `PUT` | `/api/payments/subscription/cancel` | Cancel PRO subscription |
| `PUT` | `/api/payments/subscription/auto-renew?autoRenew=true` | Toggle auto-renewal |
| `GET` | `/api/payments/history` | Payment transaction history |

### Admin only (`PLATFORM_ADMIN` role)
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/payments/admin/analytics` | Revenue & subscription analytics |
| `GET` | `/api/payments/admin/subscriptions` | All subscriptions |

---

## Payment Flow

```
1. Client → POST /api/payments/create-order  { planName: "PRO" }
   ← { razorpayOrderId, razorpayKeyId, amount: 199.00, currency: "INR" }

2. Client opens Razorpay checkout modal with the above details

3. User completes payment on Razorpay

4. Client → POST /api/payments/verify
   { razorpayOrderId, razorpayPaymentId, razorpaySignature, planName: "PRO" }
   ← { success: true, planName: PRO, startDate, endDate, subscriptionId }

5. Subscription activated — user gets PRO access for 30 days
```

---

## Webhook Events Handled

| Event | Action |
|-------|--------|
| `payment.captured` | Mark transaction SUCCESS |
| `payment.failed` | Mark transaction FAILED + send notification |
| `refund.created` | Mark transaction REFUNDED + set refundId |

Webhook signature is verified via **HMAC-SHA256** using `razorpay.webhook-secret`. All events are logged to `webhook_logs` table with idempotency check.

---

## Scheduled Jobs

| Cron | Method | Action |
|------|--------|--------|
| `0 0 0 * * *` (midnight) | `processExpiredSubscriptions()` | Marks ACTIVE subscriptions past their `endDate` as EXPIRED |
| `0 0 10 * * *` (10am) | `sendExpiryReminders()` | Sends notifications for subscriptions expiring within 3 days |

---

## Internal Service Integration

Other services call `GET /api/payments/subscription/limits/{userId}` to enforce plan limits:

```java
// Example: room-service checking if user can create another room
PlanLimitsDTO limits = restTemplate.getForObject(
    "http://payment-service/api/payments/subscription/limits/" + userId,
    PlanLimitsDTO.class
);
if (limits.getMaxRooms() != -1 && currentRoomCount >= limits.getMaxRooms()) {
    throw new PlanLimitExceededException("Upgrade to PRO for unlimited rooms");
}
```

`-1` always means **unlimited**.

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `DB_PASSWORD` | MySQL root password (default: `root`) |
| `JWT_SECRET` | Must match `auth-service` JWT secret |
| `RAZORPAY_KEY_ID` | Razorpay API key ID |
| `RAZORPAY_KEY_SECRET` | Razorpay API key secret |
| `RAZORPAY_WEBHOOK_SECRET` | Razorpay webhook signing secret |

---

## Running Locally

```bash
# 1. Create database (auto-created by JPA, but you can pre-create)
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS connecthub_payment;"

# 2. Set environment variables
export DB_PASSWORD=root
export JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
export RAZORPAY_KEY_ID=rzp_test_xxxx
export RAZORPAY_KEY_SECRET=your_secret
export RAZORPAY_WEBHOOK_SECRET=your_webhook_secret

# 3. Start order: eureka-server → api-gateway → auth-service → payment-service
mvn spring-boot:run

# 4. Run tests
mvn test

# 5. Verify on Eureka
open http://localhost:8761
# PAYMENT-SERVICE should appear

# 6. Test health
curl http://localhost:8089/api/payments/health

# 7. Test plans
curl http://localhost:8089/api/payments/plans
```

---

## Start Order

```
1. eureka-server     (8761)
2. api-gateway       (8080)
3. auth-service      (8081)
4. payment-service   (8089)
```

---

## Key Design Decisions

- **No shared database** — `connecthub_payment` is exclusively owned by this service
- **FREE plan = no payment** — users always have FREE access; PRO requires Razorpay payment
- **Cancellation is soft** — cancelled PRO subscriptions retain access until `endDate`
- **Webhook idempotency** — duplicate events skipped via `razorpayEventId` check
- **Internal endpoint unprotected** — `/subscription/limits/{userId}` has no JWT so other services can call it without a user token
- **All secrets via env vars** — no credentials committed to source control
