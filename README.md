# InsiderTrans

Ledger for insider transactions. The server stores issuers, reporting owners, and filings in PostgreSQL database `insidertrans`. The client is the ledger UI.

## Layout

- `server` — Java 21 and Spring Boot API
- `client` — React and TypeScript, built with Vite

## Database

PostgreSQL must be running. Create the database once:

```sql
CREATE DATABASE insidertrans;
```

Or, from `server`:

```powershell
psql -U postgres -h localhost -f scripts/create-database.sql
```

The API creates the tables on startup. Edit `server/src/main/resources/db.properties` to change the JDBC URL, user, or password, then restart the server.

## Run

In one terminal:

```powershell
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "your-password"
mvn -f server/pom.xml spring-boot:run
```

In another:

```powershell
cd client
npm install
npm run dev
```

Open http://localhost:5173. The dev server proxies `/api` to http://localhost:6060.

## Form 4 backfill

Walk SEC daily indexes day by day for companies already in `companies`:

```powershell
mvn -f server/pom.xml -DskipTests exec:java `
  -Dexec.mainClass=com.insidertrans.service.processor.Form4BackfillJob `
  "-Dexec.args=--startDate=2026-10-01 --endDate=2026-10-15"
```

`--startDate` and `--endDate` are `yyyy-MM-dd`. Days with no index (weekends, holidays) are skipped. Each matching Form 4 package is downloaded, the reporting owner is inserted into `insiders` when that CIK is new, and transaction rows go into `insider_transactions`. Accessions already stored are skipped.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/transactions` | List filings. Filters: `ticker`, `insider`, `code`, `from`, `to`. Paging: `page` (1-based, default 1), `size` (default 10) |
| POST | `/api/transactions` | Record a filing. Creates the issuer and insider when they are new |
| GET | `/api/transactions/{id}` | Fetch one filing |
| DELETE | `/api/transactions/{id}` | Remove a filing |
| GET | `/api/companies` | Issuers already on file |
| GET | `/api/insiders` | Reporting owners already on file |
| GET | `/api/admin/fetcher` | Auto-fetch status (interval, start/stop, last run) |
| PUT | `/api/admin/fetcher` | Set `enabled` and `intervalMinutes` (15, 30, 60, 180, 360) |
| POST | `/api/admin/backfill` | Start a date-range backfill (`startDate`, `endDate`); skips stored accessions |
| POST | `/api/impact` | Job 1 insider-flow snapshot for a ticker as of a date, using ~120 OHLCV sessions |

`POST /api/impact` body: `{ "ticker", "asOfDate", "bars": [{ "date", "open", "high", "low", "close", "volume" }] }`. The caller does not need to know whether a Form 4 was filed that day. The API loads filings already stored for that ticker whose filing date (else trade date) is on or before `asOfDate` and within 90 days. Open-market **P/S** with a price are scored (`type × cash-cap amount × 30-day linear decay`). Grants and other codes are listed with impact 0. Bars after `asOfDate` are ignored. `return5d` / `return10d` / `return20d` are session returns after the filing and are omitted until that many sessions exist before `asOfDate`. `flowScore` is mean(impact) × ln(1+N). Labels: Neutral \|z\|≤0.5, Positive/Negative to 2, Very Positive/Negative beyond. This is tape context, not a forecast.

Open `#/admin` in the UI to change the poll interval, start or stop automatic fetching, and backfill missing days. Automatic fetching is off until you start it. When it runs, it covers today and yesterday so a late-published daily index is still picked up.

Transaction codes follow the common Form 4 set: `P` purchase, `S` sale, `A` grant, `M` option exercise, `F` tax withholding, `G` gift, `D` sale to the issuer. A gift must say whether shares were acquired (`A`) or disposed (`D`).
