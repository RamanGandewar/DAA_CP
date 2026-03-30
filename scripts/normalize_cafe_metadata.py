import argparse
import csv
import json
import re
from pathlib import Path
from typing import Dict, List, Set


ALLOWED_TAGS = ["hangout", "date", "work", "meeting", "quick_coffee"]
PLACEHOLDER_PATTERNS = (
    r"^cafe\s+(node|way)/",
    r"^test\s+cafe$",
    r"^\?+$",
    r"^\?[\?\s]+\??$",
    r"^sop\s+\d+",
)
CHAIN_NAMES = (
    "starbucks",
    "cafe coffee day",
    "caf coffee day",
    "café coffee day",
    "barista",
    "tim hortons",
    "third wave",
    "third wave coffee",
    "blue tokai",
    "chai point",
)
QUICK_STOP_HINTS = (
    "chai",
    "tea",
    "snacks",
    "vada pav",
    "amruttulya",
    "durga",
    "irani",
    "rolls mania",
    "coffee corner",
    "tea stall",
    "kiosk",
)
WORK_HINTS = (
    "workcafe",
    "work cafe",
    "book",
    "study",
    "read",
    "espresso",
)
DATE_HINTS = (
    "lounge",
    "rooftop",
    "artisanal",
    "patisserie",
    "bistro",
    "commune",
    "cartoon",
)


def main() -> int:
    parser = argparse.ArgumentParser(description="Normalize noisy Gemini cafe metadata into safer recommendation scores.")
    parser.add_argument("--input-json", required=True, help="Path to Gemini JSON metadata draft.")
    parser.add_argument("--cafes-csv", default="data/cafes.csv", help="Path to source cafes CSV.")
    parser.add_argument("--output-json", default="data/cafe_metadata.cleaned.json", help="Path for cleaned JSON output.")
    parser.add_argument("--output-tsv", default="data/cafes.enrichment.tsv", help="Path for Java-ready TSV output.")
    args = parser.parse_args()

    cafes = load_cafes(Path(args.cafes_csv))
    cleaned = normalize_metadata(Path(args.input_json), cafes)
    write_json(Path(args.output_json), cleaned)
    write_tsv(Path(args.output_tsv), cleaned)

    print(f"Cleaned {len(cleaned)} cafes")
    print(f"JSON: {Path(args.output_json)}")
    print(f"TSV:  {Path(args.output_tsv)}")
    return 0


def load_cafes(path: Path) -> Dict[str, Dict[str, str]]:
    for encoding in ("utf-8", "windows-1252"):
        try:
            with path.open("r", encoding=encoding, newline="") as fh:
                reader = csv.DictReader(fh)
                return {
                    safe(row.get("id")): {
                        "name": safe(row.get("name")),
                        "location": safe(row.get("address")),
                        "food": safe(row.get("cuisines")),
                        "hours": safe(row.get("operatingHours")),
                        "rating": safe(row.get("rating")),
                        "price": safe(row.get("avgPrice")),
                    }
                    for row in reader
                }
        except UnicodeDecodeError:
            continue
    raise RuntimeError(f"Unable to read cafes CSV: {path}")


def normalize_metadata(path: Path, cafes: Dict[str, Dict[str, str]]) -> List[Dict[str, object]]:
    with path.open("r", encoding="utf-8") as fh:
        payload = json.load(fh)

    cleaned = []
    for item in payload:
        cafe_id = safe(item.get("cafe_id"))
        source = cafes.get(cafe_id, {})
        cleaned.append(normalize_item(item, source))
    return cleaned


def normalize_item(item: Dict[str, object], source: Dict[str, str]) -> Dict[str, object]:
    out = dict(item)
    name = safe(source.get("name")) or derive_name_hint(item)
    location = safe(source.get("location"))
    foods = parse_foods(source.get("food", ""))
    lowered_name = name.lower()

    placeholder = is_placeholder_name(lowered_name)
    generic = is_generic_sparse(location, foods)
    chain_like = contains_any(lowered_name, CHAIN_NAMES)
    quick_stop = contains_any(lowered_name, QUICK_STOP_HINTS) or foods & {"tea", "fast_food", "sandwich", "regional"}
    work_hint = contains_any(lowered_name, WORK_HINTS)
    date_hint = contains_any(lowered_name, DATE_HINTS)

    clamp(out, "hangout_score", 4, 7)
    clamp(out, "date_score", 2, 8)
    clamp(out, "work_score", 2, 7)
    clamp(out, "meeting_score", 3, 7)
    clamp(out, "quick_service_score", 3, 8)
    clamp(out, "privacy_score", 2, 7)
    clamp(out, "aesthetic_score", 2, 8)
    clamp(out, "group_suitability_score", 2, 7)
    clamp(out, "solo_suitability_score", 2, 7)
    clamp(out, "conversation_score", 2, 7)

    if placeholder:
        force_generic_caps(out, max_quick=5, summary_suffix="Sparse placeholder entry; best treated as a basic cafe.")
    elif generic:
        force_generic_caps(out, max_quick=6, summary_suffix="Limited source detail suggests a generic everyday cafe.")

    if chain_like:
        clamp(out, "date_score", 2, 4)
        clamp(out, "privacy_score", 2, 4)
        clamp(out, "aesthetic_score", 3, 5)
        clamp(out, "meeting_score", 3, 5)
        if work_hint or "third wave" in lowered_name or "blue tokai" in lowered_name or "starbucks" in lowered_name:
            clamp(out, "work_score", 4, 6)
        else:
            clamp(out, "work_score", 3, 5)

    if quick_stop:
        clamp(out, "date_score", 2, 3)
        clamp(out, "privacy_score", 2, 3)
        clamp(out, "work_score", 2, 4)
        clamp(out, "meeting_score", 3, 4)
        out["quick_service_score"] = max(score(out, "quick_service_score"), 6)

    if work_hint and not placeholder:
        out["work_score"] = max(score(out, "work_score"), 6)
        clamp(out, "meeting_score", 4, 6)

    if date_hint and not placeholder and not quick_stop:
        clamp(out, "aesthetic_score", 5, 7)
        clamp(out, "date_score", 4, 6)

    if not location or location.upper() == "N/A":
        clamp(out, "date_score", 2, 4)
        clamp(out, "work_score", 2, 5)
        clamp(out, "meeting_score", 3, 5)
        clamp(out, "privacy_score", 2, 4)
        clamp(out, "aesthetic_score", 2, 5)

    out["occasion_tags"] = compute_occasion_tags(out)
    out["likely_best_for"] = pick_best(out)
    out["likely_worst_for"] = pick_worst(out)
    out["primary_identity"] = choose_identity(out, placeholder, generic, chain_like, quick_stop, work_hint, date_hint)
    out["recommendation_note"] = recommendation_note(out, placeholder, generic, quick_stop, work_hint)
    out["avoid_note"] = avoid_note(out, placeholder, chain_like, quick_stop)

    return out


def force_generic_caps(out: Dict[str, object], max_quick: int, summary_suffix: str) -> None:
    out["primary_identity"] = "generic"
    clamp(out, "hangout_score", 4, 5)
    clamp(out, "date_score", 2, 3)
    clamp(out, "work_score", 2, 4)
    clamp(out, "meeting_score", 3, 4)
    clamp(out, "quick_service_score", 4, max_quick)
    clamp(out, "privacy_score", 2, 3)
    clamp(out, "aesthetic_score", 3, 4)
    clamp(out, "group_suitability_score", 3, 5)
    clamp(out, "solo_suitability_score", 3, 5)
    clamp(out, "conversation_score", 3, 5)
    desc = safe(out.get("derived_description"))
    if summary_suffix not in desc:
        out["derived_description"] = f"{desc} {summary_suffix}".strip()


def compute_occasion_tags(out: Dict[str, object]) -> List[str]:
    scores = {
        "hangout": score(out, "hangout_score"),
        "date": score(out, "date_score"),
        "work": score(out, "work_score"),
        "meeting": score(out, "meeting_score"),
        "quick_coffee": score(out, "quick_service_score"),
    }
    tags = [tag for tag, value in scores.items() if value >= 6]
    if not tags:
        best = max(scores, key=scores.get)
        tags = [best] if scores[best] >= 5 else ["hangout"]
    return tags


def pick_best(out: Dict[str, object]) -> str:
    scores = {
        "hangout": score(out, "hangout_score"),
        "date": score(out, "date_score"),
        "work": score(out, "work_score"),
        "meeting": score(out, "meeting_score"),
        "quick_coffee": score(out, "quick_service_score"),
    }
    ordered = sorted(scores.items(), key=lambda item: item[1], reverse=True)
    if len(ordered) > 1 and ordered[0][1] - ordered[1][1] <= 1 and ordered[0][1] >= 6 and ordered[1][1] >= 6:
        return "mixed"
    return ordered[0][0]


def pick_worst(out: Dict[str, object]) -> str:
    scores = {
        "hangout": score(out, "hangout_score"),
        "date": score(out, "date_score"),
        "work": score(out, "work_score"),
        "meeting": score(out, "meeting_score"),
        "quick_coffee": score(out, "quick_service_score"),
    }
    ordered = sorted(scores.items(), key=lambda item: item[1])
    if ordered[-1][1] - ordered[0][1] <= 1:
        return "none"
    return ordered[0][0]


def choose_identity(
    out: Dict[str, object],
    placeholder: bool,
    generic: bool,
    chain_like: bool,
    quick_stop: bool,
    work_hint: bool,
    date_hint: bool,
) -> str:
    if placeholder or generic:
        return "generic"
    if quick_stop and score(out, "quick_service_score") >= 6:
        return "quick_stop"
    if work_hint and score(out, "work_score") >= 6:
        return "work_friendly"
    if date_hint and score(out, "date_score") >= 5 and score(out, "aesthetic_score") >= 5:
        return "aesthetic"
    if chain_like and score(out, "work_score") >= 6:
        return "work_friendly"
    strong = sum(
        1
        for value in (
            score(out, "hangout_score"),
            score(out, "date_score"),
            score(out, "work_score"),
            score(out, "meeting_score"),
            score(out, "quick_service_score"),
        )
        if value >= 6
    )
    if strong >= 3:
        return "mixed"
    if score(out, "hangout_score") >= 6:
        return "social"
    return "generic"


def recommendation_note(out: Dict[str, object], placeholder: bool, generic: bool, quick_stop: bool, work_hint: bool) -> str:
    best = out["likely_best_for"]
    if placeholder:
        return "Rank higher only when the user just needs a basic nearby cafe option."
    if quick_stop:
        return "Rank higher for short snack, tea, or grab-and-go coffee stops."
    if work_hint or best == "work":
        return "Rank higher for solo work sessions or focused daytime visits."
    if generic:
        return "Rank higher for casual everyday hangouts without specialized expectations."
    if best == "mixed":
        return "Rank higher when the user is flexible and wants a generally useful cafe."
    return f"Rank higher when the user is specifically looking for {best.replace('_', ' ')}."


def avoid_note(out: Dict[str, object], placeholder: bool, chain_like: bool, quick_stop: bool) -> str:
    worst = out["likely_worst_for"]
    if placeholder:
        return "Rank lower when the user wants a distinctive ambience or trustworthy detail."
    if quick_stop:
        return "Rank lower for long stays, privacy, or a special occasion feel."
    if chain_like and worst in {"date", "meeting"}:
        return "Rank lower when the user wants privacy or a more distinctive setting."
    if worst == "none":
        return "Rank lower only when the user wants a very specialized cafe experience."
    return f"Rank lower when the user is prioritizing {worst.replace('_', ' ')}."


def write_json(path: Path, data: List[Dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=2)


def write_tsv(path: Path, data: List[Dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as fh:
        fh.write(
            "cafe_id\toccasion_tags\thangout_score\tdate_score\twork_score\tmeeting_score\tquick_service_score\tprivacy_score\taesthetic_score\tai_summary\n"
        )
        for item in data:
            row = [
                safe(item.get("cafe_id")),
                ",".join(item.get("occasion_tags", [])),
                str(score(item, "hangout_score")),
                str(score(item, "date_score")),
                str(score(item, "work_score")),
                str(score(item, "meeting_score")),
                str(score(item, "quick_service_score")),
                str(score(item, "privacy_score")),
                str(score(item, "aesthetic_score")),
                safe(item.get("derived_description") or item.get("recommendation_note")),
            ]
            fh.write("\t".join(value.replace("\t", " ").replace("\n", " ").strip() for value in row) + "\n")


def parse_foods(raw: str) -> Set[str]:
    return {part.strip().lower() for part in raw.split("|") if part.strip()}


def is_placeholder_name(name: str) -> bool:
    if not name:
        return True
    return any(re.search(pattern, name) for pattern in PLACEHOLDER_PATTERNS)


def is_generic_sparse(location: str, foods: Set[str]) -> bool:
    generic_foods = {"coffee_shop", "coffee", "cafe"}
    if not location or location.upper() == "N/A":
        return not foods or foods.issubset(generic_foods)
    return foods.issubset(generic_foods) and len(foods) <= 1


def contains_any(text: str, needles) -> bool:
    return any(needle in text for needle in needles)


def derive_name_hint(item: Dict[str, object]) -> str:
    desc = safe(item.get("derived_description"))
    if ";" in desc:
        return desc.split(";", 1)[0].strip()
    return ""


def clamp(out: Dict[str, object], key: str, low: int, high: int) -> None:
    out[key] = max(low, min(high, score(out, key)))


def score(out: Dict[str, object], key: str) -> int:
    try:
        return max(1, min(10, int(out.get(key, 5))))
    except Exception:
        return 5


def safe(value) -> str:
    return str(value or "").strip()


if __name__ == "__main__":
    raise SystemExit(main())
