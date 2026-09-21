import importlib.util
from pathlib import Path
import unittest

from validate_repository import ROOT


spec = importlib.util.spec_from_file_location(
    "evaluate_skills", Path(__file__).with_name("evaluate-skills.py")
)
evaluation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evaluation)


class EvaluationReferencesTest(unittest.TestCase):
    def test_sql_reference_is_available_to_tool_free_evaluator(self):
        case = next(
            case for case in evaluation.load_cases()
            if case["id"] == "concurrent-order-retry"
        )
        path = ROOT / "skills/spring-boot-4/idempotency-patterns/examples/good-idempotency.sql"
        prompt = evaluation.prompt_for(case)
        self.assertIn(f"FILE: {path.relative_to(ROOT)}\n{path.read_text()}", prompt)

    def test_automatic_templates_are_not_duplicated(self):
        reference = "transactional-patterns/templates/TransactionalOrderService.java"
        case = {
            "boot": 3,
            "skills": ["transactional-patterns"],
            "references": [reference, reference],
            "prompt": "Explain transaction boundaries.",
        }
        prompt = evaluation.prompt_for(case)
        self.assertEqual(prompt.count(f"FILE: skills/spring-boot-3/{reference}\n"), 1)
        self.assertNotIn("FILE: skills/spring-boot-4/", prompt)

    def test_rejects_missing_unselected_and_escaping_references(self):
        for reference in (
            "transactional-patterns/examples/missing.java",
            "idempotency-patterns/examples/good-idempotency.sql",
            "../../README.md",
            "transactional-patterns/../../../README.md",
            str(ROOT / "README.md"),
        ):
            with self.subTest(reference=reference), self.assertRaises(ValueError):
                evaluation.reference_paths({
                    "boot": 4,
                    "skills": ["transactional-patterns"],
                    "references": [reference],
                })

    def test_references_must_be_a_list_of_strings(self):
        for references in ("SKILL.md", [None], [1]):
            with self.subTest(references=references), self.assertRaises(ValueError):
                evaluation.reference_paths({
                    "boot": 4,
                    "skills": ["transactional-patterns"],
                    "references": references,
                })

    def test_cases_without_explicit_references_remain_supported(self):
        self.assertEqual(evaluation.reference_paths({
            "boot": 3, "skills": ["transactional-patterns"]
        }), [])


if __name__ == "__main__":
    unittest.main()
