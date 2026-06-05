# Product Gallery

Product Gallery is a full-stack product album system inspired by social-commerce catalog workflows. It includes a public storefront, an admin panel, media upload support, import tooling, order vouchers, and optional AI-assisted translation.

## Stack

- Backend: Java 8, Spring Boot 2.7, MyBatis Plus, MySQL 8
- Frontend: Vue 3, Vite, Element Plus, Tailwind CSS
- Optional services: OSS-compatible object storage, OpenAI-compatible LLM API, exchange-rate API

## Repository Notes

- This public repository is a sanitized release copy.
- Real deployment hosts, credentials, secrets, and private operational runbooks have been removed.
- Configure all secrets through environment variables before running in any non-local environment.

## Project Structure

```text
product_gallery_public/
├── backend/
│   ├── src/main/java/com/szwego/gallery
│   └── src/main/resources/sql/schema.sql
├── docs/
├── frontend/
└── README.md
```

## Features

- Public storefront with category navigation, search, image/video display, and currency switching
- Admin product management with publish/edit/delete/up-down/top actions
- Shop profile configuration with menu, custom pages, banners, and contact items
- Order voucher workflow with public voucher pages and poster generation
- Import pipeline for external album/shop content
- Optional AI-assisted English title/description generation

## Backend Setup

1. Create a MySQL database and import the schema:

```sql
SOURCE backend/src/main/resources/sql/schema.sql;
```

2. Copy and fill environment variables from `backend/.env.example`.

3. Start the backend:

```bash
cd backend
mvn spring-boot:run
```

## Frontend Setup

```bash
cd frontend
npm install
npm run dev
```

Default development URLs:

- Storefront: `http://localhost:5173/`
- Admin: `http://localhost:5173/admin`

## Required Environment Variables

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `ADMIN_TOKEN_EXPIRE_HOURS`
- `SHARE_BASE_URL`

Optional integrations:

- `OSS_ENABLED`, `OSS_ENDPOINT`, `OSS_BUCKET_NAME`, `OSS_ACCESS_KEY_ID`, `OSS_ACCESS_KEY_SECRET`, `OSS_BUCKET_DOMAIN`
- `WS_ALBUM_ENDPOINT`, `WS_ALBUM_TOKEN_SECRET`, `WS_ALBUM_CRAWL_POOL_SIZE`
- `LLM_KEY_SECRET`, `LLM_REQUEST_TIMEOUT_MS`
- `CURRENCY_BASE`, `CURRENCY_SOURCE_NAME`, `CURRENCY_API_URL`, `CURRENCY_TIMEOUT_MS`, `CURRENCY_REFRESH_CRON`, `CURRENCY_REFRESH_ZONE`, `CURRENCY_SUPPORTED`

## Public Release Guidance

- Replace all placeholder passwords and secret strings before deployment.
- Keep runtime credentials out of source control.
- Treat `docs/系统完整使用手册.md` as the sanitized public operations guide, not a private production runbook.
