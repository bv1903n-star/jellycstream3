# Cloudstream Bridge API Documentation

This document describes the API endpoints available in the Cloudstream Bridge Microservice. The service acts as a middle-layer between Jellyfin/other clients and Cloudstream 3 providers.

## Base URL
Default local development: `http://localhost:3018`

---

## 1. Extension Management (`/ext`)

These endpoints handle the lifecycle of extension (JAR) files.

### 1.1 List Installed Extensions
**GET** `/ext/list`

Retrieves a list of all extensions currently installed and loaded in the system.

- **Response (200 OK)**:
  ```json
  [
    {
      "name": "IdlixProvider",
      "pluginUrl": "/app/extensions/IdlixProvider.jar",
      "isInstalled": true,
      "providers": ["Idlix"]
    }
  ]
  ```

### 1.2 Install Extension
**POST** `/ext/install`

Installs a plugin from a repository URL. This is an asynchronous operation. The server waits up to 5 seconds to return a final state; otherwise, it returns a ticket ID.

- **Request Body**: `PluginInstallPayload` (JSON)
- **Response (200 OK / 202 Accepted)**:
  ```json
  {
    "status": "success",
    "ticketId": "uuid-string"
  }
  ```

### 1.3 Update Extension
**POST** `/ext/update`

Re-installs an extension from a provided URL, effectively updating it.

- **Request Body**: `PluginInstallPayload` (JSON)
- **Response**: Same as Install.

### 1.4 Uninstall Extension
**DELETE** `/ext/uninstall?name={pluginName}`

Removes a plugin from the system (memory, disk, and database).

- **Query Parameters**:
  - `name`: The JAR filename without extension (e.g., `IdlixProvider`).
- **Response (200 OK)**:
  ```json
  {
    "message": "Plugin 'IdlixProvider' uninstalled successfully"
  }
  ```

### 1.5 Check Installation Status
**GET** `/ext/ticket/{id}`

Checks the status of an asynchronous installation task.

- **Path Parameters**:
  - `id`: The ticket ID returned by the install/update endpoint.
- **Response (200 OK)**:
  ```json
  {
    "status": "success",
    "error": null
  }
  ```

---

## 2. Functional Bridge API (`/api`)

These endpoints are used by clients to fetch content from providers.

### 2.1 List Providers
**GET** `/api/providers`

Lists all active `MainAPI` providers loaded from installed extensions.

- **Response (200 OK)**:
  ```json
  {
    "count": 1,
    "providers": ["Idlix"]
  }
  ```

### 2.2 Search Content
**GET** `/api/search`

Performs a search across a specific provider.

- **Query Parameters**:
  - `query`: The search term (e.g., `avatar`).
  - `provider`: The internal name of the provider (e.g., `idlix`).
- **Response (200 OK)**:
  ```json
  [
    {
      "name": "Avatar: The Way of Water",
      "url": "https://idlix.com/movie/avatar-the-way-of-water",
      "posterUrl": "https://...",
      "type": "Movie"
    }
  ]
  ```

### 2.3 Load Media Details
**GET** `/api/load`

Retrieves detailed metadata for a specific media URL.

- **Query Parameters**:
  - `url`: The media URL from a search result.
  - `provider`: The internal name of the provider.
- **Response (200 OK)**:
  ```json
  {
    "name": "Avatar: The Way of Water",
    "url": "..."
  }
  ```

### 2.4 Get Streaming Links
**GET** `/api/links`

Extracts streamable links and subtitles from a media URL.

- **Query Parameters**:
  - `url`: The media detail URL.
  - `provider`: The internal name of the provider.
- **Response (200 OK)**:
  ```json
  {
    "links": [
      { "url": "https://...", "name": "Source 1", "type": "quality" }
    ],
    "subtitles": [
      { "url": "https://...", "lang": "Indonesian" }
    ]
  }
  ```

---

## Data Models (Schemas)

### PluginInstallPayload
| Field | Type | Description |
|---|---|---|
| `name` | String | Display name of the plugin |
| `jarUrl` | String | URL to the JAR file |
| `jarHash` | String | SHA-256 hash (e.g., `sha256-hash`) |
| `version` | Int | Version number |
| `url` | String | Source URL in the repository |
| ... | ... | Other metadata fields |
