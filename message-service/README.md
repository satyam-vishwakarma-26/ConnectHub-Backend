# ConnectHub — Message Service

Microservice responsible for all chat message operations in the ConnectHub platform.

## Responsibilities

| Domain | Details |
|--------|---------|
| **Persistence** | Saves messages on every `CHAT_MESSAGE` STOMP frame received by `websocket-handler` |
| **Pagination** | Newest-first page queries + before-cursor queries for infinite scroll |
| **Edit / Delete** | Sender-only edit (TEXT only); soft-delete sets `isDeleted=true` and blanks content |
| **Delivery Status** | `SENT → DELIVERED → READ` transitions driven by websocket-handler events |
| **Search** | LIKE-based full-text keyword search within a room |
| **Pinning** | Room Admin can pin/unpin messages; pinned list returned per room |
| **Unread Count** | Counts messages sent after a caller-supplied `lastReadAt` timestamp |
| **Admin** | Platform Admin force-delete any message; clear entire room history |

## Port

`8083`  (context path `/api`)

## Database

`connecthub_message` — MySQL 8.0  
Schema auto-created by Hibernate (`ddl-auto: update`).

## Key Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/messages` | Send a message |
| `GET` | `/api/messages/{id}` | Get message by ID |
| `GET` | `/api/messages/room/{roomId}?page=0&size=30` | Paginated messages (newest first) |
| `GET` | `/api/messages/room/{roomId}/before?before=<ISO>&page=0&size=30` | Infinite scroll cursor |
| `PUT` | `/api/messages/{id}` | Edit a message (sender only, TEXT type only) |
| `DELETE` | `/api/messages/{id}` | Soft-delete a message (sender only) |
| `PUT` | `/api/messages/{id}/status` | Update delivery status (`DELIVERED`/`READ`) |
| `GET` | `/api/messages/room/{roomId}/search?keyword=hello` | Full-text search |
| `GET` | `/api/messages/room/{roomId}/unread?after=<ISO>` | Unread count |
| `PUT` | `/api/messages/{id}/pin` | Pin a message |
| `PUT` | `/api/messages/{id}/unpin` | Unpin a message |
| `GET` | `/api/messages/room/{roomId}/pinned` | Get pinned messages |
| `DELETE` | `/api/messages/admin/{id}` | Admin force-delete |
| `DELETE` | `/api/messages/admin/room/{roomId}/history` | Clear room history |

Swagger UI: [http://localhost:8083/api/swagger-ui.html](http://localhost:8083/api/swagger-ui.html)

## STOMP Integration

`websocket-handler` calls `POST /api/messages` after receiving a `CHAT_MESSAGE` frame:

```
Client → STOMP /app/chat.send
  → websocket-handler receives frame
  → POST http://message-service:8083/api/messages  (persists message)
  → SimpMessagingTemplate.convertAndSend("/topic/room/{roomId}", savedMessage)
```

Delivery status is advanced by `websocket-handler` via `PUT /api/messages/{id}/status`.

## Room-Service Integration

After every `sendMessage()` call, this service fires a `PUT` to:
```
http://room-service:8082/api/rooms/{roomId}/last-message?sentAt=<ISO>
```
This keeps `Room.lastMessageAt` up to date for the room list sort order.

## Running Locally

```bash
# Start MySQL
docker-compose up mysql-message -d

# Run the service
mvn spring-boot:run

# Run tests
mvn test
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/connecthub_message` | MySQL URL |
| `SPRING_DATASOURCE_USERNAME` | `root` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `root` | DB password |
| `APP_JWT_SECRET` | (hex string) | Must match `auth-service` secret |
| `APP_ROOM_SERVICE_URL` | `http://localhost:8082/api` | Room-service base URL |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,...` | CORS origins |
