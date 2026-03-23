# Cafe Vibe Finder and Recommendation Engine

Production-grade academic project for location-aware cafe discovery, onboarding-driven personalization, and explainable ranking using Design and Analysis of Algorithms (DAA) techniques instead of machine learning.

## Executive Summary

Cafe Vibe Finder recommends cafes by combining:
- spatial search using a KD-tree
- deterministic filtering and scoring
- structured onboarding with permanent and dynamic preferences
- explainable recommendation output
- dual dataset support for CSV and XLSX pipelines
- interactive browser-based map exploration
- optional SQLite-backed onboarding persistence

The system is designed for local execution, demo readiness, modular extension, and requirement traceability.

---

## 1. Business Goal

The application helps users discover cafes that fit both stable preferences and current intent.

The recommendation engine does not rely on machine learning. Instead, it applies DAA-oriented techniques:
- spatial indexing
- constraint filtering
- weighted scoring
- top-k ranking
- fallback search expansion
- deterministic explanation generation

This keeps the system interpretable, modular, and suitable for academic evaluation.

---

## 2. Key Capabilities

### Recommendation Engine
- Nearby-cafe discovery using KD-tree candidate pruning and Haversine validation
- Weighted ranking over distance, price, rating, and cuisine/category fit
- Onboarding-aware scoring using user profile and visit context
- Top-k extraction using heap-based ranking
- Fallback search when initial constraints return no results

### Onboarding and Personalization
- Permanent profile capture: budget, cafe type, distance range, diet, social habits, ambience preferences
- Dynamic visit context capture: purpose, visit budget, visit distance, time of visit, crowd tolerance
- Profile tagging for dominant usage mode such as work/study or social hangout
- Recommendation explanations tied to actual scoring factors

### Frontend Experience
- Browser-based map with marker clustering
- Dataset selector for CSV vs XLSX pipeline
- Live location or address-based search
- Blue user-location marker and accuracy radius
- Result-card source badges, coordinates, and `Show On Map`
- Live seat and table-sharing crowd signals

### Data and Persistence
- CSV prototype support through `data/cafes.csv`
- XLSX pipeline support through `data/micuppa cafe dataset.xlsx`
- Optional geocoded XLSX preference when `data/micuppa cafe dataset.geocoded.xlsx` exists
- Optional SQLite persistence for onboarding profile, active visit context, recommendation history, and explanation history

---

## 3. Architecture Overview

### Backend
- Runtime: Java
- Server: `com.sun.net.httpserver.HttpServer`
- Core layers:
  - data ingestion and validation
  - spatial indexing
  - constraint filtering
  - scoring and top-k ranking
  - recommendation orchestration
  - HTTP API layer
  - optional SQLite persistence layer

### Frontend
- HTML, CSS, JavaScript
- Leaflet for map rendering
- Marker clustering for dense map results
- Browser geolocation and address lookup workflow

### Data Flow
1. Server loads both dataset pipelines at startup.
2. UI selects the active source per request: `csv` or `xlsx`.
3. User location is resolved through browser location or address lookup.
4. User profile and dynamic context are merged into the request flow.
5. Recommendation pipeline filters, scores, ranks, and explains results.
6. UI renders ranked cards and synchronized map markers.

---

## 4. Repository Structure

```text
C:.
+---data
+---docs
+---lib
+---out
¦   +---app
¦   +---data
¦   +---db
¦   +---filter
¦   +---model
¦   +---rank
¦   +---score
¦   +---service
¦   +---spatial
¦   +---web
+---scripts
¦   +---__pycache__
+---src
¦   +---app
¦   +---data
¦   +---db
¦   +---filter
¦   +---model
¦   +---rank
¦   +---score
¦   +---service
¦   +---spatial
¦   +---web
+---web
```

---

## 5. Module Responsibilities

### `src/app`
- `Main.java`: application bootstrap, dataset initialization, and server startup.

### `src/data`
- `DataLoader.java`: CSV and XLSX ingestion.
- `DataValidator.java`: dataset sanity validation.
- `GlobalStats.java`: normalization support for scoring.

### `src/db`
- `DatabaseManager.java`: SQLite bootstrap and connection factory.
- `SchemaInitializer.java`: onboarding schema initialization.
- `DatabaseStatus.java`: database enablement and health reporting.
- `OnboardingRepository.java`: repository contract.
- `SQLiteOnboardingRepository.java`: SQLite implementation for onboarding persistence.
- `RepositoryException.java`: repository-layer exception wrapper.

### `src/filter`
- `ConstraintFilter.java`: hard filters for diet, budget, and extended preferences.
- `CuisineIndex.java`: cuisine lookup optimization.

### `src/model`
- Cafe, query, onboarding, history, and response-domain models.

### `src/rank`
- `TopKSelector.java`: heap-based top-k selection.

### `src/score`
- `ScoreCalculator.java`: base weighted score.
- `OnboardingScorer.java`: profile-aware ranking logic.
- `OnboardingScoreBreakdown.java`: factor-level explanation support.
- `OnboardingWeights.java` and `Weights.java`: scoring-weight validation.

### `src/service`
- `RecommendationService.java`: recommendation orchestration and explanation generation.
- `InsightsService.java`: derived cafe insights.
- `LiveStatusService.java`: runtime live crowd signals.
- `LiveStatus.java`: seat and table-share state model.

### `src/spatial`
- `KDTree.java`: spatial prefilter index.
- `GeoUtils.java`: Haversine distance utilities.

### `src/web`
- `CafeHttpServer.java`: static serving, API routing, source selection, and onboarding integration.
- `RequestParsers.java`: typed request parsing and validation.
- `JsonUtil.java`: JSON serialization helpers.

### `web`
- `index.html`, `styles.css`, `app.js`: browser UI, onboarding form, recommendation flow, and map integration.

### `docs`
- `onboarding_schema.sql`: reference schema for onboarding storage.

### `scripts`
- `geocode_micuppa.py`: optional data enrichment workflow for XLSX coordinates.

### `lib`
- Place `sqlite-jdbc-<version>.jar` here to enable SQLite-backed onboarding persistence.

---

## 6. Recommendation Model

### Core Search Pipeline
For each recommendation request, the system performs:
1. request parsing and validation
2. spatial candidate retrieval using KD-tree bounds
3. exact radius filtering using Haversine distance
4. hard constraint filtering
5. weighted scoring
6. top-k extraction
7. fallback search if no initial result is found
8. explanation generation
9. optional persistence of history when SQLite is enabled

### Base Scoring Factors
- distance score
- price compatibility
- rating quality
- cuisine/category fit

### Onboarding-Aware Scoring Factors
- user profile score
- dynamic context score
- distance score
- budget compatibility
- cafe category match
- ambience match

### Explanation Generation
Each recommendation is returned with a human-readable explanation based on the strongest matched ranking signals, for example:
- work-friendly preference match
- budget compatibility
- distance fit
- ambience alignment
- dynamic visit purpose fit

---

## 7. Onboarding Model

### Permanent Profile
- name
- age group
- occupation
- default budget range
- preferred cafe type
- preferred distance range
- dietary preference
- usual visit type
- seating preference
- music preference
- lighting preference

### Dynamic Visit Context
- purpose of visit
- current visit budget
- travel distance for this visit
- time of visit
- crowd tolerance

### Stored Outcomes
- saved onboarding profile
- active dynamic context
- dominant profile tag
- recommendation history and explanation records when SQLite is enabled

---

## 8. Data Sources

### CSV Prototype
- File: `data/cafes.csv`
- Best for stable structured records and baseline prototype runs

### XLSX Prototype
- File: `data/micuppa cafe dataset.xlsx`
- Used when the user selects the XLSX source in the UI
- If `data/micuppa cafe dataset.geocoded.xlsx` exists, it is preferred automatically

### Derived XLSX Values
When the raw XLSX data does not contain all runtime fields, the loader derives operational values such as:
- approximate coordinates
- estimated price level
- estimated rating
- diet flags inferred from text

---

## 9. API Summary

Base URL: `http://localhost:<port>`

### Health and Search
- `GET /api/health`
  - returns service status, available sources, and database status
- `GET /api/recommend`
  - returns ranked recommendations, source, explanations, profile information, and live status

### Onboarding APIs
- `GET /api/onboarding/status?userKey=<key>`
- `GET /api/onboarding/profile?userKey=<key>`
- `POST /api/onboarding/profile?...`
- `GET /api/onboarding/context?userKey=<key>`
- `POST /api/onboarding/context?...`

### Live Signal APIs
- `POST /api/live/seat?cafeId=<id>&status=<easy|standing>`
- `POST /api/live/table-share?cafeId=<id>&available=<true|false>`
- `GET /api/live/seat?cafeId=<id>`

---

## 10. Setup and Run

### Prerequisites
- Java JDK
- PowerShell on Windows
- Python 3 only if geocoding workflow is needed
- Python packages for geocoding: `pandas`, `requests`, `openpyxl`
- `sqlite-jdbc` jar in `lib` if database-backed onboarding is required

### Start the Application
```powershell
.\run.ps1
```

### Runtime Behavior
- The app starts on the first available local port in the configured range.
- Both dataset pipelines are prepared during startup.
- The user chooses `CSV Prototype` or `XLSX Prototype` in the UI.
- If `lib\sqlite-jdbc-<version>.jar` exists, the app initializes `data/cafe_recommendation.db` automatically.
- If the SQLite JDBC jar is missing, onboarding remains available in browser-local mode and server-side DB persistence stays disabled.

---

## 11. Frontend User Flow

1. Open the local application URL shown in the terminal.
2. Save or load onboarding profile for the current user key.
3. Resolve location by browser live location or address lookup.
4. Choose dataset method and search preferences.
5. Submit the search request.
6. Review ranked recommendation cards with explanations, source badges, coordinates, and map actions.
7. Inspect recommended cafes on the map with fitted bounds and marker focus.
8. Optionally send live seat or table-sharing updates.

---

## 12. Geocoding Workflow

Run the optional enrichment script:

```powershell
python scripts\geocode_micuppa.py
```

Generated artifacts:
- `data/micuppa cafe dataset.geocoded.xlsx`
- `data/geocode_cache.json`
- `data/geocode_failures.csv`

The application automatically prefers the geocoded XLSX file when present.

---

## 13. Operational Notes

- Live seat and table-sharing signals are currently in-memory and reset on restart.
- Searches no longer assume a hardcoded city-center location.
- The system requires a real resolved location before recommendation execution.
- SQLite persistence is conditional on the presence of the JDBC jar.
- The architecture is intentionally modular so new scoring factors can be added without redesigning the full pipeline.

---

## 14. Validation Notes

Current validation performed during development:
- Java compile checks across source files
- health endpoint checks
- recommendation flow smoke validation
- onboarding API integration checks
- documentation alignment with implemented modules and UI behavior

Recommended next validation steps:
- unit tests for scoring and onboarding merge logic
- integration tests for `csv` and `xlsx` search paths
- repository tests for SQLite persistence behavior
- browser workflow regression checks for onboarding and location resolution

---

## 15. Current Limitations

- SQLite-backed onboarding is disabled until `sqlite-jdbc` is placed in `lib`.
- XLSX accuracy depends on the quality of derived or geocoded fields.
- Live crowd state is not yet persisted across restarts.
- Weight validation is strict and expects exact valid input.
- The project is local-first and not yet hardened for multi-user production deployment.

---

## 16. Roadmap

- Persist live signal state in SQLite or another durable store
- Add automated test coverage and CI checks
- Introduce stronger input-contract validation and API test fixtures
- Add authentication and multi-user profile ownership
- Add benchmarking for latency and ranking throughput
- Expand onboarding factors and scoring trace output

---

## 17. Quick Start

1. Keep dataset files in `data/`.
2. Add `sqlite-jdbc-<version>.jar` to `lib/` if you want server-side onboarding persistence.
3. Run `./run.ps1` from PowerShell as `./run.ps1` or `.\run.ps1`.
4. Open the displayed local URL.
5. Save onboarding profile, resolve location, choose source, and search cafes.
