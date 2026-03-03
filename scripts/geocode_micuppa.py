#!/usr/bin/env python3
import argparse
import json
import time
from pathlib import Path

import pandas as pd
import requests


def norm_location(raw: str) -> str:
    value = (raw or "").strip()
    return value.replace("from from", "from")

def build_queries(name: str, location: str, city: str, country: str):
    queries = []
    seen = set()

    def add(q: str):
        query = " ".join(q.split()).strip(" ,")
        key = query.lower()
        if query and key not in seen:
            seen.add(key)
            queries.append(query)

    # Most specific to least specific
    add(f"{name}, {location}, {city}, {country}")
    add(f"{name}, {location}, {city}")
    add(f"{name}, {city}, {country}")
    add(f"{name}, {city}")
    add(f"{location}, {city}, {country}")
    add(f"{location}, {city}")

    # Strip common noisy prefix patterns like "0.5 km from JM Road"
    if " from " in location.lower():
        cleaned_loc = location.split(" from ", 1)[-1].strip()
        add(f"{name}, {cleaned_loc}, {city}, {country}")
        add(f"{cleaned_loc}, {city}, {country}")
        add(f"{cleaned_loc}, {city}")

    return queries


def geocode(query: str, session: requests.Session, pause_seconds: float):
    url = "https://nominatim.openstreetmap.org/search"
    params = {"q": query, "format": "jsonv2", "limit": 1}
    time.sleep(pause_seconds)
    response = session.get(url, params=params, timeout=30)
    response.raise_for_status()
    data = response.json()
    if not data:
        return None
    top = data[0]
    return {
        "lat": float(top["lat"]),
        "lon": float(top["lon"]),
        "display_name": top.get("display_name", ""),
        "importance": float(top.get("importance", 0.0) or 0.0),
        "type": top.get("type", ""),
        "class": top.get("class", ""),
    }


def confidence_label(result: dict, city_hint: str) -> str:
    importance = float(result.get("importance", 0.0))
    display = (result.get("display_name", "") or "").lower()
    city_in_display = city_hint.lower() in display if city_hint else False
    place_type = (result.get("type", "") or "").lower()

    if importance >= 0.45 and city_in_display:
        return "HIGH"
    if importance >= 0.20 and (city_in_display or place_type in {"cafe", "restaurant", "commercial"}):
        return "MEDIUM"
    return "LOW"


def main():
    parser = argparse.ArgumentParser(description="Add Latitude/Longitude to micuppa cafe dataset using Nominatim.")
    parser.add_argument("--input", default="data/micuppa cafe dataset.xlsx", help="Input XLSX path")
    parser.add_argument("--output", default="data/micuppa cafe dataset.geocoded.xlsx", help="Output XLSX path")
    parser.add_argument("--cache", default="data/geocode_cache.json", help="Cache JSON path")
    parser.add_argument("--failed-report", default="data/geocode_failures.csv", help="Failed rows report path")
    parser.add_argument("--city", default="Pune", help="City hint for geocoding query")
    parser.add_argument("--country", default="India", help="Country hint for geocoding query")
    parser.add_argument("--pause", type=float, default=1.1, help="Delay between API calls in seconds")
    args = parser.parse_args()

    input_path = Path(args.input)
    output_path = Path(args.output)
    cache_path = Path(args.cache)
    failed_report_path = Path(args.failed_report)

    if not input_path.exists():
        raise FileNotFoundError(f"Input file not found: {input_path}")

    df = pd.read_excel(input_path)
    if "Name" not in df.columns or "Location" not in df.columns:
        raise ValueError("Input XLSX must include 'Name' and 'Location' columns")

    cache = {}
    if cache_path.exists():
        try:
            cache = json.loads(cache_path.read_text(encoding="utf-8"))
        except Exception:
            cache = {}

    if "Latitude" not in df.columns:
        df["Latitude"] = pd.NA
    if "Longitude" not in df.columns:
        df["Longitude"] = pd.NA
    if "GeocodedAddress" not in df.columns:
        df["GeocodedAddress"] = pd.NA
    if "GeocodeStatus" not in df.columns:
        df["GeocodeStatus"] = pd.NA
    if "GeocodeConfidence" not in df.columns:
        df["GeocodeConfidence"] = pd.NA
    if "GeocodeQuery" not in df.columns:
        df["GeocodeQuery"] = pd.NA

    session = requests.Session()
    session.headers.update({
        "User-Agent": "CafeRecoGeocoder/1.0 (local script for academic project)",
        "Accept": "application/json",
    })

    geocoded_count = 0
    failed = 0
    failed_rows = []

    for idx, row in df.iterrows():
        if pd.notna(row.get("Latitude")) and pd.notna(row.get("Longitude")):
            if pd.isna(row.get("GeocodeStatus")):
                df.at[idx, "GeocodeStatus"] = "EXISTING"
            if pd.isna(row.get("GeocodeConfidence")):
                df.at[idx, "GeocodeConfidence"] = "HIGH"
            continue

        name = str(row.get("Name", "")).strip()
        location = norm_location(str(row.get("Location", "")).strip())
        key = f"{name} | {location}".lower()
        query_candidates = build_queries(name, location, args.city, args.country)
        primary_query = query_candidates[0] if query_candidates else ""
        df.at[idx, "GeocodeQuery"] = primary_query

        if key in cache:
            item = cache[key]
            if item.get("lat") is not None and item.get("lon") is not None:
                df.at[idx, "Latitude"] = item["lat"]
                df.at[idx, "Longitude"] = item["lon"]
                df.at[idx, "GeocodedAddress"] = item.get("display_name", "")
                df.at[idx, "GeocodeStatus"] = "CACHED"
                df.at[idx, "GeocodeConfidence"] = item.get("confidence", "MEDIUM")
                geocoded_count += 1
            else:
                df.at[idx, "GeocodeStatus"] = "FAILED_CACHED"
                df.at[idx, "GeocodeConfidence"] = "LOW"
                failed += 1
                failed_rows.append({
                    "row_index": idx,
                    "name": name,
                    "location": location,
                    "query": primary_query,
                    "reason": item.get("reason", "no match in cache")
                })
            continue

        try:
            result = None
            used_query = ""
            for candidate in query_candidates:
                result = geocode(candidate, session, args.pause)
                if result is not None:
                    used_query = candidate
                    break

            if result is not None:
                confidence = confidence_label(result, args.city)
                cache[key] = {
                    "lat": result["lat"],
                    "lon": result["lon"],
                    "display_name": result["display_name"],
                    "confidence": confidence,
                    "query": used_query,
                }
                df.at[idx, "Latitude"] = result["lat"]
                df.at[idx, "Longitude"] = result["lon"]
                df.at[idx, "GeocodedAddress"] = result["display_name"]
                df.at[idx, "GeocodeStatus"] = "GEOCODED"
                df.at[idx, "GeocodeConfidence"] = confidence
                df.at[idx, "GeocodeQuery"] = used_query
                geocoded_count += 1
            else:
                cache[key] = {
                    "lat": None,
                    "lon": None,
                    "display_name": "",
                    "reason": "no result from API",
                    "query": primary_query,
                }
                df.at[idx, "GeocodeStatus"] = "FAILED_NO_MATCH"
                df.at[idx, "GeocodeConfidence"] = "LOW"
                failed += 1
                failed_rows.append({
                    "row_index": idx,
                    "name": name,
                    "location": location,
                    "query": primary_query,
                    "reason": "no result from API"
                })
        except Exception:
            cache[key] = {
                "lat": None,
                "lon": None,
                "display_name": "",
                "reason": "request/parse error",
                "query": primary_query,
            }
            df.at[idx, "GeocodeStatus"] = "FAILED_ERROR"
            df.at[idx, "GeocodeConfidence"] = "LOW"
            failed += 1
            failed_rows.append({
                "row_index": idx,
                "name": name,
                "location": location,
                "query": primary_query,
                "reason": "request/parse error"
            })

    output_path.parent.mkdir(parents=True, exist_ok=True)
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    df.to_excel(output_path, index=False)
    cache_path.write_text(json.dumps(cache, indent=2), encoding="utf-8")
    pd.DataFrame(failed_rows).to_csv(failed_report_path, index=False)

    print(f"Saved geocoded dataset: {output_path}")
    print(f"Rows geocoded this run: {geocoded_count}")
    print(f"Rows failed/no match: {failed}")
    print(f"Failed rows report: {failed_report_path}")


if __name__ == "__main__":
    main()
