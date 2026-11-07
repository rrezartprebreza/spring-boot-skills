#!/usr/bin/env python3
"""Fingerprint explicitly named local files without copying or uploading contents."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import stat


def fingerprint(path):
    path = Path(path)
    if path.is_symlink():
        raise ValueError("Symbolic links are not accepted")
    flags = os.O_RDONLY | getattr(os, "O_NONBLOCK", 0) | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(path, flags)
    with os.fdopen(fd, "rb") as stream:
        before = os.fstat(stream.fileno())
        if not stat.S_ISREG(before.st_mode):
            raise ValueError("Only regular files are accepted")
        digest = hashlib.sha256()
        size = 0
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
            size += len(chunk)
        after = os.fstat(stream.fileno())
    if (before.st_size, before.st_mtime_ns, before.st_ctime_ns) != (
            after.st_size, after.st_mtime_ns, after.st_ctime_ns) or size != before.st_size:
        raise ValueError("File changed while reading; use an approved snapshot")
    return {"bytes": size, "sha256": digest.hexdigest()}


def manifest(paths):
    return {
        "inventory_time_utc": datetime.now(timezone.utc).isoformat(),
        "artifacts": [
            {"id": f"artifact-{index}", **fingerprint(path)}
            for index, path in enumerate(paths, start=1)
        ],
        "limitations": [
            "Fingerprints are not proof of authenticity, collection time or chain of custody.",
            "No content redaction or secret scanning was performed.",
            "Keep the ordered source-path mapping in the restricted case record.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", type=Path, nargs="+")
    args = parser.parse_args()
    try:
        result = manifest(args.files)
    except (OSError, ValueError) as error:
        parser.exit(2, f"Cannot fingerprint evidence: {type(error).__name__}; check regular-file snapshots and access.\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
