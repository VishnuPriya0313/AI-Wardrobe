# AI Wardrobe Studio

AI Wardrobe Studio is split into two services:

- `frontend/`: React browser UI served as static files.
- `backend/`: Spring Boot API service that keeps the OpenAI key server-side.

## Local Run

Start the backend:

```powershell
npm run start:backend
```

Start the frontend:

```powershell
npm run start:frontend
```

Open:

- Frontend: `http://127.0.0.1:5173/`
- Backend health: `http://127.0.0.1:8080/api/health`

## Configuration

Copy `.env.example` to `.env` and set:

```text
AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://127.0.0.1:11434
OLLAMA_MODEL=llava
OLLAMA_MATCH_MODEL=llama3.2:3b
OPENAI_API_KEY=your_key_here
OPENAI_MODEL=gpt-4.1-mini
PORT=8080
CORS_ALLOWED_ORIGINS=http://127.0.0.1:5173,http://localhost:5173
APP_S3_ENABLED=false
APP_S3_BUCKET=your-wardrobe-bucket
APP_S3_REGION=us-east-1
APP_S3_KEY_PREFIX=wardrobe-uploads
APP_S3_PUBLIC_BASE_URL=
APP_S3_ENDPOINT=
AWS_ACCESS_KEY_ID=your_aws_access_key
AWS_SECRET_ACCESS_KEY=your_aws_secret_key
```

Use `AI_PROVIDER=ollama` for local Ollama. `OLLAMA_MODEL` is the vision model for upload recognition, and `OLLAMA_MATCH_MODEL` is the faster text model for matching. Use `AI_PROVIDER=openai` to switch back to OpenAI.

## S3 / R2 Storage

Uploaded wardrobe items are saved in the browser first. When `APP_S3_ENABLED=true`, the backend also stores each user-uploaded item in S3:

- Image: `APP_S3_KEY_PREFIX/items/{item-id}/image.{ext}`
- Metadata JSON: `APP_S3_KEY_PREFIX/items/{item-id}/metadata.json`

For Cloudflare R2, set `APP_S3_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com` and use `APP_S3_REGION=auto`. For AWS S3, leave `APP_S3_ENDPOINT` blank and set `APP_S3_REGION` to the bucket region.

Keep `APP_S3_PUBLIC_BASE_URL` blank while the bucket is private. The frontend still displays the browser's saved image data; R2/S3 is currently the backup copy. Later, with user accounts, the app can load images from public R2 URLs or backend-generated presigned URLs.

Do not commit real AWS credentials. Use environment variables, `.env`, or your deployment secret manager. The backend uses the AWS default credentials chain, so `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, IAM roles, and local AWS profiles can all work depending on where you run it.

## Ollama Setup

Install Ollama, then pull a vision model:

```powershell
ollama pull llava
ollama pull llama3.2:3b
```

Keep Ollama running locally. The backend expects it at:

```text
http://127.0.0.1:11434
```

## Checks

```powershell
npm run check:frontend
npm run check:backend
```
