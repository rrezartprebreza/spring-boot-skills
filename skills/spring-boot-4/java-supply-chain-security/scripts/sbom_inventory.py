#!/usr/bin/env python3
"""Summarize local CycloneDX JSON metadata; never a vulnerability assessment."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import stat

MAX_BYTES = 10 * 1024 * 1024


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


def inventory(document):
    if not isinstance(document, dict) or document.get("bomFormat") != "CycloneDX":
        raise ValueError("Expected a CycloneDX JSON object")
    if not isinstance(document.get("specVersion"), str):
        raise ValueError("Missing CycloneDX specVersion")
    components = []

    def collect(items, depth=0):
        if depth > 20 or not isinstance(items, list):
            raise ValueError("Invalid or excessively nested components")
        for item in items:
            if not isinstance(item, dict) or not isinstance(item.get("name"), str) or not item["name"].strip():
                raise ValueError("Each component needs a name")
            row = {}
            for key in ("type", "group", "name", "version", "purl"):
                value = item.get(key)
                if value is not None and not isinstance(value, str):
                    raise ValueError("Component metadata must be text")
                if value is not None:
                    row[key] = value
            row["missing_identity_fields"] = [
                key for key in ("version", "purl") if not row.get(key, "").strip()
            ]
            components.append(row)
            collect(item.get("components", []), depth + 1)

    collect(document.get("components", []))
    return {
        "spec_version": document["specVersion"],
        "component_count": len(components),
        "components": components,
        "assessment": "not-performed",
        "limitations": [
            "Inventory only; no vulnerability, exploitability, license or schema assessment.",
            "Only the components tree is listed; metadata root, services and dependency edges are not evaluated.",
            "Missing or empty components do not establish that an artifact is safe or complete.",
        ],
    }


def inspect_file(path):
    path = Path(path)
    if path.is_symlink():
        raise ValueError("Symbolic links are not accepted")
    fd = os.open(path, os.O_RDONLY | getattr(os, "O_NONBLOCK", 0) | getattr(os, "O_NOFOLLOW", 0))
    with os.fdopen(fd, "rb") as stream:
        if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
            raise ValueError("Only regular files are accepted")
        raw = stream.read(MAX_BYTES + 1)
    if len(raw) > MAX_BYTES:
        raise ValueError("SBOM exceeds 10 MiB input limit")
    document = json.loads(raw, object_pairs_hook=unique_object)
    result = inventory(document)
    result["sha256"] = hashlib.sha256(raw).hexdigest()
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sbom", type=Path)
    args = parser.parse_args()
    try:
        result = inspect_file(args.sbom)
    except (OSError, ValueError, RecursionError) as error:
        parser.exit(2, f"Cannot inspect SBOM: {type(error).__name__}; check format and local file access.\n")
    print(json.dumps(result, indent=2, ensure_ascii=True))


if __name__ == "__main__":
    main()
