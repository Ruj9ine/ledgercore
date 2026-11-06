# LedgerCore

Financial wallet and transaction management API built with Java 21, Spring Boot and PostgreSQL.

> Status: project skeleton only. Features are added milestone by milestone; see the Git history.

## Local setup (Windows PowerShell)

```powershell
Copy-Item .env.example .env        # then edit the passwords
docker compose --env-file .env up -d
```

Requires JDK 21, Maven 3.9+ and Docker.
