#!/usr/bin/env python3
"""Collect response-level evaluations; human review decides behavioral correctness."""
import argparse
import datetime
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

from validate_repository import ROOT, frontmatter


def load_cases():
    cases = json.loads((ROOT / "evaluations/cases.json").read_text())
    ids = set()
    for case in cases:
        assert case["id"] not in ids, "duplicate case id"
        ids.add(case["id"])
        assert case["boot"] in (3, 4)
        assert case["prompt"] and len(case["criteria"]) >= 3
        for skill in case["skills"]:
            assert (ROOT / "skills" / f"spring-boot-{case['boot']}" / skill / "SKILL.md").is_file()
    return cases


def prompt_for(case):
    catalog = []
    for path in sorted((ROOT / "skills").glob("*/*/SKILL.md")):
        _, description = frontmatter(path, path.read_text())
        catalog.append(f"{path.relative_to(ROOT)}: {description.strip()}")
    references = []
    for skill in case["skills"]:
        folder = ROOT / "skills" / f"spring-boot-{case['boot']}" / skill
        for path in [folder / "SKILL.md", *sorted((folder / "templates").glob("*.java"))]:
            references.append(f"FILE: {path.relative_to(ROOT)}\n{path.read_text()}")
    return (
        "This is a response-level skill evaluation. Do not use tools or modify files. "
        "Treat the request below as the user's task. State which versioned skill paths apply, "
        "then provide concrete implementation guidance and code where necessary. "
        "Preserve the stated project conventions. Do not grade your own response.\n\n"
        + "CATALOG\n" + "\n".join(catalog)
        + "\n\nAVAILABLE REFERENCE CONTENT\n" + "\n\n".join(references)
        + "\n\nUSER REQUEST\n" + case["prompt"]
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--agent", choices=["claude", "codex"])
    parser.add_argument("--executable", help="Optional absolute CLI path")
    parser.add_argument("--model", help="Record and select a specific model for reproducibility")
    parser.add_argument("--case", help="Case ID; omit to collect all cases")
    parser.add_argument("--output", type=Path, default=ROOT / "evaluations/results")
    args = parser.parse_args()
    cases = load_cases()
    if args.check:
        print(f"Validated {len(cases)} evaluation cases.")
        return
    if not args.agent:
        parser.error("--agent is required unless using --check")
    selected = [case for case in cases if not args.case or case["id"] == args.case]
    if not selected:
        parser.error("unknown --case")
    executable = args.executable or shutil.which(args.agent)
    if not executable:
        parser.error(f"{args.agent} CLI is not installed; install and authenticate it first")
    args.output.mkdir(parents=True, exist_ok=True)
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    dirty = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True))
    version = subprocess.run([executable, "--version"], capture_output=True, text=True, check=True).stdout.strip()
    for case in selected:
        prompt = prompt_for(case)
        stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
        prefix = args.output / f"{stamp}-{args.agent}-{case['id']}"
        with tempfile.TemporaryDirectory(prefix="spring-skill-eval-") as directory:
            if args.agent == "codex":
                response = Path(directory) / "response.txt"
                command = [executable, "exec", "--sandbox", "read-only", "--ephemeral",
                           "--skip-git-repo-check", "--output-last-message", str(response), "-"]
            else:
                command = [executable, "-p", "--tools", "", "--output-format", "text"]
            if args.model:
                command += ["--model", args.model]
            try:
                result = subprocess.run(command, input=prompt, cwd=directory, text=True,
                                        capture_output=True, timeout=300)
                answer = response.read_text() if args.agent == "codex" and response.exists() else result.stdout
                record = {"exit_code": result.returncode, "response": answer, "stderr": result.stderr}
            except subprocess.TimeoutExpired as error:
                record = {"exit_code": None, "response": "", "stderr": "Timed out after 300 seconds"}
        record.update({"case": case["id"], "agent": args.agent, "model": args.model or "CLI default",
                       "cli_version": version, "revision": revision, "dirty_worktree": dirty,
                       "prompt": prompt, "review_status": "unreviewed", "criteria": case["criteria"]})
        prefix.with_suffix(".json").write_text(json.dumps(record, indent=2) + "\n")
        print(f"{case['id']}: saved {prefix.with_suffix('.json')}", flush=True)
        if record["exit_code"] != 0:
            raise SystemExit(f"{args.agent} failed; inspect the saved transcript")


if __name__ == "__main__":
    main()
