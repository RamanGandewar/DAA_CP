import argparse
import csv
import json
import os
import re
import sys
import time
import zipfile
from pathlib import Path
from typing import Dict, Iterable, List, Tuple
from xml.etree import ElementTree as ET

import requests


DEFAULT_MODEL = "gemini-2.0-flash"
DEFAULT_TIMEOUT = 45
DEFAULT_PAUSE = 1.0
DEFAULT_RETRIES = 5


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Offline Gemini enrichment for cafe occasion-fit metadata."
    )
    parser.add_argument("--input", required=True, help="Path to cafes.csv or XLSX dataset.")
    parser.add_argument(
        "--output",
        default="",
        help="Output TSV path. Defaults to <dataset-base>.enrichment.tsv beside the dataset.",
    )
    parser.add_argument(
        "--api-key",
        default=os.environ.get("GEMINI_API_KEY", ""),
        help="Gemini API key. Prefer setting GEMINI_API_KEY instead of passing it on the CLI.",
    )
    parser.add_argument("--model", default=DEFAULT_MODEL, help="Gemini model name.")
    parser.add_argument("--pause", type=float, default=DEFAULT_PAUSE, help="Pause in seconds between requests.")
    parser.add_argument("--retries", type=int, default=DEFAULT_RETRIES, help="Retry count for rate limits or transient failures.")
    parser.add_argument("--start", type=int, default=1, help="1-based start index for batch processing.")
    parser.add_argument("--limit", type=int, default=0, help="Optional max number of cafes to enrich.")
    parser.add_argument("--batch-size", type=int, default=10, help="Number of cafes to send in a single Gemini request.")
    parser.add_argument("--resume", action="store_true", help="Skip cafe IDs already present in output TSV.")
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        raise SystemExit(f"Input file not found: {input_path}")

    if not args.api_key:
        raise SystemExit("Missing Gemini API key. Set GEMINI_API_KEY or pass --api-key.")

    output_path = Path(args.output) if args.output else input_path.with_suffix(".enrichment.tsv")

    cafes = load_dataset(input_path)
    start_index = max(1, args.start) - 1
    if start_index >= len(cafes):
        print(f"Start index {args.start} is beyond dataset size {len(cafes)}.")
        return 0
    cafes = cafes[start_index:]
    if args.limit > 0:
        cafes = cafes[: args.limit]

    existing_ids = load_existing_ids(output_path) if args.resume else set()
    pending = []
    for idx, cafe in enumerate(cafes, start=1):
        cafe_id = cafe["cafe_id"]
        if cafe_id in existing_ids:
            print(f"[skip] {cafe_id} already enriched")
            continue
        pending.append((start_index + idx, cafe))

    if not pending:
        print("No cafes left to enrich for this selection.")
        return 0

    append_mode = output_path.exists() or bool(existing_ids)
    batch_size = max(1, args.batch_size)

    for batch_start in range(0, len(pending), batch_size):
        chunk = pending[batch_start: batch_start + batch_size]
        payloads = request_enrichment_batch([item[1] for item in chunk], args.api_key, args.model, args.retries)
        rows_to_write: List[List[str]] = []
        payload_by_id = {str(payload["cafe_id"]): payload for payload in payloads}

        for dataset_index, cafe in chunk:
            cafe_id = cafe["cafe_id"]
            payload = payload_by_id.get(cafe_id)
            if payload is None:
                print(f"[warn] Missing payload for {cafe_id}; skipped")
                continue
            rows_to_write.append(to_tsv_row(cafe_id, payload))
            print(f"[ok] {cafe_id} (dataset index {dataset_index})")

        write_rows(output_path, rows_to_write, append=append_mode)
        append_mode = True
        time.sleep(max(0.0, args.pause))

    return 0


def load_dataset(path: Path) -> List[Dict[str, str]]:
    if path.suffix.lower() == ".csv":
        return load_csv(path)
    if path.suffix.lower() == ".xlsx":
        return load_xlsx(path)
    raise SystemExit(f"Unsupported input format: {path.suffix}")


def load_csv(path: Path) -> List[Dict[str, str]]:
    last_error = None
    for encoding in ("utf-8", "windows-1252"):
        cafes: List[Dict[str, str]] = []
        try:
            with path.open("r", encoding=encoding, newline="") as fh:
                reader = csv.DictReader(fh)
                for row in reader:
                    cafes.append(
                        {
                            "cafe_id": safe(row.get("id")),
                            "name": safe(row.get("name")),
                            "location": safe(row.get("address")),
                            "food": ",".join(filter(None, [safe(row.get("cuisines"))])),
                            "ambience": "",
                            "speciality": "",
                            "hours": safe(row.get("operatingHours")),
                            "price": safe(row.get("avgPrice")),
                            "rating": safe(row.get("rating")),
                        }
                    )
            return cafes
        except UnicodeDecodeError as exc:
            last_error = exc
    raise last_error if last_error is not None else RuntimeError(f"Unable to read CSV: {path}")


def load_xlsx(path: Path) -> List[Dict[str, str]]:
    rows = parse_first_sheet(path)
    if not rows:
        return []
    headers = rows[0]
    cafes: List[Dict[str, str]] = []
    row_number = 1
    for row in rows[1:]:
        row_number += 1
        record = normalize_row(headers, row)
        name = safe(record.get("Name"))
        location = clean_location(safe(record.get("Location")))
        if not name or not location:
            continue
        cafe_id = build_xlsx_id(name, location, row_number)
        cafes.append(
            {
                "cafe_id": cafe_id,
                "name": name,
                "location": location,
                "food": safe(record.get("Food")),
                "ambience": safe(record.get("Ambience")) + " " + safe(record.get("Ambiencetype")),
                "speciality": safe(record.get("Speciality")),
                "hours": safe(record.get("Time")),
                "price": safe(record.get("MaxCapacity")),
                "rating": "",
            }
        )
    return cafes


def parse_first_sheet(path: Path) -> List[Dict[int, str]]:
    with zipfile.ZipFile(path) as zf:
        shared = parse_shared_strings(zf)
        sheet_name = "xl/worksheets/sheet1.xml"
        if sheet_name not in zf.namelist():
            return []
        root = ET.fromstring(zf.read(sheet_name))
        ns = {"x": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
        rows: List[Dict[int, str]] = []
        for row_node in root.findall(".//x:sheetData/x:row", ns):
            row: Dict[int, str] = {}
            for cell in row_node.findall("x:c", ns):
                ref = cell.attrib.get("r", "")
                col = column_index(ref)
                cell_type = cell.attrib.get("t", "")
                value = ""
                if cell_type == "inlineStr":
                    text_node = cell.find(".//x:t", ns)
                    value = text_node.text if text_node is not None and text_node.text else ""
                else:
                    v_node = cell.find("x:v", ns)
                    if v_node is not None and v_node.text:
                        value = v_node.text.strip()
                        if cell_type == "s":
                            value = shared[int(value)] if value.isdigit() and int(value) < len(shared) else value
                row[col] = value.strip()
            rows.append(row)
        return rows


def parse_shared_strings(zf: zipfile.ZipFile) -> List[str]:
    name = "xl/sharedStrings.xml"
    if name not in zf.namelist():
        return []
    root = ET.fromstring(zf.read(name))
    ns = {"x": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
    values: List[str] = []
    for si in root.findall("x:si", ns):
        text_parts = []
        for text_node in si.findall(".//x:t", ns):
            text_parts.append(text_node.text or "")
        values.append("".join(text_parts))
    return values


def normalize_row(headers: Dict[int, str], row: Dict[int, str]) -> Dict[str, str]:
    out: Dict[str, str] = {}
    for idx, header in headers.items():
        out[header] = row.get(idx, "")
    return out


def column_index(cell_ref: str) -> int:
    result = 0
    for ch in cell_ref:
        if ch.isalpha():
            result = result * 26 + (ord(ch.upper()) - ord("A") + 1)
        else:
            break
    return result - 1


def build_xlsx_id(name: str, location: str, row_number: int) -> str:
    base = re.sub(r"[^a-z0-9]+", "-", f"{name}-{location}".lower()).strip("-")
    return f"{base}-{row_number}"


def clean_location(value: str) -> str:
    return re.sub(r"(?i)\bfrom\s+from\b", "from", value).strip()


def request_enrichment_batch(cafes: List[Dict[str, str]], api_key: str, model: str, retries: int) -> List[Dict[str, object]]:
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
    prompt = build_prompt(cafes)
    body = {
        "contents": [
            {
                "parts": [
                    {"text": prompt}
                ]
            }
        ],
        "generationConfig": {
            "temperature": 0.1,
            "responseMimeType": "application/json"
        }
    }
    last_error = None
    for attempt in range(retries + 1):
        try:
            response = requests.post(url, json=body, timeout=DEFAULT_TIMEOUT)
            if response.status_code == 429 and attempt < retries:
                sleep_seconds = min(60.0, (2 ** attempt) * 3.0)
                time.sleep(sleep_seconds)
                continue
            response.raise_for_status()
            data = response.json()
            text = extract_text(data)
            payload = json.loads(text)
            return normalize_payloads(payload)
        except requests.HTTPError as exc:
            last_error = exc
            if exc.response is not None and exc.response.status_code in (429, 500, 502, 503, 504) and attempt < retries:
                sleep_seconds = min(60.0, (2 ** attempt) * 3.0)
                time.sleep(sleep_seconds)
                continue
            raise
        except (requests.RequestException, json.JSONDecodeError, ValueError) as exc:
            last_error = exc
            if attempt < retries:
                sleep_seconds = min(30.0, (2 ** attempt) * 2.0)
                time.sleep(sleep_seconds)
                continue
            raise
    raise last_error if last_error is not None else RuntimeError("Gemini enrichment failed.")


def build_prompt(cafes: List[Dict[str, str]]) -> str:
    batch = []
    for cafe in cafes:
        batch.append(
            {
                "cafe_id": cafe["cafe_id"],
                "name": cafe["name"],
                "location": cafe["location"],
                "food": cafe["food"],
                "ambience": cafe["ambience"],
                "speciality": cafe["speciality"],
                "hours": cafe["hours"],
                "price_hint": cafe["price"],
                "rating_hint": cafe["rating"],
            }
        )

    return f"""
You are enriching cafe metadata for a deterministic DAA-based cafe recommendation engine.
Return STRICT JSON ONLY. Do not return markdown. Do not return analysis. Do not return an essay. Do not return text before or after the JSON.
Your entire response must be a valid JSON array.

For each cafe, return one JSON object using exactly this schema:
{{
  "cafe_id": "string",
  "occasion_tags": ["hangout","date","work","meeting","quick_coffee"],
  "hangout_score": 1,
  "date_score": 1,
  "work_score": 1,
  "meeting_score": 1,
  "quick_service_score": 1,
  "privacy_score": 1,
  "aesthetic_score": 1,
  "ai_summary": "one short sentence"
}}

Scoring guidance:
- Be conservative.
- Generic or everyday cafes should usually be moderate or strong for hangout, but not automatically strong for date.
- Date score should be high only when privacy, ambience, aesthetics, or cozy vibe are clearly supported.
- Work score should reflect quietness, seating practicality, wifi/work suitability, and session comfort.
- Quick service score should reflect grab-and-go convenience or short-stop suitability.
- Meeting score should reflect moderate noise, seating, and practical conversation suitability.
- Use only integers from 1 to 10.
- Do not omit any input cafe_id.
- Do not add extra keys.
- Output JSON array only.

Cafe batch:
{json.dumps(batch, ensure_ascii=False, indent=2)}
""".strip()


def extract_text(response_json: Dict[str, object]) -> str:
    candidates = response_json.get("candidates", [])
    if not candidates:
        raise ValueError("No candidates returned by Gemini.")
    content = candidates[0].get("content", {})
    parts = content.get("parts", [])
    if not parts:
        raise ValueError("No content parts returned by Gemini.")
    text = parts[0].get("text", "")
    if not text:
        raise ValueError("Empty response text returned by Gemini.")
    return text


def normalize_payloads(payload: object) -> List[Dict[str, object]]:
    if isinstance(payload, dict):
        payload = [payload]
    if not isinstance(payload, list):
        raise ValueError("Gemini did not return a JSON array.")

    normalized = []
    for item in payload:
        if not isinstance(item, dict):
            continue
        tags = item.get("occasion_tags", [])
        if not isinstance(tags, list):
            tags = []
        cleaned_tags = []
        allowed = {"hangout", "date", "work", "meeting", "quick_coffee"}
        for tag in tags:
            value = safe(str(tag)).lower().replace(" ", "_")
            if value in allowed and value not in cleaned_tags:
                cleaned_tags.append(value)

        normalized.append(
            {
                "cafe_id": safe(str(item.get("cafe_id", ""))),
                "occasion_tags": cleaned_tags,
                "hangout_score": clamp_score(item.get("hangout_score", 6)),
                "date_score": clamp_score(item.get("date_score", 5)),
                "work_score": clamp_score(item.get("work_score", 5)),
                "meeting_score": clamp_score(item.get("meeting_score", 5)),
                "quick_service_score": clamp_score(item.get("quick_service_score", 5)),
                "privacy_score": clamp_score(item.get("privacy_score", 5)),
                "aesthetic_score": clamp_score(item.get("aesthetic_score", 5)),
                "ai_summary": safe(str(item.get("ai_summary", ""))),
            }
        )
    return normalized


def to_tsv_row(cafe_id: str, payload: Dict[str, object]) -> List[str]:
    return [
        cafe_id,
        ",".join(payload["occasion_tags"]),
        str(payload["hangout_score"]),
        str(payload["date_score"]),
        str(payload["work_score"]),
        str(payload["meeting_score"]),
        str(payload["quick_service_score"]),
        str(payload["privacy_score"]),
        str(payload["aesthetic_score"]),
        payload["ai_summary"].replace("\t", " ").replace("\n", " ").strip(),
    ]


def write_rows(path: Path, rows: List[List[str]], append: bool) -> None:
    if not rows:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    mode = "a" if append else "w"
    with path.open(mode, encoding="utf-8", newline="") as fh:
        if not append:
            fh.write(
                "cafe_id\toccasion_tags\thangout_score\tdate_score\twork_score\tmeeting_score\tquick_service_score\tprivacy_score\taesthetic_score\tai_summary\n"
            )
        for row in rows:
            fh.write("\t".join(row) + "\n")


def load_existing_ids(path: Path) -> set:
    if not path.exists():
        return set()
    seen = set()
    with path.open("r", encoding="utf-8") as fh:
        next(fh, None)
        for line in fh:
            if line.strip():
                seen.add(line.split("\t", 1)[0].strip())
    return seen


def clamp_score(value: object) -> int:
    try:
        numeric = int(value)
    except Exception:
        numeric = 5
    return max(1, min(10, numeric))


def safe(value: str) -> str:
    return (value or "").strip()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except requests.HTTPError as exc:
        print(f"Gemini request failed: {exc}", file=sys.stderr)
        raise
