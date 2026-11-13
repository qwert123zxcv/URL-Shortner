# URL Shortener & Link Analytics

A small Spring Boot service that turns long URLs into short codes and
redirects visitors back to the original link.

See [WRITEUP.md](WRITEUP.md) for the design write-up (AI usage, trade-offs,
what's missing).

## Stack

- Java 21, Spring Boot 3.3 (Web, Data JPA, Validation)
- PostgreSQL (persistence)
- Maven
- JUnit 5, AssertJ, MockMvc, H2 (tests only)

## Prerequisites

- JDK 21+
- Maven 3.9+
- A running PostgreSQL server (local install or any reachable instance)

## Setup

1. Create a database:

   ```sql
   CREATE DATABASE url_shortener;
   ```

2. Configure the connection via environment variables (defaults shown are
   used if unset — override at least the password):

   | Variable | Default | Purpose |
   |---|---|---|
   | `DB_NAME` | `url_shortener` | Database name |
   | `DB_USERNAME` | `postgres` | DB user |
   | `DB_PASSWORD` | `postgres` | DB password |
   | `APP_BASE_URL` | `http://localhost:8080` | Used to build the `shortUrl` in responses |

   No secrets are hard-coded in `application.yml` — it's not safe to commit
   real credentials to a repo that's meant to be shared.

## Run

```bash
DB_PASSWORD=your_password mvn spring-boot:run
```

On Windows PowerShell:

```powershell
$env:DB_PASSWORD="your_password"; mvn spring-boot:run
```

The service starts on `http://localhost:8080`. The `url_mapping` table and
`url_mapping_seq` sequence are created automatically on first run
(`schema.sql` + `ddl-auto: update`) — no manual migration step needed.

## Test

```bash
mvn test
```

Tests run against an in-memory H2 database (`src/test/resources/application.yml`)
and need no local Postgres or other setup.

## API

### `POST /shorten`

Request:

```json
{ "url": "https://example.com/some/very/long/path", "alias": "my-alias" }
```

`alias` is optional. If omitted, a short code is generated automatically.

Response — `201 Created` for a newly created mapping, `200 OK` when the
same URL was already shortened before (see [WRITEUP.md](WRITEUP.md) for
why):

```json
{
  "shortCode": "LQeX4G",
  "shortUrl": "http://localhost:8080/LQeX4G",
  "longUrl": "https://example.com/some/very/long/path",
  "createdAt": "2026-07-21T19:13:52.440378800Z"
}
```

Errors:

| Status | When |
|---|---|
| `400` | URL is blank, not absolute, not http/https, or over 2048 chars; alias fails format rules or is a reserved word |
| `409` | Custom alias is already taken |

### `GET /{code}`

Redirects with `301 Moved Permanently` to the original URL, or `404` if
the code doesn't exist.

```bash
curl -i http://localhost:8080/LQeX4G
curl -i http://localhost:8080/unknown-code   # 404
```

## Example session

```bash
curl -s -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.paytm.com"}'

curl -i http://localhost:8080/<shortCode>
```

## Project layout

```
src/main/java/com/urlshortener/
  controller/    REST endpoints
  service/       business logic (validation, dedup, short code generation)
  entity/        JPA entity
  repository/    Spring Data JPA repository
  dto/           request/response bodies
  exception/     domain exceptions + global error handler
src/main/resources/
  application.yml
  schema.sql     creates the id sequence
src/test/        unit + MockMvc integration tests
```
