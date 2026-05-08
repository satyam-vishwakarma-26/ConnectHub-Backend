# ConnectHub — Media Service

Microservice responsible for all file and image uploads in the ConnectHub platform.

## Responsibilities

| Domain | Details |
|--------|---------|
| **Image upload** | Accepts JPEG/PNG/GIF/WebP up to 25MB; stores original to AWS S3 |
| **Thumbnail generation** | Uses **Thumbnailator** to resize images server-side (320×240 default); stored under `thumbnails/` prefix in S3 |
| **File upload** | Accepts PDF/DOCX/ZIP documents; stored under `files/` prefix in S3 |
| **Pre-signed URLs** | Generates time-limited (24h) S3 pre-signed URLs for private-file downloads |
| **Room gallery** | Returns all images/files shared in a room sorted by upload time |
| **Message linking** | Associates a MediaFile record with its chat message after send |
| **Delete** | Removes both the S3 object (and thumbnail) and the DB record |
| **Admin** | Platform Admin can view all files, force-delete any file, and view total storage used |

## Port

`8084`  (context path `/api`)

## Database

`connecthub_media` — MySQL 8.0  
Schema auto-created by Hibernate (`ddl-auto: update`).

## AWS S3 Layout

```
connecthub-media/
  images/{uploaderId}/{uuid}-{filename}        ← original image
  thumbnails/{uploaderId}/thumb_{filename}.jpg ← auto-generated thumbnail
  files/{uploaderId}/{uuid}-{filename}         ← documents
```

## Key Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/media/upload/image?roomId={id}` | Upload image (multipart) — generates thumbnail |
| `POST` | `/api/media/upload/file?roomId={id}` | Upload document (multipart) |
| `GET` | `/api/media/{mediaId}` | Get media metadata by ID |
| `GET` | `/api/media/room/{roomId}` | Room media gallery (all files) |
| `GET` | `/api/media/room/{roomId}/images` | Room image gallery only |
| `GET` | `/api/media/uploader/{uploaderId}` | Files uploaded by a user |
| `GET` | `/api/media/{mediaId}/download` | Generate 24h pre-signed S3 download URL |
| `PUT` | `/api/media/{mediaId}/link` | Link media to its chat message |
| `DELETE` | `/api/media/{mediaId}` | Delete own file (S3 + DB) |
| `GET` | `/api/media/room/{roomId}/count` | File count for a room |
| `GET` | `/api/media/admin/all` | Admin — all files |
| `DELETE` | `/api/media/admin/{mediaId}` | Admin — force delete any file |
| `GET` | `/api/media/admin/storage` | Admin — total storage used |

Swagger UI: [http://localhost:8084/api/swagger-ui.html](http://localhost:8084/api/swagger-ui.html)

## Upload Flow

```
Client
  → POST /api/media/upload/image?roomId=10   (multipart, image/jpeg)
  ← { id, url, thumbnailUrl, sizeKb, width, height }

Client
  → STOMP /app/chat.send
    { roomId: 10, type: "IMAGE", mediaUrl: "<url>", content: "caption" }

websocket-handler
  → POST /api/messages  (persists message, returns messageId)
  → PUT  /api/media/{mediaId}/link  { messageId }
  → broadcast to /topic/room/10
```

## AWS Credentials

Credentials are resolved by the AWS SDK `DefaultCredentialsProvider` in this order:

1. `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` environment variables  
2. `~/.aws/credentials` file  
3. IAM role attached to the EC2/ECS instance (**recommended for production**)

**Never commit AWS credentials to source control.**

## Running Locally

```bash
# Start MySQL
docker-compose up mysql-media -d

# Export AWS credentials
export AWS_ACCESS_KEY_ID=your_key
export AWS_SECRET_ACCESS_KEY=your_secret

# Run the service
mvn spring-boot:run

# Run tests (S3 calls are mocked)
mvn test
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/connecthub_media` | MySQL URL |
| `SPRING_DATASOURCE_USERNAME` | `root` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `root` | DB password |
| `APP_JWT_SECRET` | (hex string) | Must match `auth-service` secret |
| `APP_AWS_REGION` | `us-east-1` | AWS region |
| `APP_AWS_S3_BUCKET` | `connecthub-media` | S3 bucket name |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,...` | CORS origins |
| `AWS_ACCESS_KEY_ID` | — | AWS access key (env var) |
| `AWS_SECRET_ACCESS_KEY` | — | AWS secret key (env var) |
