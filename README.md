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

The API creates the tables on startup. It connects to `jdbc:postgresql://localhost:5432/insidertrans` as user `postgres` with password `postgres`. Override the login with `DB_USERNAME` and `DB_PASSWORD`. Override the host and port with `DB_HOST` and `DB_PORT`. The database name stays `insidertrans`.

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

Open http://localhost:5173. The dev server proxies `/api` to http://localhost:8080.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/transactions` | List filings. Filters: `ticker`, `insider`, `code`, `from`, `to` |
| POST | `/api/transactions` | Record a filing. Creates the issuer and insider when they are new |
| GET | `/api/transactions/{id}` | Fetch one filing |
| DELETE | `/api/transactions/{id}` | Remove a filing |
| GET | `/api/companies` | Issuers already on file |
| GET | `/api/insiders` | Reporting owners already on file |

Transaction codes follow the common Form 4 set: `P` purchase, `S` sale, `A` grant, `M` option exercise, `F` tax withholding, `G` gift, `D` sale to the issuer. A gift must say whether shares were acquired (`A`) or disposed (`D`).
