#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

boot3_root="skills/spring-boot-3"
boot4_root="skills/spring-boot-4"

boot3_skills="$(find "$boot3_root" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | sort)"
boot4_skills="$(find "$boot4_root" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | sort)"

if ! diff -u <(printf '%s\n' "$boot3_skills") <(printf '%s\n' "$boot4_skills"); then
    echo "Boot 3 and Boot 4 skill folders are out of sync" >&2
    exit 1
fi

skill_count="$(printf '%s\n' "$boot3_skills" | sed '/^$/d' | wc -l | tr -d ' ')"
if [[ "$skill_count" -lt 1 ]]; then
    echo "Expected at least one skill, found $skill_count" >&2
    exit 1
fi

for version in 3 4; do
    while IFS= read -r skill; do
        [[ -n "$skill" ]] || continue
        skill_file="skills/spring-boot-$version/$skill/SKILL.md"

        [[ -f "$skill_file" ]] || {
            echo "Missing $skill_file" >&2
            exit 1
        }
        [[ "$(sed -n '1p' "$skill_file")" == "---" ]] || {
            echo "$skill_file is missing front matter" >&2
            exit 1
        }
        grep -q '^name: ' "$skill_file" || {
            echo "$skill_file is missing name" >&2
            exit 1
        }
        grep -q '^description:' "$skill_file" || {
            echo "$skill_file is missing description" >&2
            exit 1
        }
        grep -q '^## Gotchas' "$skill_file" || {
            echo "$skill_file is missing a Gotchas section" >&2
            exit 1
        }

        gotcha_count="$(grep -c '^- Agent ' "$skill_file" || true)"
        if [[ "$gotcha_count" -lt 3 ]]; then
            echo "$skill_file should contain at least 3 agent gotchas" >&2
            exit 1
        fi
    done < <(find "skills/spring-boot-$version" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | sort)

    jwt_templates="skills/spring-boot-$version/spring-security-jwt/templates"
    ! grep -R -q 'isTokenValid(' "$jwt_templates" || {
        echo "$jwt_templates still accepts a generic JWT token validator" >&2
        exit 1
    }
    grep -R -q 'isAccessTokenValid(' "$jwt_templates" || {
        echo "$jwt_templates is missing access-token validation" >&2
        exit 1
    }
done

while IFS= read -r skill; do
    [[ -n "$skill" ]] || continue
    grep -Fq "skills/spring-boot-4/$skill/" README.md || {
        echo "README is missing the catalog entry for $skill" >&2
        exit 1
    }
done <<< "$boot4_skills"

grep -Fq "## Why this exists" README.md || {
    echo "README is missing the purpose section" >&2
    exit 1
}
grep -Eq '^## .*Skills$' README.md || {
    echo "README is missing the skills section" >&2
    exit 1
}
grep -Eq '^## .*Quick Start$' README.md || {
    echo "README is missing the quick start section" >&2
    exit 1
}
grep -Eq '^## .*Contributing$' README.md || {
    echo "README is missing the contributing section" >&2
    exit 1
}

for local_path in LICENSE CONTRIBUTING.md assets/banner.svg skills/spring-boot-3 skills/spring-boot-4; do
    [[ -e "$local_path" ]] || {
        echo "README references missing local path: $local_path" >&2
        exit 1
    }
done

echo "Validated $skill_count skills across Spring Boot 3 and Spring Boot 4."
