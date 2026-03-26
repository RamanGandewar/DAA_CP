# ESPRESSO YOURSELF

Production-grade academic project for location-aware cafe discovery, onboarding-driven personalization, and explainable ranking using Design and Analysis of Algorithms (DAA) techniques instead of machine learning.

## Executive Summary

ESPRESSO YOURSELF recommends cafes by combining:
- spatial search using a KD-tree
- deterministic filtering
- onboarding-aware scoring
- intent-aware ranking and shortlist selection
- explainable recommendation output
- branded landing, login, user, and admin experiences
- SQLite-backed user, onboarding, session, and history persistence

The system is designed for local execution, modular extension, and academic evaluation where the core recommendation logic remains algorithmic and interpretable.

---

## 1. Business Goal

The application helps users discover cafes that fit both stable preferences and current intent.

The recommendation engine does not rely on machine learning. Instead, it applies DAA-oriented techniques:
- spatial indexing
- constraint filtering
- weighted scoring
- top-k ranking
- intent-aware shortlist pruning
- fallback search expansion
- deterministic explanation generation

This keeps the system interpretable, modular, and suitable for academic evaluation.

---

## 2. Key Capabilities

### Recommendation Engine
- Nearby-cafe discovery using KD-tree candidate pruning and Haversine validation
- Deterministic filtering over budget, diet, vibe, acoustic profile, and menu-related inputs
- Onboarding-aware scoring using permanent profile and current visit context
- Intent-aware shortlist creation before full ranking
- Top-k extraction using heap-based ranking
- Fallback search when initial constraints return no results

### Onboarding and Personalization
- Permanent profile capture: budget, cafe type, distance range, diet, social habits, ambience preferences
- Dynamic visit context capture: purpose, visit budget, visit distance, time of visit, crowd tolerance
- Profile tagging for dominant usage mode such as work/study or social hangout
- Ranking reasons and explanations tied to actual scoring factors
- Different visit motives now influence ranking more strongly through mismatch penalties and purpose-aware scoring

### Platform and Access
- Branded landing page for public entry
- Shared login and signup flow for users
- Seeded single-admin account for platform oversight
- Admin dashboard for users, onboarding completion, login counts, search counts, and last known location
- Guest mode for demo use without authentication

### Frontend Experience
- Browser-based map with synchronized cafe markers
- Live location tracking with pulsing blue marker and accuracy radius
- Address-based search fallback
- Result-card source badges, coordinates, ranking reason, explanation, and `Show On Map`
- Live seat and table-sharing crowd signals

### Data and Persistence
- CSV prototype support through `data/cafes.csv`
- XLSX pipeline support through `data/micuppa cafe dataset.xlsx`
- Optional geocoded XLSX preference when `data/micuppa cafe dataset.geocoded.xlsx` exists
- SQLite persistence for users, onboarding profiles, active visit context, sessions, recommendation history, and explanation history

---

## 3. Architecture Overview

### Backend
- Runtime: Java
- Server: `com.sun.net.httpserver.HttpServer`
- Core layers:
  - authentication and session handling
  - data ingestion and validation
  - spatial indexing
  - constraint filtering
  - scoring and top-k ranking
  - recommendation orchestration
  - HTTP API layer
  - SQLite persistence layer

### Frontend
- HTML, CSS, JavaScript
- Leaflet for map rendering
- Marker clustering for dense map results
- Browser geolocation and address lookup workflow
- Dedicated public, auth, admin, and user pages

### Data Flow
1. Server loads both dataset pipelines at startup.
2. Landing page routes the visitor to guest mode, user login, or admin login.
3. Authenticated users obtain a session token backed by SQLite.
4. User location is resolved through browser location or address lookup.
5. Stored onboarding profile and current visit context are merged into recommendation input.
6. Recommendation pipeline filters, shortlists, scores, ranks, explains, and optionally stores results.
7. UI renders ranked cards and synchronized map markers.

### Dataset Control
- Both CSV and XLSX pipelines are initialized during startup.
- End users do not choose the pipeline from the user dashboard.
- User recommendation flow is currently fixed to the CSV-backed experience.
- Dataset governance is treated as a controlled platform concern rather than a normal user option.

---

## 4. Repository Structure

```text
C:.
+---data
+---docs
+---images
+---lib
+---out
|   +---app
|   +---data
|   +---db
|   +---filter
|   +---model
|   +---rank
|   +---score
|   +---service
|   +---spatial
|   +---web
+---scripts
|   +---__pycache__
+---src
|   +---app
|   +---data
|   +---db
|   +---filter
|   +---model
|   +---rank
|   +---score
|   +---service
|   +---spatial
|   +---web
+---web
```

---

## 5. Module Responsibilities

### `src/app`
- `Main.java`: application bootstrap, dataset initialization, database initialization, and server startup.

### `src/data`
- `DataLoader.java`: CSV and XLSX ingestion.
- `DataValidator.java`: dataset sanity validation.
- `GlobalStats.java`: normalization support for scoring.

### `src/db`
- `DatabaseManager.java`: SQLite bootstrap and connection factory.
- `SchemaInitializer.java`: schema initialization and migration logic.
- `AdminSeeder.java`: single-admin bootstrap logic.
- `PasswordHasher.java`: password hashing and verification.
- `AuthRepository.java` and `SQLiteAuthRepository.java`: authentication and session persistence.
- `OnboardingRepository.java` and `SQLiteOnboardingRepository.java`: onboarding persistence.
- `DatabaseStatus.java`: database enablement and health reporting.
- `RepositoryException.java`: repository-layer exception wrapper.

### `src/filter`
- `ConstraintFilter.java`: hard filters for diet, budget, and extended preferences.
- `CuisineIndex.java`: cuisine lookup optimization.

### `src/model`
- Cafe, query, onboarding, auth session, admin overview, history, and response-domain models.

### `src/rank`
- `TopKSelector.java`: heap-based top-k selection.

### `src/score`
- `ScoreCalculator.java`: base weighted score.
- `OnboardingScorer.java`: profile-aware and intent-aware ranking logic.
- `OnboardingScoreBreakdown.java`: factor-level explanation support.
- `OnboardingWeights.java`: fixed backend onboarding weight strategy.
- `Weights.java`: base score validation model.

### `src/service`
- `RecommendationService.java`: recommendation orchestration, shortlist logic, ranking reasons, and explanation generation.
- `InsightsService.java`: derived cafe insights.
- `LiveStatusService.java`: runtime live crowd signals.
- `LiveStatus.java`: seat and table-share state model.

### `src/spatial`
- `KDTree.java`: spatial prefilter index.
- `GeoUtils.java`: Haversine distance utilities.

### `src/web`
- `CafeHttpServer.java`: static serving, API routing, auth, admin, onboarding, source control, and recommendation integration.
- `RequestParsers.java`: typed request parsing and validation.
- `JsonUtil.java`: JSON serialization helpers.

### `web`
- `landing.html`: public landing page
- `login.html`, `auth.js`, `auth.css`: login and signup flow
- `admin.html`, `admin.js`: admin dashboard
- `index.html`, `app.js`, `styles.css`: user application, onboarding form, recommendation flow, and map integration

### `docs`
- `onboarding_schema.sql`: reference schema for persistence and onboarding storage.

### `images`
- `LOGO_DAA.png`: logo asset used in the branded header lockups.

### `scripts`
- `geocode_micuppa.py`: optional data enrichment workflow for XLSX coordinates.

### `lib`
- Place `sqlite-jdbc-<version>.jar` here to enable SQLite-backed persistence.

---

## 6. Recommendation Model

### Core Search Pipeline
For each recommendation request, the system performs:
1. request parsing and validation
2. spatial candidate retrieval using KD-tree bounds
3. exact radius filtering using Haversine distance
4. hard constraint filtering
5. intent-aware shortlist construction
6. weighted scoring
7. top-k extraction
8. fallback search if no initial result is found
9. ranking-reason and explanation generation
10. optional persistence of history and session-linked activity

### Base Scoring Factors
- distance score
- price compatibility
- rating quality
- cuisine or category fit

### Onboarding-Aware Scoring Factors
- user profile score
- dynamic context score
- distance score
- budget compatibility
- cafe category match
- ambience match

### Current Personalization Behavior
- Permanent preferences provide a stable baseline.
- Dynamic visit context is weighted strongly enough to change the outcome when motive changes.
- Intent mismatch penalties push down cafes that conflict with the current purpose.
- Purpose-aware shortlist selection helps reduce overlap across work, hangout, date, and quick-coffee searches.
- Users do not control weights from the UI; the backend uses generalized fixed weights for consistency.

### Explanation Generation
Each recommendation is returned with:
- `rankingReason`: compact reason such as `Ranked higher for: casual hangout + lively + evening`
- `explanation`: longer natural-language reasoning based on the strongest matched signals

---

## 7. DAA Core Reasoning

The system is intentionally grounded in Design and Analysis of Algorithms. Even though the project now includes onboarding, authentication, admin oversight, richer UI behavior, and persistence, the recommendation core still depends on classical algorithmic ideas:
- spatial indexing through a KD-tree to reduce search space
- deterministic filtering to eliminate infeasible candidates early
- intent-aware shortlist pruning to reduce unnecessary full scoring
- weighted scoring to evaluate feasible candidates without black-box learning
- heap-based top-k selection to avoid unnecessary full sorting
- fallback expansion to maintain graceful behavior when strict constraints produce no result

This keeps the project aligned with DAA thinking: reduce the effective input size early, apply predictable scoring, and optimize query cost instead of relying on model training.

### Time and Space Complexity
Let:
- `n` = total cafes in the active dataset
- `m` = cafes remaining after spatial pruning and hard filtering
- `s` = cafes remaining after intent-aware shortlist creation
- `k` = requested number of recommendations
- `c` = number of active category or preference checks used during scoring

#### Build-Time Complexity
- Data loading:
  - CSV ingestion: `O(n)`
  - XLSX ingestion: `O(n)` over row traversal
- KD-tree construction:
  - typical current implementation: `O(n log^2 n)`
  - space: `O(n)`
- Insight and derived metadata preparation:
  - time: `O(n)`
  - additional space: `O(n)`

#### Query-Time Complexity
- KD-tree candidate search:
  - average: `O(log n + m)`
  - worst case: `O(n)`
- Exact Haversine refinement:
  - time: `O(m)`
  - extra space: `O(m)`
- Hard filtering:
  - time: `O(m)`
  - extra space: `O(m)`
- Intent-aware shortlist:
  - time: `O(m)`
  - extra space: `O(s)`
- Scoring:
  - time: `O(s * c)`
  - extra space: `O(s)`
- Top-k heap selection:
  - time: `O(s log k)`
  - extra space: `O(k)`

#### End-to-End Recommendation Cost
- Average practical query cost: `O(log n + m + s * c + s log k)`
- Worst-case query cost: `O(n log k)` when pruning is ineffective and most cafes survive to ranking
- Dominant persistent memory footprint: `O(n)` for loaded cafes, indexes, and derived runtime metadata

#### Why This Matters
- KD-tree pruning prevents scoring every cafe on every query.
- Intent-aware shortlist construction improves practical ranking efficiency and personalization separation.
- Heap-based ranking is more efficient than full sorting when only top results are needed.
- Deterministic onboarding scoring adds personalization without changing the algorithmic character of the pipeline.

---

## 8. Onboarding and Identity Model

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

### User and Admin Model
- many normal users may register
- exactly one seeded admin is created if absent
- admin is not publicly created through signup
- authenticated users keep onboarding, session, and history state in SQLite

### Stored Outcomes
- user record
- session record with last known location source
- saved onboarding profile
- active dynamic context
- dominant profile tag
- recommendation history and explanation records

---

## 9. Data Sources

### CSV Prototype
- File: `data/cafes.csv`
- Used by the current user-facing recommendation experience

### XLSX Prototype
- File: `data/micuppa cafe dataset.xlsx`
- Loaded at startup as an alternative pipeline
- If `data/micuppa cafe dataset.geocoded.xlsx` exists, it is preferred automatically

### Derived XLSX Values
When the raw XLSX data does not contain all runtime fields, the loader derives operational values such as:
- approximate coordinates
- estimated price level
- estimated rating
- diet flags inferred from text

---

## 10. API Summary

Base URL: `http://localhost:<port>`

### Health and Search
- `GET /api/health`
  - returns service status, supported sources, and database status
- `GET /api/recommend`
  - returns ranked recommendations, source, profile information, ranking reasons, explanations, and live status

### Authentication and Admin APIs
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/admin/overview`

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

## 11. Setup and Run

### Prerequisites
- Java JDK
- PowerShell on Windows
- Python 3 only if geocoding workflow is needed
- Python packages for geocoding: `pandas`, `requests`, `openpyxl`
- `sqlite-jdbc-<version>.jar` in `lib`

### Start the Application
```powershell
.\run.ps1
```

### Runtime Behavior
- The app starts on the first available local port in the configured range.
- Both dataset pipelines are prepared during startup.
- SQLite initializes `data/cafe_recommendation.db` automatically when the JDBC jar is present.
- A seeded admin account is created if no admin exists.
- User recommendation flow runs through the user dashboard and no longer exposes weight or dataset controls.

### Seeded Admin
- Name: `System Administrator`
- Email: `admin@cafevibefinder.local`
- Initial Password: `Admin@123`
- Role: `ADMIN`

---

## 12. Frontend User Flow

1. Open the local application URL shown in the terminal.
2. Start from the landing page.
3. Continue as guest or use login and signup.
4. Save or load onboarding profile for the current authenticated user.
5. Resolve location by browser live location or address lookup.
6. Submit the search request.
7. Review ranked recommendation cards with ranking reasons, explanations, source badges, coordinates, and map actions.
8. Inspect recommended cafes on the map with fitted bounds and marker focus.
9. Optionally send live seat or table-sharing updates.

### Admin Flow
1. Login through the shared auth page.
2. Open the admin dashboard.
3. Review user count, login count, search count, onboarding completion, and user activity summaries.

---

## 13. Geocoding Workflow

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

## 14. Operational Notes

- Live seat and table-sharing signals are currently in-memory and reset on restart.
- Searches no longer assume a hardcoded city-center location.
- The system requires a real resolved location before recommendation execution.
- User-facing weights are fixed in the backend and not exposed in the UI.
- Dataset switching is not exposed to the end user dashboard.
- The architecture is intentionally modular so new scoring factors can be added without redesigning the full pipeline.

---

## 15. Validation Notes

Current validation performed during development:
- Java compile checks across source files
- health endpoint checks
- authentication and admin flow integration checks
- recommendation flow smoke validation
- onboarding API integration checks
- documentation alignment with implemented modules and UI behavior

Recommended next validation steps:
- unit tests for scoring and onboarding merge logic
- integration tests for CSV and XLSX search paths
- repository tests for SQLite persistence behavior
- browser workflow regression checks for landing, auth, admin, onboarding, and location resolution

---

## 16. Current Limitations

- The platform is local-first and not hardened for internet-facing production deployment.
- Live crowd state is not yet persisted across restarts.
- XLSX accuracy depends on the quality of derived or geocoded fields.
- Dataset governance is still simple and not yet exposed as a mature admin-control workflow.
- Auth is suitable for local academic use, but production-grade security hardening would still require more work.

---

## 17. Roadmap

- Add deeper admin analytics and user drill-down history
- Persist live signal state in SQLite or another durable store
- Add automated test coverage and CI checks
- Introduce stronger input-contract validation and API test fixtures
- Expand benchmarking for latency and ranking throughput
- Expand onboarding factors and scoring trace output

---

## 18. Quick Start

1. Keep dataset files in `data/`.
2. Add `sqlite-jdbc-<version>.jar` to `lib/`.
3. Run `.\run.ps1` from PowerShell.
4. Open the displayed local URL.
5. Login, complete onboarding, resolve location, and search cafes.

---

## 19. Developer

**Raman** — [https://github.com/RamanGandewar](https://github.com/RamanGandewar)
