import contextlib
import io
import unittest
from pathlib import Path

from validate_repository import find_icon, frontmatter, parse_yaml


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
