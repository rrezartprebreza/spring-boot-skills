import contextlib
import io
import unittest
from pathlib import Path
from unittest.mock import patch

from validate_repository import ROOT, find_icon, frontmatter, load_json, parse_yaml


class JsonValidationTest(unittest.TestCase):
    def setUp(self):
        self.path = ROOT / ".claude-plugin" / "marketplace.json"

    def test_accepts_distinct_objects_with_shared_key_names(self):
        text = '{"plugins": [{"name": "boot3"}, {"name": "boot4"}]}'
        with patch.object(Path, "read_text", return_value=text):
            self.assertEqual(load_json(self.path), {
                "plugins": [{"name": "boot3"}, {"name": "boot4"}]
            })

    def test_rejects_duplicate_keys_at_any_depth(self):
        """Reject root, nested, and escape-equivalent duplicates with diagnostics."""
        cases = (
            ('{"plugins": [], "plugins": []}', "plugins"),
            ('{"plugins": [{"name": "boot3", "name": "boot4"}]}', "name"),
            (r'{"name": "boot3", "\u006eame": "boot4"}', "name"),
        )
        for text, key in cases:
            with self.subTest(text=text), patch.object(Path, "read_text", return_value=text):
                error = io.StringIO()
                with contextlib.redirect_stderr(error), self.assertRaises(SystemExit) as raised:
                    load_json(self.path)
                self.assertEqual(raised.exception.code, 1)
                self.assertIn(".claude-plugin/marketplace.json", error.getvalue())
                self.assertIn(f"duplicate JSON key: {key}", error.getvalue())

    def test_rejects_non_object_roots(self):
        """Reject arrays and scalar roots before manifest consumers access fields."""
        for text in ('[]', 'null', '"plugin"', '42', 'true'):
            with self.subTest(text=text), patch.object(Path, "read_text", return_value=text):
                error = io.StringIO()
                with contextlib.redirect_stderr(error), self.assertRaises(SystemExit):
                    load_json(self.path)
                self.assertIn("expected a JSON object", error.getvalue())

    def test_reports_malformed_json(self):
        with patch.object(Path, "read_text", return_value='{"plugins":'):
            error = io.StringIO()
            with contextlib.redirect_stderr(error), self.assertRaises(SystemExit):
                load_json(self.path)
            self.assertIn("invalid JSON in .claude-plugin/marketplace.json", error.getvalue())

    def test_reports_unreadable_file(self):
        with patch.object(Path, "read_text", side_effect=OSError("permission denied")):
            error = io.StringIO()
            with contextlib.redirect_stderr(error), self.assertRaises(SystemExit):
                load_json(self.path)
            self.assertIn("permission denied", error.getvalue())


class YamlValidationTest(unittest.TestCase):
    def test_inline_description_and_optional_metadata(self):
        name, description = frontmatter(Path("SKILL.md"),
            "---\nname: api\ndescription: 'Use when editing APIs'\nmetadata:\n  owner: team\n---\n")
        self.assertEqual((name, description), ("api", "Use when editing APIs"))

    def test_folded_description(self):
        _, description = frontmatter(Path("SKILL.md"),
            "---\nname: api\ndescription: >\n  Use when editing\n  APIs\n---\n")
        self.assertIn("editing APIs", description)

    def test_rejects_invalid_duplicate_and_unsafe_yaml(self):
        for text in ("name: [", "name: a\nname: b", "- item", "!!python/object:example {}"):
            with self.subTest(text=text), contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit):
                    parse_yaml(text, Path("invalid.yaml"))

    def test_rejects_non_string_description(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            frontmatter(Path("SKILL.md"), "---\nname: api\ndescription: false\n---\n")


class IconValidationTest(unittest.TestCase):
    def test_detects_check_cross_and_warning_icons(self):
        for line in ("// \u2705 GOOD", "-- \u274c Dangerous", "// \u26a0\ufe0f note", "\U0001F525 Hot"):
            with self.subTest(line=line):
                self.assertIsNotNone(find_icon(line))

    def test_allows_plain_labels_and_typographic_arrows(self):
        for line in ("// BAD \u2014 field injection", "Controller \u2192 Service", "-- Safe: add column"):
            with self.subTest(line=line):
                self.assertIsNone(find_icon(line))


if __name__ == "__main__":
    unittest.main()
