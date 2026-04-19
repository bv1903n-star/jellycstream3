# JellyCStream Bridge

JellyCStream Bridge is a **Kotlin (Ktor)** microservice running inside **Docker**. This project aims to bring the powerful cloud-scraping capabilities of **Cloudstream 3** extensions to the **Jellyfin** ecosystem.

## 🚀 Project Objectives
This service acts as a *Bridge Runtime* that allows Jellyfin plugins to:
1. Run Cloudstream extension files (`.jar` / `.cs3`) natively in a JVM environment (Desktop/Server).
2. Perform standard content scraping (Search, Load Details, Extract Links) without requiring an Android environment.
3. Standardize output from various providers into a single, consistent JSON REST API.

## ✨ Current Features
- **Dynamic JAR Loading**: Dynamically loads and parses provider files from the `extensions/` directory.
- **SQLite Persistence**: Permanently stores extension metadata using Exposed ORM.
- **Robust Ticketing System**: Asynchronous plugin installation. If the loading process exceeds 5 seconds, the system issues a "Ticket ID" for client polling (preventing request hanging).
- **Hash Verification**: Ensures file integrity with SHA-256 verification after downloading.
- **Auto-Recovery**: Automatically reloads all registered plugins from the database upon service restart.

## 🛠️ Tech Stack
- **Language**: Kotlin
- **Framework**: Ktor (Netty Engine)
- **Database**: SQLite (via Exposed ORM)
- **Containerization**: Docker & Docker Compose
- **Build System**: Gradle (Shadow JAR)

## ⚠️ Important Notes
For privacy and security reasons, this repository **DOES NOT** store:
- Extension binary files (`.jar`, `.cs3`).
- Local databases (`plugins.db`).
- Temporary cache or metadata files.

These files are managed via `.gitignore` and kept locally within Docker volumes.

---
*This project is under intensive development for full integration with the Jellyfin Plugin.*
