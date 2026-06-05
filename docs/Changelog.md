# Changelog

> This file records key changes at the "functional and behavioral level" for easier tracking by operations, testing, and consumer sides.

## 2026-05-13

* Module: Order Voucher
* Type: Add
* Change:
* Added a new "Order Voucher" page to the backend, supporting manual invoice creation.
* Supported two sources for product details: selecting from system products, or manually entering the product name, image, and order quantity.
* Supported customer details, shipping fees, discounts, amount paid, validity period, and internal notes.
* Added currency conversion for unpaid invoices, calculating the entire invoice based on the current project exchange rate and synchronously updating item details and summary amounts.
* Added a public voucher page `/voucher/{publicCode}` and a voucher poster API `/api/order-vouchers/poster/{publicCode}`.
* Added a "Create Voucher" quick entry point to the operations column in Product Management.


* Scope of Impact: `/api/admin/order-vouchers*`, `/api/order-vouchers/public/{publicCode}`, backend management pages, public voucher pages
* Migration Required: No (The backend automatically creates tables `order_voucher` and `order_voucher_item`, and `schema.sql` has been synchronized).

## 2026-04-30

* Module: WeChat Business Album Crawling
* Type: Add
* Change:
* Added "Specified Image/Text ID Crawling" capability to the backend crawling tasks (supporting multiple `itemId`s).
* Added a "Merge into 1 staging product" option for multiple `itemId`s.
* Added a crawling branch that uses `https://www.szwego.com/commodity/view?targetAlbumId=...&itemId=...` to request by ID, carrying the original token session.
* When `itemId` is left blank, it still falls back to the original paginated crawling pipeline, without affecting existing logic.


* Scope of Impact: Backend crawling task form, `/api/admin/ws-album/crawl/start`, staging database ingestion pipeline
* Migration Required: No

## 2026-04-13

* Module: Product Tags (Frontend English) / Import Code Extraction
* Type: Fix
* Change:
* Added tag translations (including brands and common category words) when returning tags on English pages.
* Supplemented product code tags (e.g., `2VH192`) extracted from titles/descriptions when returning product lists and details.
* Corrected regex boundaries for code extraction to support scenarios where "Chinese characters immediately follow the code" (e.g., `2VH192新品`).


* Scope of Impact: `/api/products`, `/api/products/{id}`, tag generation for importing WeChat Business data into the production database
* Migration Required: No

## 2026-04-07

* Module: Documentation System
* Type: Add
* Change: Added `docs/System_Complete_User_Manual.md` and `docs/Documentation_Maintenance_Specifications.md`, establishing a documentation update closed loop.
* Scope of Impact: Whole-system usage, operations, and release processes
* Migration Required: No

## 2026-04-07

* Module: Store Configuration / Frontend Contact
* Type: Modify
* Change:
* Supported configuring multiple contact methods in the backend (Email, WhatsApp, WeChat, Phone, Custom).
* Changed the frontend Contact section to display in a modal popup and supported one-click copying.
* Added the `contacts` structure to the backend while maintaining compatibility with legacy fields `contactName/contactWechat/contactPhone`.


* Scope of Impact: `/api/shop/profile`, `/api/admin/shop/profile`, backend store configuration page, frontend Contact section
* Migration Required: No (The backend automatically adds the column `contact_config_json`, legacy data is compatible).

## 2026-04-06 (Retroactive)

* Module: Exchange Rate & Currency
* Type: Add/Modify
* Change:
* Added currency switching (USD/EUR/GBP/JPY/CNY) to the frontend.
* Fixed the default language to English and the default currency to USD (manually switchable).
* Added a backend exchange rate snapshot API and a daily scheduled task for database updates (`currency_rate`).


* Scope of Impact: Frontend list and details price display, `/api/currency/rates`
* Migration Required: No (The backend automatically creates the table `currency_rate`).

## 2026-04-06 (Retroactive)

* Module: Media URL & Display
* Type: Fix
* Change:
* Removed OSS signature parameters to prevent image expiration caused by link timeouts.
* Enabled OSS thumbnail parameters for list images; removed thumbnail parameters for large image previews and details images.


* Scope of Impact: Frontend list/details/preview, image URLs returned by product APIs
* Migration Required: No

## 2026-04-05 (Retroactive)

* Module: WeChat Business Import
* Type: Modify
* Change:
* Supported one-click import limited by specific stores.
* Enhanced the import process (state transitions, failure recording, exception flagging, and ignoring low-quality data).


* Scope of Impact: `ws_album_*` import pipeline, backend WeChat Business import page
* Migration Required: No
