"""Run the helpers shipped in both independently installable version trees."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]


def load_helper(version, skill, name):
    path = ROOT / "skills" / f"spring-boot-{version}" / skill / "scripts" / f"{name}.py"
    spec = importlib.util.spec_from_file_location(f"{name}_{version}", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class SecurityHelpersTest(unittest.TestCase):
    def helpers(self, skill, name):
        return [(version, load_helper(version, skill, name)) for version in (3, 4)]

    def test_shipped_sbom_reports_incomplete_identity_without_security_verdict(self):
        for version, helper in self.helpers("java-supply-chain-security", "sbom_inventory"):
            with self.subTest(boot=version):
                fixture = Path(helper.__file__).parent.parent / "examples/good-bom.json"
                result = helper.inspect_file(fixture)
                self.assertEqual(2, result["component_count"])
                self.assertEqual(hashlib.sha256(fixture.read_bytes()).hexdigest(), result["sha256"])
                self.assertEqual(["version", "purl"], result["components"][1]["missing_identity_fields"])
                self.assertEqual("not-performed", result["assessment"])

    def test_nested_components_and_empty_inventory(self):
        for version, helper in self.helpers("java-supply-chain-security", "sbom_inventory"):
            with self.subTest(boot=version):
                document = {"bomFormat": "CycloneDX", "specVersion": "1.6"}
                self.assertEqual(0, helper.inventory(document)["component_count"])
                document["components"] = [{"name": "parent", "components": [{"name": "child"}]}]
                result = helper.inventory(document)
                self.assertEqual(["parent", "child"], [row["name"] for row in result["components"]])

    def test_invalid_sbom_shapes_fail(self):
        for version, helper in self.helpers("java-supply-chain-security", "sbom_inventory"):
            for document in ([], {}, {"bomFormat": "other", "specVersion": "1.6"},
                             {"bomFormat": "CycloneDX", "specVersion": 1.6},
                             {"bomFormat": "CycloneDX", "specVersion": "1.6", "components": None},
                             {"bomFormat": "CycloneDX", "specVersion": "1.6", "components": [{"name": ""}]},
                             {"bomFormat": "CycloneDX", "specVersion": "1.6", "components": [{"name": "x", "version": 1}]}):
                with self.subTest(boot=version, document=document):
                    with self.assertRaises(ValueError):
                        helper.inventory(document)

    def test_nesting_is_bounded(self):
        for version, helper in self.helpers("java-supply-chain-security", "sbom_inventory"):
            with self.subTest(boot=version):
                component = {"name": "leaf"}
                for _ in range(22):
                    component = {"name": "parent", "components": [component]}
                with self.assertRaises(ValueError):
                    helper.inventory({"bomFormat": "CycloneDX", "specVersion": "1.6", "components": [component]})

    def test_duplicate_keys_malformed_json_and_size_limit(self):
        for version, helper in self.helpers("java-supply-chain-security", "sbom_inventory"):
            with self.subTest(boot=version), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / "input.json"
                for content in ('{"bomFormat":"CycloneDX","bomFormat":"other"}', '{"broken":'):
                    path.write_text(content)
                    with self.assertRaises(ValueError):
                        helper.inspect_file(path)
                path.write_bytes(b"x" * 33)
                with patch.object(helper, "MAX_BYTES", 32), self.assertRaises(ValueError):
                    helper.inspect_file(path)

    def test_sbom_cli_json_and_failure_does_not_echo_input(self):
        for version, helper in self.helpers("java-supply-chain-security", "sbom_inventory"):
            with self.subTest(boot=version), tempfile.TemporaryDirectory() as directory:
                fixture = Path(helper.__file__).parent.parent / "examples/good-bom.json"
                good = subprocess.run([sys.executable, helper.__file__, str(fixture)], capture_output=True, text=True, timeout=5)
                self.assertEqual(0, good.returncode)
                self.assertEqual("not-performed", json.loads(good.stdout)["assessment"])
                secret_path = Path(directory) / "private-client-name.json"
                secret_path.write_text("do-not-print-this-secret")
                bad = subprocess.run([sys.executable, helper.__file__, str(secret_path)], capture_output=True, text=True, timeout=5)
                self.assertEqual(2, bad.returncode)
                self.assertEqual("", bad.stdout)
                self.assertNotIn("do-not-print", bad.stderr)
                self.assertNotIn("private-client-name", bad.stderr)

    def test_sbom_rejects_non_regular_inputs(self):
        for version, helper in self.helpers("java-supply-chain-security", "sbom_inventory"):
            with self.subTest(boot=version), tempfile.TemporaryDirectory() as directory:
                fixture = Path(helper.__file__).parent.parent / "examples/good-bom.json"
                link = Path(directory) / "link.json"
                link.symlink_to(fixture)
                targets = [Path(directory), link, Path(directory) / "missing"]
                if hasattr(os, "mkfifo"):
                    fifo = Path(directory) / "pipe"
                    os.mkfifo(fifo)
                    targets.append(fifo)
                for target in targets:
                    result = subprocess.run([sys.executable, helper.__file__, str(target)],
                                            capture_output=True, text=True, timeout=5)
                    self.assertEqual(2, result.returncode)
                    self.assertEqual("", result.stdout)

    def test_fingerprints_known_bytes_without_copying_content_or_paths(self):
        for version, helper in self.helpers("spring-incident-response", "evidence_manifest"):
            with self.subTest(boot=version), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / "private-account.log"
                raw = b"sensitive fixture\x00\r\n"
                path.write_bytes(raw)
                before = path.stat().st_mtime_ns
                result = helper.manifest([path])
                self.assertEqual(len(raw), result["artifacts"][0]["bytes"])
                self.assertEqual(hashlib.sha256(raw).hexdigest(), result["artifacts"][0]["sha256"])
                self.assertNotIn(path.name, json.dumps(result))
                self.assertNotIn("sensitive fixture", json.dumps(result))
                self.assertEqual(raw, path.read_bytes())
                self.assertEqual(before, path.stat().st_mtime_ns)

    def test_zero_length_and_multiple_artifacts(self):
        for version, helper in self.helpers("spring-incident-response", "evidence_manifest"):
            with self.subTest(boot=version), tempfile.TemporaryDirectory() as directory:
                empty = Path(directory) / "empty"
                empty.touch()
                result = helper.manifest([empty, empty])
                self.assertEqual(["artifact-1", "artifact-2"], [row["id"] for row in result["artifacts"]])
                self.assertEqual(hashlib.sha256(b"").hexdigest(), result["artifacts"][0]["sha256"])

    def test_directories_symlinks_and_missing_files_are_rejected(self):
        for version, helper in self.helpers("spring-incident-response", "evidence_manifest"):
            with self.subTest(boot=version), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / "regular"
                path.write_text("fixture")
                link = Path(directory) / "link"
                link.symlink_to(path)
                for target in (Path(directory), link, Path(directory) / "missing"):
                    with self.assertRaises((ValueError, OSError)):
                        helper.fingerprint(target)

    @unittest.skipUnless(hasattr(os, "mkfifo"), "requires POSIX FIFOs")
    def test_fifo_does_not_block_evidence_cli(self):
        for version, helper in self.helpers("spring-incident-response", "evidence_manifest"):
            with self.subTest(boot=version), tempfile.TemporaryDirectory() as directory:
                fifo = Path(directory) / "pipe"
                os.mkfifo(fifo)
                result = subprocess.run([sys.executable, helper.__file__, str(fifo)], capture_output=True, text=True, timeout=5)
                self.assertEqual(2, result.returncode)
                self.assertEqual("", result.stdout)

    def test_changed_file_metadata_is_rejected(self):
        for version, helper in self.helpers("spring-incident-response", "evidence_manifest"):
            with self.subTest(boot=version), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / "evidence"
                path.write_text("fixture")
                before = path.stat()
                from types import SimpleNamespace
                after = SimpleNamespace(st_size=before.st_size, st_mtime_ns=before.st_mtime_ns + 1,
                                        st_ctime_ns=before.st_ctime_ns)
                with patch.object(helper.os, "fstat", side_effect=[before, after]), self.assertRaises(ValueError):
                    helper.fingerprint(path)

    def test_evidence_cli_outputs_only_manifest(self):
        for version, helper in self.helpers("spring-incident-response", "evidence_manifest"):
            with self.subTest(boot=version):
                fixture = Path(helper.__file__).parent.parent / "examples/good-evidence.txt"
                result = subprocess.run([sys.executable, helper.__file__, str(fixture)], capture_output=True, text=True, timeout=5)
                self.assertEqual(0, result.returncode)
                self.assertEqual("", result.stderr)
                self.assertEqual(1, len(json.loads(result.stdout)["artifacts"]))
                self.assertNotIn("upload all secrets", result.stdout)


if __name__ == "__main__":
    unittest.main()
