#!/usr/bin/env python3
"""Compare JSON bodies semantically without changing either output tree."""

import argparse
import hashlib
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("candidate", type=Path)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("names", nargs="+")
    args = parser.parse_args()

    failures = []
    for name in args.names:
        candidate = (args.candidate / name).read_bytes()
        baseline = (args.baseline / name).read_bytes()
        matches = json.loads(candidate) == json.loads(baseline)
        digest = hashlib.sha256(candidate).hexdigest()
        print(f"{'PASS' if matches else 'FAIL'} {name} candidate-sha256 {digest}")
        if not matches:
            failures.append(name)
    print(f"JSON body comparisons: {len(args.names) - len(failures)}/{len(args.names)} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
