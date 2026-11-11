#!/usr/bin/env python3
"""Generate, check, or safely install native Claude and Codex specialists."""

from __future__ import annotations

import argparse
import json
import re
import tomllib
from pathlib import Path

import yaml

from validate_repository import load_json, parse_yaml

ROOT = Path(__file__).resolve().parents[1]
ROLES = {"architect", "implementer", "reviewer", "security-reviewer", "test-engineer", "migration-engineer"}
READ_ROLES = {"architect", "reviewer", "security-reviewer"}
CLIENTS = {"claude": (".claude", ".md"), "codex": (".codex", ".toml")}


def load_roles(root: Path = ROOT) -> dict:
    """Return the six role definitions after validating their schema and skill routes.

    Enforce each role's read/edit classification and require every referenced
    skill in both Boot trees. Semantic violations raise ValueError; malformed
    JSON is reported by the repository loader through SystemExit.
    """
    roles = load_json(root / "specialists" / "roles.json")
    if set(roles) != ROLES:
        raise ValueError("the specialist catalog must contain exactly the six documented roles")
    for name, role in roles.items():
        if not isinstance(role, dict) or set(role) != {"description", "access", "skills", "instructions"}:
            raise ValueError(f"invalid role schema: {name}")
        if role["access"] != ("read" if name in READ_ROLES else "edit"):
            raise ValueError(f"invalid access for {name}")
        if not isinstance(role["description"], str) or not role["description"].startswith("Use when "):
            raise ValueError(f"missing routing description: {name}")
        for field in ("skills", "instructions"):
            values = role[field]
            if not isinstance(values, list) or not values or any(not isinstance(v, str) or not v.strip() for v in values):
                raise ValueError(f"invalid {field}: {name}")
        if len(set(role["skills"])) != len(role["skills"]):
            raise ValueError(f"duplicate skill routing: {name}")
        for skill in role["skills"]:
            if not re.fullmatch(r"[a-z0-9-]+", skill):
                raise ValueError(f"invalid skill name: {skill}")
            for boot in (3, 4):
                if not (root / "skills" / f"spring-boot-{boot}" / skill / "SKILL.md").is_file():
                    raise ValueError(f"{name}: missing Boot {boot} skill {skill}")
    return roles


def render(name: str, role: dict, common: str, client: str) -> str:
    """Render a validated role as Claude Markdown or Codex TOML without writing.

    The caller supplies a client from CLIENTS and shared instructions. Add
    client-specific skill discovery and read-role boundaries while preserving
    the role workflow; native string escaping keeps instructions intact.
    """
    instructions = common.strip() + "\n\n## Skill discovery\n\n"
    if client == "claude":
        instructions += (
            "Use Skill to load the matching installed plugin skill, for example "
            "spring-boot-4-skills:rest-api-conventions or its Boot 3 counterpart. "
            "For project-installed skills, read .claude/skills/<skill>/SKILL.md and confirm "
            "its Boot version. Do not assume an unqualified skill name identifies a version.\n"
        )
    else:
        instructions += (
            "Use the available skill catalog and its supplied paths to open the matching "
            "Boot variant. For project-installed skills, inspect .agents/skills/ or "
            ".codex/skills/ within the approved project. Do not recursively search the "
            "user's home directory. Installing this agent does not install its skills.\n"
        )
    instructions += "\nRelevant skill names: " + ", ".join(f"`{s}`" for s in role["skills"]) + ".\n"
    if role["access"] == "read":
        instructions += "\n## Read-only role\n\nDo not edit files or run tests, builds, project scripts, or state-changing commands. "
        if client == "codex":
            instructions += (
                "Use read-only inspection commands such as git diff, git status, rg, and sed "
                "when file-reading tools are unavailable. Shell access is not permission to mutate files. "
            )
        else:
            instructions += "Ask the parent for diffs or effective dependency reports that require shell execution. "
        instructions += (
            "Ask the parent or test engineer for execution evidence. These instructions do not "
            "create a separate sandbox; the parent must enforce the appropriate permissions.\n"
        )
    instructions += "\n## Role workflow\n\n" + "\n".join(f"- {step}" for step in role["instructions"]) + "\n"
    agent_name = f"spring-{name}"
    if client == "claude":
        tools = ["Read", "Glob", "Grep", "Skill"]
        if role["access"] == "edit":
            tools += ["Edit", "Write", "Bash"]
        metadata = {"name": agent_name, "description": role["description"], "tools": ", ".join(tools), "model": "inherit"}
        return "---\n" + yaml.safe_dump(metadata, sort_keys=False) + "---\n\n" + instructions
    # JSON escaping also works inside TOML basic strings.
    result = "# Generated by scripts/specialist_agents.py; edit specialists/ sources.\n"
    for key, value in (("name", agent_name), ("description", role["description"])):
        result += f"{key} = {json.dumps(value, ensure_ascii=False)}\n"
    escaped_lines = "\n".join(json.dumps(line, ensure_ascii=False)[1:-1] for line in instructions.splitlines())
    result += f'developer_instructions = """\n{escaped_lines}\n"""\n'
    return result


def artifacts(root: Path = ROOT) -> dict[Path, str]:
    """Build repository-relative paths and contents for all twelve agent files.

    Read and validate the canonical roles and shared instructions, raising
    ValueError for empty shared content. No generated files are written here.
    """
    roles = load_roles(root)
    common = (root / "specialists" / "shared.md").read_text(encoding="utf-8")
    if not common.strip():
        raise ValueError("shared instructions must not be empty")
    return {
        Path("specialists") / client / f"spring-{name}{suffix}": render(name, role, common, client)
        for client, (_, suffix) in CLIENTS.items()
        for name, role in sorted(roles.items())
    }


def check(root: Path = ROOT) -> None:
    """Validate generated files, native syntax, and Claude marketplace exposure.

    Require exactly the expected files and byte-for-byte agreement with the
    canonical sources. Raise ValueError on drift or packaging mismatches;
    filesystem and parser failures propagate without modifying the repository.
    """
    expected = artifacts(root)
    for client in CLIENTS:
        actual = {p.relative_to(root) for p in (root / "specialists" / client).iterdir()}
        if actual != {p for p in expected if p.parts[1] == client}:
            raise ValueError(f"unexpected or missing {client} agent files")
    for relative, content in expected.items():
        path = root / relative
        if path.read_text(encoding="utf-8") != content:
            raise ValueError(f"stale generated agent: {relative}; run generate")
        if path.suffix == ".toml":
            tomllib.loads(content)
        else:
            parse_yaml(content.split("---\n", 2)[1], path)
    marketplace = load_json(root / ".claude-plugin" / "marketplace.json")
    paths = sorted("./" + p.as_posix() for p in expected if p.parts[1] == "claude")
    for plugin in marketplace["plugins"]:
        if plugin.get("agents") != paths:
            raise ValueError(f"Claude plugin {plugin['name']} does not expose the six specialists")


def install(project: Path, clients: list[str], dry_run: bool, root: Path = ROOT) -> list[Path]:
    """Install missing definitions for selected CLIENTS and return their paths.

    Preflight the existing project and all destinations before writing. Reject
    symlink destinations, invalid parent directories, and differing files with
    ValueError; identical definitions are left untouched. A dry run returns
    planned paths without creating files or directories. On a write OSError,
    remove files created by this call and re-raise; created directories may
    remain. This is not an atomic transaction against concurrent filesystem edits.
    """
    if not project.is_dir():
        raise ValueError(f"project directory does not exist: {project}")
    project = project.resolve()
    expected = artifacts(root)
    pending = []
    # Preflight the entire set before writing anything; never follow destination symlinks.
    for relative, content in expected.items():
        client = relative.parts[1]
        if client not in clients:
            continue
        target = project / CLIENTS[client][0] / "agents" / relative.name
        if any(p.is_symlink() for p in (target.parent.parent, target.parent, target)):
            raise ValueError(f"refusing symlink destination: {target}")
        for parent in (target.parent.parent, target.parent):
            if parent.exists() and not parent.is_dir():
                raise ValueError(f"not a directory: {parent}")
        if target.exists():
            if not target.is_file() or target.read_text(encoding="utf-8") != content:
                raise ValueError(f"existing agent differs; preserve and reconcile manually: {target}")
        else:
            pending.append((target, content))
    if not dry_run:
        created = []
        try:
            for path, content in pending:
                path.parent.mkdir(parents=True, exist_ok=True)
                with path.open("x", encoding="utf-8") as stream:
                    created.append(path)
                    stream.write(content)
        except OSError:
            for path in created:
                path.unlink()
            raise
    return [path for path, _ in pending]


def main() -> None:
    """Dispatch generate, check, or install from command-line arguments.

    Generate updates checked-in definitions; install first checks their source
    consistency and expands the 'both' client selection. Report filesystem and
    validation errors as CLI diagnostics with exit status 1.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("generate")
    sub.add_parser("check")
    setup = sub.add_parser("install")
    setup.add_argument("--project", type=Path, required=True)
    setup.add_argument("--client", choices=[*CLIENTS, "both"], required=True)
    setup.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    try:
        if args.command == "generate":
            for relative, content in artifacts().items():
                path = ROOT / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")
            print("Generated six Claude and six Codex specialists.")
        elif args.command == "check":
            check()
            print("Validated specialist definitions, skill routes, and Claude packaging.")
        else:
            check()
            clients = list(CLIENTS) if args.client == "both" else [args.client]
            paths = install(args.project, clients, args.dry_run)
            for path in paths:
                print(f"{'Would install' if args.dry_run else 'Installed'} {path}")
            if not paths:
                print("All selected agents are already installed unchanged.")
    except (OSError, ValueError) as error:
        parser.exit(1, f"ERROR: {error}\n")


if __name__ == "__main__":
    main()
