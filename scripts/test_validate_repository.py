import contextlib
import io
import unittest
from pathlib import Path

from validate_repository import frontmatter, parse_yaml


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


if __name__ == "__main__":
    unittest.main()
