# ConnectHub — room-service

> **Port:** `8082` | **Base path:** `/api/rooms` | **Package:** `com.connecthub.room`

Room & Channel Management Microservice for ConnectHub.
Handles GROUP rooms, Direct Messages (DM), membership roles,
mute control, invite codes, and unread count tracking.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2, Spring Security 6 |
| Auth | JWT validation (shared secret with auth-service) |
| Database | MySQL 8 + Spring Data JPA |
| Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Build | Maven 3.9, Java 17 |
| Container | Docker + Docker Compose |

---

## Quick Start

### Option A — Docker Compose

```bash
cd room-service
docker-compose up --build
```

Service at `http://localhost:8082`
Swagger UI at `http://localhost:8082/api/swagger-ui.html`

### Option B — Local

```bash
# MySQL must be running on port 3306 with database connecthub_room
mvn spring-boot:run
```

---

## Project Structure

```
room-service/
├── src/main/java/com/connecthub/room/
│   ├── RoomServiceApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java           ← JWT filter, CORS, stateless sessions
│   │   └── OpenApiConfig.java
│   ├── controller/
│   │   └── RoomController.java           ← all 18 REST endpoints
│   ├── dto/
│   │   ├── request/                      ← CreateRoomRequest, UpdateRoomRequest, etc.
│   │   └── response/                     ← RoomResponse, RoomMemberResponse, ApiResponse
│   ├── entity/
│   │   ├── Room.java                     ← GROUP / DM, inviteCode, lastMessageAt
│   │   └── RoomMember.java               ← role, isMuted, lastReadAt
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   └── *.java
│   ├── repository/
│   │   ├── RoomRepository.java           ← findRoomsByUserId, findDmBetween, etc.
│   │   └── RoomMemberRepository.java     ← updateLastReadAt, countAdmins
│   ├── security/
│   │   ├── JwtService.java               ← validates tokens from auth-service
│   │   ├── JwtAuthenticationFilter.java
│   │   └── AuthenticatedUser.java        ← security principal (userId, email, role)
│   └── service/
│       ├── RoomService.java              ← business contract
│       └── impl/RoomServiceImpl.java     ← full implementation
└── src/test/
    └── RoomServiceImplTest.java          ← 12 unit tests
```

---

## API Reference

### Room CRUD

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/rooms` | Create GROUP room or start DM |
| GET | `/api/rooms/{roomId}` | Get room with member list |
| GET | `/api/rooms/my` | Get all rooms for current user |
| PUT | `/api/rooms/{roomId}` | Update room — Admin only |
| DELETE | `/api/rooms/{roomId}` | Delete room — creator only |

### Join / Leave / Invite

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/rooms/join/{inviteCode}` | Join room via invite link |
| POST | `/api/rooms/{roomId}/leave` | Leave a room |
| POST | `/api/rooms/{roomId}/invite/regenerate` | New invite code — Admin only |

### Member Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/rooms/{roomId}/members` | List all members |
| POST | `/api/rooms/{roomId}/members` | Add member — Admin only |
| DELETE | `/api/rooms/{roomId}/members/{userId}` | Remove member — Admin only |
| PUT | `/api/rooms/{roomId}/members/{userId}/role` | Change role — Admin only |
| PUT | `/api/rooms/{roomId}/members/{userId}/mute` | Mute member — Admin only |
| PUT | `/api/rooms/{roomId}/members/{userId}/unmute` | Unmute member — Admin only |

### Unread Tracking

| Method | Endpoint | Description |
|--------|----------|-------------|
| PUT | `/api/rooms/{roomId}/read` | Update last-read timestamp |
| GET | `/api/rooms/{roomId}/unread` | Get unread count |

### DM

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/rooms/dm/{targetUserId}` | Get existing DM or create new |

### Internal (called by other services)

| Method | Endpoint | Description |
|--------|----------|-------------|
| PUT | `/api/rooms/{roomId}/last-message` | Update lastMessageAt — called by message-service |

### Admin

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/rooms/admin/all` | List all rooms |
| DELETE | `/api/rooms/admin/{roomId}` | Force delete any room |

---

## cURL Examples

### Create GROUP Room
```bash
curl -X POST http://localhost:8082/api/rooms \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Engineering","description":"Dev team chat","type":"GROUP"}'
```

### Start a DM
```bash
curl -X POST http://localhost:8082/api/rooms/dm/42 \
  -H "Authorization: Bearer <TOKEN>"
```

### Join via Invite Code
```bash
curl -X POST http://localhost:8082/api/rooms/join/ABC123XYZ000 \
  -H "Authorization: Bearer <TOKEN>"
```

### Add Member
```bash
curl -X POST http://localhost:8082/api/rooms/1/members \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"userId": 55, "role": "MEMBER"}'
```

### Mark Messages as Read
```bash
curl -X PUT http://localhost:8082/api/rooms/1/read \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"readAt": "2026-04-18T10:30:00"}'
```

---

## Inter-Service Communication

The `message-service` calls `PUT /api/rooms/{roomId}/last-message?sentAt=...`
after every new message is saved, keeping `Room.lastMessageAt` current for
sorting the room list.

The `websocket-handler` reads membership and mute status via
`GET /api/rooms/{roomId}/members` before broadcasting a STOMP frame,
to skip delivery to muted users.

---

## Running Tests

```bash
mvn test
```
