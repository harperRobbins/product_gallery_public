# Product Gallery

Product Gallery is an AI-enhanced product album and sales-conversion system inspired by social-commerce catalog workflows. It combines a public storefront, an admin panel, import tooling, payment-oriented order workflows, and configurable LLM capabilities for multilingual content operations and buyer follow-up.

## Stack

- Backend: Java 8+, Spring Boot 2.7+, MyBatis Plus, MySQL 8+
- Frontend: Vue 3, Vite, Element Plus, Tailwind CSS
- Optional services: OSS-compatible object storage, OpenAI-compatible LLM API, exchange-rate API, local knowledge base / RAG pipeline

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
- Backend-configurable LLM integration for AI-assisted content generation and multilingual workflows

## AI Applications

### 1. LLM-powered multilingual content operations

- The backend can be configured with an LLM provider, model, base URL, and secret for AI workflows.
- After product data is organized into a clean English base description, AI can:
  - generate multilingual product summaries
  - extract structured product tags from the normalized product information
  - produce marketing copy variants based on the already-prepared English base content
- This design keeps the original source data, the cleaned English baseline, and the AI-generated variants as separate layers.

### 2. AI Virtual Account Manager

- After overseas buyers open the storefront or product pages, an AI Account Manager can provide 24-hour product Q&A.
- Using RAG (retrieval-augmented generation), the system can combine:
  - product database content
  - normalized product descriptions and tags
  - historical FAQ knowledge
- When a buyer wants to pay, or after payment is completed, the AI flow can:
  - identify the product link or SKU from the conversation
  - generate the corresponding payment or collection link
  - recognize the related order after payment
  - check whether the matching payment has been received
  - automatically update order status and related order information

## AI Architecture Direction

- Product data layer: SKU, product content, media, tags, translations
- Knowledge layer: FAQ, payment guidance, logistics notes, product-specific Q&A
- Retrieval layer: local indexed knowledge base for product and FAQ grounding
- Action layer: payment-link generation, order recognition, payment reconciliation, status update
- LLM layer: multilingual summarization, tag extraction, buyer communication, marketing-copy variants

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
