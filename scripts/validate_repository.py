#!/usr/bin/env python3
"""Validate skill structure, agent metadata, links, and marketplace packaging."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BOOT_ROOTS = {version: ROOT / "skills" / f"spring-boot-{version}" for version in (3, 4)}
PLACEHOLDER = re.compile(r"\b(?:TODO|FIXME|TBD)\b|\[TODO:", re.IGNORECASE)
LINK = re.compile(r"\[[^\]]*\]\(([^)]+)\)")


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"invalid JSON in {path.relative_to(ROOT)}: {error}")


def frontmatter(path: Path, text: str) -> tuple[str, str]:
    lines = text.splitlines()
    if not lines or lines[0] != "---":
        fail(f"{path.relative_to(ROOT)} must start with YAML frontmatter")
    try:
        end = lines.index("---", 1)
    except ValueError:
        fail(f"{path.relative_to(ROOT)} has unterminated frontmatter")

    block = lines[1:end]
    keys = [match.group(1) for line in block if (match := re.match(r"^([a-z_]+):", line))]
    if keys != ["name", "description"]:
        fail(f"{path.relative_to(ROOT)} frontmatter keys must be name and description only")

    name_line = next(line for line in block if line.startswith("name:"))
    name = name_line.split(":", 1)[1].strip().strip('"\'')
    description_index = next(i for i, line in enumerate(block) if line.startswith("description:"))
    description = " ".join(line.strip() for line in block[description_index + 1 :])
    if "Use when" not in description:
        fail(f"{path.relative_to(ROOT)} description must include a 'Use when' trigger")
    return name, description


def validate_skill(version: int, skill_dir: Path, catalog_version: int) -> None:
    skill_name = skill_dir.name
    skill_file = skill_dir / "SKILL.md"
    if not skill_file.is_file():
        fail(f"missing {skill_file.relative_to(ROOT)}")

    text = skill_file.read_text(encoding="utf-8")
    name, _ = frontmatter(skill_file, text)
    if name != skill_name:
        fail(f"{skill_file.relative_to(ROOT)} name '{name}' does not match its folder")
    if len(text.splitlines()) > 500:
        fail(f"{skill_file.relative_to(ROOT)} exceeds the 500-line skill limit")
    if "## Gotchas" not in text:
        fail(f"{skill_file.relative_to(ROOT)} is missing a Gotchas section")
    if len(re.findall(r"^- Agent ", text, re.MULTILINE)) < 3:
        fail(f"{skill_file.relative_to(ROOT)} needs at least three agent gotchas")
    if PLACEHOLDER.search(text):
        fail(f"{skill_file.relative_to(ROOT)} contains a placeholder")

    metadata = skill_dir / "agents" / "openai.yaml"
    if not metadata.is_file():
        fail(f"missing {metadata.relative_to(ROOT)}")
    metadata_text = metadata.read_text(encoding="utf-8")
    for field in ("display_name:", "short_description:", "default_prompt:"):
        if field not in metadata_text:
            fail(f"{metadata.relative_to(ROOT)} is missing {field[:-1]}")
    if f"${skill_name}" not in metadata_text:
        fail(f"{metadata.relative_to(ROOT)} default prompt must mention ${skill_name}")
    short_match = re.search(r'^\s*short_description:\s*"([^"]+)"', metadata_text, re.MULTILINE)
    if not short_match or not 25 <= len(short_match.group(1)) <= 64:
        fail(f"{metadata.relative_to(ROOT)} short description must contain 25-64 characters")
    if PLACEHOLDER.search(metadata_text):
        fail(f"{metadata.relative_to(ROOT)} contains a placeholder")

    examples = skill_dir / "examples"
    if not examples.is_dir():
        fail(f"missing {examples.relative_to(ROOT)}")
    example_names = [path.name.lower() for path in examples.iterdir() if path.is_file()]
    if not any(name.startswith("good") for name in example_names):
        fail(f"{examples.relative_to(ROOT)} needs a good example")
    if not any(name.startswith("bad") for name in example_names):
        fail(f"{examples.relative_to(ROOT)} needs a bad example")

    readme_path = f"skills/spring-boot-{catalog_version}/{skill_name}/"
    if readme_path not in (ROOT / "README.md").read_text(encoding="utf-8"):
        fail(f"README.md is missing the catalog entry for {skill_name}")


def validate_markdown_links() -> None:
    markdown_files = [ROOT / "README.md", ROOT / "CONTRIBUTING.md"]
    markdown_files.extend(ROOT.glob("skills/**/SKILL.md"))
    for path in markdown_files:
        text = path.read_text(encoding="utf-8")
        for raw_target in LINK.findall(text):
            target = raw_target.strip().strip("<>").split("#", 1)[0]
            if not target or re.match(r"^(?:https?://|mailto:|codex:)", target):
                continue
            target = target.split(" ", 1)[0]
            resolved = (path.parent / target).resolve()
            if not resolved.exists():
                fail(f"broken local link in {path.relative_to(ROOT)}: {raw_target}")


def validate_packaging(skill_counts: dict[int, int]) -> None:
    claude = load_json(ROOT / ".claude-plugin" / "marketplace.json")
    codex = load_json(ROOT / ".agents" / "plugins" / "marketplace.json")
    expected_plugins = {"spring-boot-3-skills", "spring-boot-4-skills"}
    if {plugin.get("name") for plugin in claude.get("plugins", [])} != expected_plugins:
        fail("Claude marketplace must contain the Boot 3 and Boot 4 plugins")
    if {plugin.get("name") for plugin in codex.get("plugins", [])} != expected_plugins:
        fail("Codex marketplace must contain the Boot 3 and Boot 4 plugins")

    for version in (3, 4):
        plugin_dir = ROOT / "plugins" / f"spring-boot-{version}-skills"
        manifest = load_json(plugin_dir / ".codex-plugin" / "plugin.json")
        if manifest.get("name") != f"spring-boot-{version}-skills":
            fail(f"Boot {version} Codex plugin name is invalid")
        if manifest.get("skills") != "./skills/":
            fail(f"Boot {version} Codex plugin must expose ./skills/")
        linked_skills = plugin_dir / "skills"
        if not linked_skills.is_dir():
            fail(f"Boot {version} Codex plugin skills link is broken")
        if len([path for path in linked_skills.iterdir() if path.is_dir()]) != skill_counts[version]:
            fail(f"Boot {version} Codex plugin does not expose all {skill_counts[version]} skills")

    for version in (3, 4):
        for agent_file in ("AGENTS.md", "CLAUDE.md"):
            path = ROOT / "templates" / f"spring-boot-{version}" / agent_file
            if not path.is_file():
                fail(f"missing {path.relative_to(ROOT)}")


def validate_version_sensitive_examples() -> None:
    mcp_dir = BOOT_ROOTS[4] / "mcp-server"
    mcp_skill = (mcp_dir / "SKILL.md").read_text(encoding="utf-8")
    mcp_example = (mcp_dir / "examples" / "OrderMcpTools.java").read_text(encoding="utf-8")
    if "MCP Java SDK 2" not in mcp_skill or "<version>2.0.0</version>" not in mcp_skill:
        fail("Boot 4 MCP skill must document the verified standalone SDK 2.0 baseline")
    if "org.springframework.ai.tool.annotation.Tool" in mcp_example or "@Tool(" in mcp_example:
        fail("Boot 4 MCP example must use native @McpTool registration")
    if "com.fasterxml.jackson.databind" in mcp_example:
        fail("Boot 4 MCP example must not depend on Jackson 2 databind")
    if "@McpTool(" not in mcp_example or "@McpToolParam(" not in mcp_example:
        fail("Boot 4 MCP example is missing native MCP annotations")

    gateway_dir = BOOT_ROOTS[4] / "spring-cloud-gateway"
    gateway_skill = (gateway_dir / "SKILL.md").read_text(encoding="utf-8")
    webflux_routes = (gateway_dir / "examples" / "good-routes.yml").read_text(encoding="utf-8")
    webmvc_routes = (gateway_dir / "examples" / "good-webmvc-routes.yml").read_text(encoding="utf-8")
    if "spring-cloud-starter-gateway-server-webflux" not in gateway_skill:
        fail("Boot 4 Gateway skill is missing the Gateway 5 WebFlux starter")
    if "server:\n        webflux:" not in webflux_routes:
        fail("Boot 4 WebFlux Gateway example must use the Gateway 5 server namespace")
    if "server:\n        webmvc:" not in webmvc_routes:
        fail("Boot 4 Web MVC Gateway example must use the Gateway 5 server namespace")

    for version in (3, 4):
        dockerfile = (
            BOOT_ROOTS[version]
            / "container-native-deployment"
            / "examples"
            / "good-dockerfile"
        ).read_text(encoding="utf-8")
        required = (
            "FROM eclipse-temurin:21-jre AS builder",
            "-Djarmode=tools",
            "extracted/spring-boot-loader/",
            "extracted/snapshot-dependencies/",
            'ENTRYPOINT ["java", "-jar", "application.jar"]',
        )
        if any(marker not in dockerfile for marker in required):
            fail(f"Boot {version} good Dockerfile must use complete layered-jar extraction")

        maven_dir = BOOT_ROOTS[version] / "multi-module-maven"
        maven_files = (
            maven_dir / "SKILL.md",
            maven_dir / "examples" / "good-parent-pom.xml",
            maven_dir / "templates" / "parent-pom.xml",
        )
        for path in maven_files:
            if "<mapstruct.version>1.6.3</mapstruct.version>" not in path.read_text(encoding="utf-8"):
                fail(f"{path.relative_to(ROOT)} must use the shared verified MapStruct version")

    rapidly_changing = {
        "container-native-deployment",
        "event-driven-messaging",
        "mcp-server",
        "multi-tenancy",
        "production-observability",
        "spring-cloud-gateway",
        "webflux-reactive-patterns",
    }
    for skill_name in rapidly_changing:
        skill_file = BOOT_ROOTS[4] / skill_name / "SKILL.md"
        if "## Official sources" not in skill_file.read_text(encoding="utf-8"):
            fail(f"{skill_file.relative_to(ROOT)} must link its version-sensitive official sources")

    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    if "MCP_Java_SDK-2.0" not in readme or "Java-17%2B" not in readme:
        fail("README compatibility badges are out of sync with the verified baselines")

    verification_pom = ROOT / "verification" / "spring-boot-4-mcp" / "pom.xml"
    workflow = ROOT / ".github" / "workflows" / "validate.yml"
    if not verification_pom.is_file():
        fail("missing the Spring Boot 4 MCP compilation fixture")
    if "verification/spring-boot-4-mcp/pom.xml" not in workflow.read_text(encoding="utf-8"):
        fail("CI does not compile the Spring Boot 4 MCP example")


def main() -> None:
    skill_sets = {
        version: {path.name for path in root.iterdir() if path.is_dir()}
        for version, root in BOOT_ROOTS.items()
    }
    for version in (3, 4):
        if len(skill_sets[version]) < 30:
            fail(f"expected at least 30 Boot {version} skills, found {len(skill_sets[version])}")

    for version, root in BOOT_ROOTS.items():
        for skill_name in sorted(skill_sets[version]):
            catalog_version = 4 if skill_name in skill_sets[4] else 3
            validate_skill(version, root / skill_name, catalog_version)

    validate_markdown_links()
    validate_packaging({version: len(skills) for version, skills in skill_sets.items()})
    validate_version_sensitive_examples()

    for version in (3, 4):
        jwt_templates = BOOT_ROOTS[version] / "spring-security-jwt" / "templates"
        jwt_text = "\n".join(path.read_text(encoding="utf-8") for path in jwt_templates.iterdir())
        if "isTokenValid(" in jwt_text or "isAccessTokenValid(" not in jwt_text:
            fail(f"Boot {version} JWT templates do not distinguish access tokens")

    print(
        f"Validated {len(skill_sets[3])} Spring Boot 3 skills and "
        f"{len(skill_sets[4])} Spring Boot 4 skills."
    )


if __name__ == "__main__":
    main()
