# AI Wardrobe Studio

AI Wardrobe Studio is split into two services:

- `frontend/`: React browser UI served as static files.
- `backend/`: Spring Boot API service that keeps the OpenAI key server-side.

## Local Run

Start the backend:

```shell
npm run start:backend
```

The backend uses PostgreSQL. With Docker Desktop running, this command automatically
starts the project's database container the first time it is needed. You can also
manage it explicitly:

```shell
npm run start:database
npm run stop:database
```

Start the frontend:

```shell
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
DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/ai_wardrobe
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your_database_password
SESSION_COOKIE_SECURE=false
SESSION_COOKIE_SAME_SITE=lax
FRONTEND_BASE_URL=http://127.0.0.1:5173
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_gmail_app_password
MAIL_FROM=your_email@gmail.com
GOOGLE_CLIENT_ID=your_google_web_client_id
GOOGLE_CLIENT_SECRET=your_google_web_client_secret
APP_S3_ENABLED=false
APP_S3_BUCKET=your-wardrobe-bucket
APP_S3_REGION=us-east-1
APP_S3_KEY_PREFIX=wardrobe-uploads
APP_S3_PUBLIC_BASE_URL=
APP_S3_ENDPOINT=
AWS_ACCESS_KEY_ID=your_aws_access_key
AWS_SECRET_ACCESS_KEY=your_aws_secret_key
```

Create the PostgreSQL database before starting the backend. Flyway creates the user and wardrobe tables automatically. For HTTPS production deployments, set `SESSION_COOKIE_SECURE=true`. Prefer serving the frontend and API from the same site; if they must be cross-site, use `SESSION_COOKIE_SAME_SITE=none` and HTTPS.

New password accounts must verify their email before login. Configure SMTP using the
`MAIL_*` settings above. Google sign-in is enabled when both Google OAuth values are
present. Configure the authorized redirect URI as
`http://127.0.0.1:8080/login/oauth2/code/google` for local development; use the deployed
backend origin in production.

Use `AI_PROVIDER=ollama` for local Ollama. `OLLAMA_MODEL` is the vision model for upload recognition, and `OLLAMA_MATCH_MODEL` is the faster text model for matching. Use `AI_PROVIDER=openai` to switch back to OpenAI.

## S3 / R2 Storage

Uploaded wardrobe items remain in memory for the current browser session. When `APP_S3_ENABLED=true`, the backend stores each user-uploaded item in S3 so it can be restored in future sessions:

- Image: `APP_S3_KEY_PREFIX/users/{user-id}/items/{item-id}/image.{ext}`
- Metadata JSON: `APP_S3_KEY_PREFIX/users/{user-id}/items/{item-id}/metadata.json`

For Cloudflare R2, set `APP_S3_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com` and use `APP_S3_REGION=auto`. For AWS S3, leave `APP_S3_ENDPOINT` blank and set `APP_S3_REGION` to the bucket region.

Keep `APP_S3_PUBLIC_BASE_URL` blank while the bucket is private. The backend reads private objects after login and sends them to the signed-in user's wardrobe; no wardrobe items are persisted in browser storage.

Do not commit real AWS credentials. Use environment variables, `.env`, or your deployment secret manager. R2 uses `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`; AWS IAM roles and local AWS profiles remain available through the default credentials chain when those values are blank.

When the frontend is deployed separately, add all `APP_S3_*`, `AWS_ACCESS_KEY_ID`, and `AWS_SECRET_ACCESS_KEY` values to the backend service (for example, Render), not to the static frontend host. The R2 token needs Object Read & Write access to `APP_S3_BUCKET`.

## Ollama Setup

Install Ollama, then pull a vision model:

```shell
ollama pull llava
ollama pull llama3.2:3b
```

Keep Ollama running locally. The backend expects it at:

```text
http://127.0.0.1:11434
```

## Checks

```shell
npm run check:frontend
npm run check:backend
npm run check:r2
```

`check:r2` writes one tiny diagnostic object, reads it, and removes it immediately.
