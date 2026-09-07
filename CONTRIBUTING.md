# Contributing

Skills get better with real-world use. The most valuable contribution is a **Gotcha** — something your AI agent did wrong in a real Spring Boot project that this skill should prevent.

## Adding a Gotcha

Found a case where the agent generated bad code? Open the relevant skill and add to the Gotchas section:

```markdown
## Gotchas
- Agent does X when it should do Y — add specific pattern here
```

Keep it concrete. "Agent uses field injection" is good. "Agent sometimes makes mistakes" is not.

## Adding a New Skill

1. Copy an existing skill as a template
2. Create the skill in both version trees:
   - `skills/spring-boot-3/your-skill-name/`
   - `skills/spring-boot-4/your-skill-name/`
3. Keep the skill name and front matter aligned across both versions. If a topic is genuinely
   version-specific, document that clearly in the relevant folder and README catalog entry.
4. Follow the structure:
   - Front matter with `name` and `description` (the trigger — "Use when...")
   - Conventions with code examples
   - Gotchas section (most important)
5. Add `examples/good-*` and `examples/bad-*` files
6. Generate `agents/openai.yaml` with Codex's skill metadata generator
7. Link primary official sources for version-sensitive APIs
8. Optionally add `templates/` with copy-paste starting points

## Skill Description = Trigger

The `description` field is what tells the agent when to load the skill. Write it as:

```
Use when [specific condition]. Covers [topics].
```

Not as a summary of what's inside. The agent decides whether to load the skill based on this.

## What Makes a Good Skill

- **Context-aware** — explains tradeoffs and preserves existing project contracts
- **Gotchas-rich** — captures real failures, not hypothetical ones
- **Examples-driven** — shows good AND bad code side by side
- **Narrow** — one concern per skill, not a catch-all

## PR Checklist

- [ ] Skill has `name` and `description` in front matter
- [ ] Description is a trigger ("Use when..."), not a summary
- [ ] Has a Gotchas section with at least 3 entries
- [ ] Has paired good/bad examples
- [ ] Has `agents/openai.yaml` with a default prompt that names the skill
- [ ] Code examples compile (or are clearly illustrative)
- [ ] Version-sensitive claims link to primary official documentation
- [ ] Doesn't duplicate what Spring Boot already does by default
- [ ] Both Boot 3 and Boot 4 versions are updated, or the version-specific scope is documented
- [ ] `bash scripts/validate-skills.sh` passes

Install validator dependencies in a virtual environment first:

```sh
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r scripts/requirements.txt
python3 -m unittest discover -s scripts -p 'test_*.py'
bash scripts/validate-skills.sh
```

For executable template changes, run the matching Boot profiles in
[verification](verification/README.md). Add behavioral assertions for the failure being fixed.
For skill selection or instruction changes, add a representative prompt and observable
criteria to the [agent evaluations](evaluations/README.md). Record model results separately;
do not describe structural validation as proof of model correctness.
