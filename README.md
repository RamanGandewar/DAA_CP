# Cafe Vibe Finder and Recommendation Engine

Production-style academic project for location-aware cafe discovery, ranking, and live crowd signals.

This system combines:
- Spatial search (`KDTree`) for nearby cafes
- Constraint filtering (budget, diet, extended preferences)
- Weighted multi-factor scoring
- Efficient top-k selection using a heap
- Frontend map visualization with marker clustering
- Optional live geocoding pipeline for real latitude/longitude from Excel data

---

## 1. Project Purpose

The goal is to recommend the best cafes for a user based on:
- Distance from user location
- Budget fit
- Rating quality
- Cuisine match
- Optional vibe/acoustic/menu/independent-cafe filters

The application serves ranked recommendations through REST APIs and a browser UI.

---

## 2. High-Level Architecture

### Backend (Java)
- HTTP server: `com.sun.net.httpserver.HttpServer`
- Data layer: CSV/XLSX loaders + validation
- Spatial layer: KD-tree + Haversine distance
- Filter layer: budget, diet, and extended constraints
- Ranking layer: weighted score + min-cost top-k extraction
- Insights layer: generated tags and workspace-style metadata
- Live signals: seat availability votes + table sharing status

### Frontend (HTML/CSS/JS)
- Leaflet map + marker clustering
- Search/filter controls
- Ranked result cards
- Live location and address-based search (Nominatim from browser)

### Data Pipeline
- Primary dataset: `data/micuppa cafe dataset.xlsx`
- Optional geocoded dataset: `data/micuppa cafe dataset.geocoded.xlsx`
- Cache and failure logs for geocoding

---

## 3. Repository Structure

```text
Proto2
├── data
├── out
│   ├── app
│   ├── data
│   ├── filter
│   ├── model
│   ├── rank
│   ├── score
│   ├── service
│   ├── spatial
│   └── web
├── scripts
├── src
│   ├── app
│   ├── data
│   ├── filter
│   ├── model
│   ├── rank
│   ├── score
│   ├── service
│   ├── spatial
│   └── web
└── web
```

---

## 4. Core Modules and Responsibilities

### `src/app`
- `Main.java`: selects data source and boots server.
- Uses geocoded XLSX automatically if present.

### `src/data`
- `DataLoader.java`:
  - `loadFromFile(...)` supports both `.csv` and `.xlsx`.
  - XLSX parser reads workbook XML directly (no external Java Excel library).
  - If `Latitude/Longitude` exists in sheet, uses exact coordinates.
  - If absent, derives approximate coordinates and other required fields.
- `DataValidator.java`: sanity check for non-empty dataset.
- `GlobalStats.java`: min/max price range for normalization.

### `src/spatial`
- `KDTree.java`: 2D index on latitude/longitude for radius prefilter.
- `GeoUtils.java`: Haversine great-circle distance.

### `src/filter`
- `ConstraintFilter.java`: budget and diet filters.
- `CuisineIndex.java`: cuisine-to-cafes inverted map (utility index).

### `src/score`
- `ScoreCalculator.java`: weighted score computation.
- `Weights.java`: strict weight validation (sum must be exactly `1.0`).

### `src/rank`
- `TopKSelector.java`: heap-based top-k extraction.

### `src/service`
- `RecommendationService.java`: full recommendation pipeline coordinator.
- `InsightsService.java`: deterministic synthetic cafe insights generation.
- `LiveStatusService.java` and `LiveStatus.java`: concurrent vote state.

### `src/web`
- `CafeHttpServer.java`: API routing + static file serving.
- `RequestParsers.java`: query parsing and typed parameter conversion.
- `JsonUtil.java`: response payload serialization.

### `web`
- `index.html`, `styles.css`, `app.js`: client UI, map, API integration.

### `scripts`
- `geocode_micuppa.py`: enrich Excel rows with real coordinates using Nominatim.

---

## 5. Data Model and Dataset Mapping

### Runtime Cafe model (`model.Cafe`)
Required by ranking pipeline:
- `id`, `name`
- `latitude`, `longitude`
- `cuisines` (set)
- `avgPrice`, `rating`
- `veg`, `nonVeg`, `vegan`
- `operatingHours`, `address`, `contact`

### XLSX source columns (current dataset)
- `Name`, `Location`, `Food`, `Ambience`, `Event`, `Wi-Fi`, `Time`
- `No. of People`, `Speciality`, `MaxCapacity`, `Ambiencetype`

### Handling missing fields
If geocoding is not done yet, loader derives missing required fields:
- Coordinates: deterministic location-cluster approximation
- Price: estimated from `MaxCapacity` and `No. of People`
- Rating: estimated from Wi-Fi/event/ambience richness
- Diet flags: inferred from food keywords

Once geocoded file includes `Latitude` and `Longitude`, exact coordinates are used automatically.

---

## 6. Recommendation Workflow

For each `/api/recommend` request:

1. Parse request parameters  
2. KD-tree bounding-box candidate retrieval  
3. Precise radius check using Haversine  
4. Hard filters:
   - budget
   - dietary preference
   - extended filters (`indieOnly`, menu query, vibe tags, acoustic profile)
5. Score each remaining cafe  
6. Select top-k recommendations  
7. If empty, run fallback with expanded radius (`2x`) and lowest-price fallback ordering  
8. Return enriched JSON (insights + live status)

---

## 7. Scoring Design

### Weighted objective
Lower score is better.

\[
\text{score} = w_d \cdot D + w_p \cdot P + w_r \cdot R + w_c \cdot C
\]

Where:
- `D`: normalized distance (`distanceKm / radiusKm`)
- `P`: normalized price (`(price - minPrice) / (maxPrice - minPrice)`)
- `R`: rating penalty (`(5 - rating)/4`, clamped)
- `C`: cuisine mismatch (`1 - cuisineMatchRatio`)

Constraints:
- `w_d + w_p + w_r + w_c = 1.0` (strictly enforced)

### Tie-breaking in ranking
If two cafes have same score:
1. Higher rating first
2. Lower distance first

---

## 8. Algorithm and Complexity Analysis

Let:
- `n` = total cafes
- `m` = cafes within geographic candidates/filters
- `k` = requested top-k
- `c` = number of preferred cuisines

### Build-time complexity
- Data load:
  - CSV parsing: `O(n)`
  - XLSX parsing: `O(n)` over row/cell traversal
- KD-tree build (current implementation sorts at each recursion):
  - Typical: `O(n log^2 n)`
  - Space: `O(n)`
- Insights generation:
  - `O(n)` average (small bounded feature generation)

### Query-time complexity
- KD-tree range search:
  - Average: `O(log n + m)`
  - Worst case: `O(n)`
- Haversine refinement:
  - `O(m)`
- Constraint filters:
  - `O(m)`
- Scoring:
  - `O(m * c)` for cuisine overlap + constant-time terms
- Top-k with heap:
  - `O(m log k)`
  - Space: `O(k)`

### End-to-end per request
- Average practical: `O(log n + m log k + m*c)`
- Worst case: `O(n log k)` if candidate set grows near full dataset

### Why this design is efficient
- Spatial pruning avoids scoring all `n` cafes.
- Heap-based top-k avoids full sort (`O(m log m)`), using `O(m log k)` instead.
- Deterministic preprocessing keeps request path lightweight.

---

## 9. API Reference

Base URL: `http://localhost:<port>`

### `GET /api/health`
- Response: `{"status":"ok"}`

### `GET /api/recommend`
Required:
- `lat`, `lon`

Optional:
- `radius` (default `5`)
- `budget` (default `500`)
- `k` (default `20`)
- `cuisines` (comma-separated)
- `diet` (`ANY`, `VEG`, `NON_VEG`, `VEGAN`)
- `w1`, `w2`, `w3`, `w4` (must sum to `1.0`)
- `vibes` (comma-separated tags)
- `menuQuery` (string)
- `acoustic` (`Library Quiet`, `Lo-fi Beats`, `Active Chatter`)
- `indieOnly` (`true/false`)

### `POST /api/live/seat?cafeId=<id>&status=<easy|standing>`
- Increments seat signal counters.

### `POST /api/live/table-share?cafeId=<id>&available=<true|false>`
- Updates table sharing availability.

### `GET /api/live/seat?cafeId=<id>`
- Returns current live status counters for cafe.

---

## 10. Setup and Run

## Prerequisites
- Java JDK (for `javac` and `java`)
- PowerShell (Windows)
- Python 3 (only for geocoding script)
- Python packages: `pandas`, `requests`, `openpyxl`

Install Python packages:

```powershell
pip install pandas requests openpyxl
```

Compile and run app:

```powershell
.\run.ps1
```

App starts on first free port in `8080..8099` range (as per script checks).

---

## 11. Geocoding Workflow (Recommended)

Run geocoder:

```powershell
python scripts\geocode_micuppa.py
```

Generated artifacts:
- `data/micuppa cafe dataset.geocoded.xlsx`
- `data/geocode_cache.json`
- `data/geocode_failures.csv`

Added columns in geocoded Excel:
- `Latitude`, `Longitude`, `GeocodedAddress`
- `GeocodeStatus`, `GeocodeConfidence`, `GeocodeQuery`

Script behavior:
- Multi-pass query fallbacks from specific to broad
- Caching to avoid repeated API calls
- Failure report for manual correction loop

After geocoding, application automatically prefers geocoded Excel at startup.

---

## 12. Frontend Workflow

1. Acquire user location:
   - Browser geolocation button, or
   - Address to coordinate lookup via Nominatim
2. Set filters and weights
3. Submit recommendation query
4. Visualize:
   - Circle radius on map
   - Marker clusters
   - Ranked cards with insights and live seat controls
5. Crowd users update seat/table-share status in real time via APIs

---

## 13. Validation and Testing Notes

Manual validation checkpoints used:
- Compilation test for all Java source files
- Health endpoint check
- Recommendation endpoint smoke test
- Geocoder script syntax and CLI checks

Recommended additional tests:
- Unit tests for scoring and weight validation
- Integration tests for `/api/recommend` parameter combinations
- Geocoding regression test over cached dataset keys

---

## 14. Assumptions and Limitations

- Current live status is in-memory; resets on server restart.
- Without geocoded coordinates, spatial precision is approximate.
- Geocoding quality depends on input text quality and external Nominatim coverage.
- Weight sum uses strict floating check; UI should keep exact sums to avoid validation errors.
- XLSX reader is custom lightweight XML parsing for current sheet shape.

---

## 15. Future Enhancements

- Persist live seat signals to database
- Add automated data cleaning preprocessor before geocoding
- Introduce confidence-aware ranking penalty for uncertain coordinates
- Replace synthetic insights with real observed metadata
- Add benchmark suite for latency and throughput
- Support incremental KD-tree rebuild for online dataset updates

---

## 16. Quick Start Summary

1. Keep source dataset in `data/micuppa cafe dataset.xlsx`
2. Run geocoder once for exact coordinates
3. Start app with `.\run.ps1`
4. Open displayed local URL and search cafes
5. Use `data/geocode_failures.csv` to improve unresolved entries and rerun geocoder

