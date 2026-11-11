import tempfile
import tomllib
import unittest
from pathlib import Path
from unittest.mock import patch

import yaml

from specialist_agents import CLIENTS, READ_ROLES, ROOT, artifacts, check, install, load_roles, render
from validate_repository import load_json


class SpecialistDefinitionTest(unittest.TestCase):
    def test_generated_files_and_marketplace_match_sources(self):
        check()

    def test_standalone_check_requires_both_unique_marketplace_plugins(self):
        """Reject absent, incomplete, duplicated, and malformed plugin collections."""
        plugins = load_json(ROOT / ".claude-plugin" / "marketplace.json")["plugins"]
        invalid_marketplaces = [
            {},
            {"plugins": None},
            {"plugins": {}},
            {"plugins": []},
            {"plugins": [plugins[0]]},
            {"plugins": [plugins[1]]},
            {"plugins": [plugins[0], plugins[0]]},
            {"plugins": [*plugins, plugins[0]]},
            {"plugins": [plugins[0], {**plugins[1], "name": "unexpected-plugin"}]},
            {"plugins": [plugins[0], {"name": []}]},
            {"plugins": [plugins[0], None]},
        ]
        for marketplace in invalid_marketplaces:
            with self.subTest(marketplace=marketplace):
                with patch("specialist_agents.load_json", side_effect=lambda path: marketplace if path.name == "marketplace.json" else load_json(path)):
                    with self.assertRaisesRegex(ValueError, "exactly one Boot 3 and one Boot 4"):
                        check()

    def test_standalone_check_accepts_reordered_plugins_but_rejects_missing_agents(self):
        """Treat plugin ordering as irrelevant while still checking each agent list."""
        marketplace = load_json(ROOT / ".claude-plugin" / "marketplace.json")
        marketplace["plugins"].reverse()
        with patch("specialist_agents.load_json", side_effect=lambda path: marketplace if path.name == "marketplace.json" else load_json(path)):
            check()
            marketplace["plugins"][0]["agents"] = []
            with self.assertRaisesRegex(ValueError, "does not expose the six specialists"):
                check()

    def test_clients_share_role_workflow_and_skill_routes(self):
        """Require both native formats to preserve every role's workflow and routes."""
        generated = artifacts()
        roles = load_roles()
        self.assertEqual(len(generated), 12)
        for name, role in roles.items():
            with self.subTest(role=name):
                claude = generated[Path(f"specialists/claude/spring-{name}.md")]
                metadata, body = claude.split("---\n", 2)[1:]
                metadata = yaml.safe_load(metadata)
                codex = tomllib.loads(generated[Path(f"specialists/codex/spring-{name}.toml")])
                self.assertEqual(metadata["name"], codex["name"])
                self.assertEqual(metadata["description"], codex["description"])
                for instruction in role["instructions"]:
                    self.assertIn(instruction, body)
                    self.assertIn(instruction, codex["developer_instructions"])
                for skill in role["skills"]:
                    self.assertIn(f"`{skill}`", body)
                    self.assertIn(f"`{skill}`", codex["developer_instructions"])

    def test_read_roles_have_no_claude_write_or_shell_tools(self):
        """Check Claude tool restrictions and Codex's inherited-permission contract."""
        generated = artifacts()
        for name in READ_ROLES:
            with self.subTest(role=name):
                content = generated[Path(f"specialists/claude/spring-{name}.md")]
                metadata = yaml.safe_load(content.split("---\n", 2)[1])
                self.assertEqual(set(metadata["tools"].split(", ")), {"Read", "Glob", "Grep", "Skill"})
                codex = tomllib.loads(generated[Path(f"specialists/codex/spring-{name}.toml")])
                self.assertEqual(set(codex), {"name", "description", "developer_instructions"})
                self.assertIn("read-only inspection commands", codex["developer_instructions"])
                self.assertIn("Do not edit files or run tests", codex["developer_instructions"])
                self.assertNotIn("sandbox_mode", codex)
                self.assertNotIn("approval_policy", codex)

    def test_rejects_missing_versioned_skill(self):
        roles = load_roles()
        roles["architect"]["skills"].append("not-a-shipped-skill")
        with patch("specialist_agents.load_json", return_value=roles):
            with self.assertRaisesRegex(ValueError, "missing Boot 3 skill"):
                load_roles()

    def test_native_formats_preserve_quoted_multiline_instructions(self):
        role = load_roles()["reviewer"]
        common = 'A "quoted" path C:\\code\nA triple quote: """\nUnicode: \U00010400'
        codex = tomllib.loads(render("reviewer", role, common, "codex"))
        self.assertTrue(codex["developer_instructions"].startswith(common + "\n"))
        claude = render("reviewer", role, common, "claude")
        self.assertTrue(claude.split("---\n", 2)[2].lstrip().startswith(common + "\n"))

    def test_rejects_access_expansion(self):
        roles = load_roles()
        roles["reviewer"]["access"] = "edit"
        with patch("specialist_agents.load_json", return_value=roles):
            with self.assertRaisesRegex(ValueError, "invalid access"):
                load_roles()

    def test_rejects_duplicate_skill_and_invalid_role_fields(self):
        roles = load_roles()
        roles["architect"]["skills"] *= 2
        with patch("specialist_agents.load_json", return_value=roles):
            with self.assertRaisesRegex(ValueError, "duplicate skill"):
                load_roles()
        roles = load_roles()
        roles["reviewer"]["permissionMode"] = "bypassPermissions"
        with patch("specialist_agents.load_json", return_value=roles):
            with self.assertRaisesRegex(ValueError, "invalid role schema"):
                load_roles()


class SpecialistInstallTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.project = Path(self.temp.name) / "project with spaces"
        self.project.mkdir()

    def test_dry_run_writes_nothing(self):
        paths = install(self.project, list(CLIENTS), True)
        self.assertEqual(len(paths), 12)
        self.assertEqual(list(self.project.iterdir()), [])

    def test_install_is_idempotent_and_preserves_unrelated_config(self):
        """Verify repeat installation is a no-op and preserves existing client config."""
        config = self.project / ".codex" / "config.toml"
        config.parent.mkdir()
        config.write_text('model = "existing-model"\n')
        paths = install(self.project, list(CLIENTS), False)
        self.assertEqual(len(paths), 12)
        self.assertEqual(install(self.project, list(CLIENTS), False), [])
        self.assertEqual(config.read_text(), 'model = "existing-model"\n')
        for path in paths:
            self.assertTrue(path.is_file())

    def test_collision_aborts_entire_install(self):
        existing = self.project / ".codex" / "agents" / "spring-reviewer.toml"
        existing.parent.mkdir(parents=True)
        existing.write_text("user-owned content")
        with self.assertRaisesRegex(ValueError, "existing agent differs"):
            install(self.project, list(CLIENTS), False)
        self.assertFalse((self.project / ".claude").exists())
        self.assertEqual(list(existing.parent.iterdir()), [existing])
        self.assertEqual(existing.read_text(), "user-owned content")

    def test_symlink_directory_is_refused(self):
        outside = Path(self.temp.name) / "outside"
        outside.mkdir()
        (self.project / ".codex").symlink_to(outside, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "symlink destination"):
            install(self.project, ["codex"], False)
        self.assertEqual(list(outside.iterdir()), [])

    def test_dangling_symlink_file_is_refused(self):
        agents = self.project / ".codex" / "agents"
        agents.mkdir(parents=True)
        (agents / "spring-architect.toml").symlink_to(self.project / "missing")
        with self.assertRaisesRegex(ValueError, "symlink destination"):
            install(self.project, ["codex"], False)

    def test_install_only_selected_client(self):
        self.assertEqual(len(install(self.project, ["claude"], False)), 6)
        self.assertFalse((self.project / ".codex").exists())

    def test_refuses_missing_project_and_file_parent(self):
        with self.assertRaisesRegex(ValueError, "does not exist"):
            install(self.project / "missing", ["codex"], False)
        (self.project / ".codex").write_text("not a directory")
        with self.assertRaisesRegex(ValueError, "not a directory"):
            install(self.project, ["codex"], False)

    def test_creation_failure_rolls_back_only_new_files(self):
        original_open = Path.open
        protected = self.project / "keep.txt"
        protected.write_text("keep")

        def failing_open(path, *args, **kwargs):
            """Fail one exclusive agent-file creation after an earlier file was written."""
            if path.name == "spring-implementer.toml" and args and args[0] == "x":
                raise OSError("simulated write failure")
            return original_open(path, *args, **kwargs)

        with patch.object(Path, "open", failing_open):
            with self.assertRaisesRegex(OSError, "simulated write failure"):
                install(self.project, ["codex"], False)
        self.assertEqual(list((self.project / ".codex" / "agents").iterdir()), [])
        self.assertEqual(protected.read_text(), "keep")


if __name__ == "__main__":
    unittest.main()
