"""Validate actual application records, including duplicate keys and sentinel leaks."""
import argparse
import datetime as dt
import json
from pathlib import Path
from jsonschema import Draft202012Validator
def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result
def validate(path, schema):
    count = 0
    validator = Draft202012Validator(schema)
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        if "SENTINEL-" in line:
            raise ValueError(f"{path}:{number}: sentinel secret leaked")
        record = json.loads(line, object_pairs_hook=unique)
        validator.validate(record)
        dt.datetime.fromisoformat(record["timestamp"].replace("Z", "+00:00"))
        count += 1
    if count == 0:
        raise ValueError(f"{path}: no application records")
    return count
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("samples", type=Path, nargs="+")
    args = parser.parse_args()
    schema = json.loads(Path(__file__).with_name("log.schema.json").read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    for path in args.samples:
        print(f"PASS {path}: {validate(path, schema)} records")
if __name__ == "__main__":
    main()
