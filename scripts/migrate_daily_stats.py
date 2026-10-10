#!/usr/bin/env python3
"""Remove obsolete aggregate stats from a local JSON file; preserve daily history."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import tempfile

AGGREGATE_FIELDS = ("winCount", "lossCount", "attemptsDistributionForWins", "currentStreak")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--output", type=Path, help="Write a new file (must not already exist)")
    mode.add_argument("--in-place", action="store_true", help="Back up and replace the source file")
    args = parser.parse_args()
    original = args.source.read_text(encoding="utf-8")
    data = json.loads(original)
    if not isinstance(data, dict) or not isinstance(data.get("dailyRuns", []), list):
        parser.error("Expected a JSON object with a dailyRuns array")
    removed = [key for key in AGGREGATE_FIELDS if key in data]
    for key in removed:
        del data[key]
    data.setdefault("dailyRuns", [])
    content = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    print(f"Daily runs preserved: {len(data['dailyRuns'])}; fields removed: {', '.join(removed) or 'none'}")
    if args.output:
        with args.output.open("x", encoding="utf-8") as output:
            output.write(content)
        print(f"Written: {args.output}")
    elif args.in_place:
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
        backup = args.source.with_name(f"{args.source.name}.{timestamp}.bak")
        with backup.open("xb") as output:
            output.write(args.source.read_bytes())
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=args.source.parent, delete=False) as output:
                temporary = Path(output.name)
                output.write(content)
            shutil.copymode(args.source, temporary)
            temporary.replace(args.source)
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)
        print(f"Backup: {backup}; updated: {args.source}")
    else:
        print("Preview only. Use --output NEW_FILE or --in-place to rewrite.")


if __name__ == "__main__":
    main()
