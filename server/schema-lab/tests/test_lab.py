import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import urllib.error


SPEC = importlib.util.spec_from_file_location("schema_lab", Path(__file__).resolve().parents[1] / "lab.py")
lab = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = lab
SPEC.loader.exec_module(lab)


class LabGuardTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        lab.write_json(self.root / "state.json", {"active": "blue", "runtime_root": str(self.root),
                                               "slots": {"blue": {"version": "C0"}}})
        self.run = lab.Lab("test-run", root=self.root)

    def test_run_id_cannot_escape_the_lab_directory_or_be_a_cli_option(self):
        for value in ("../production", "-p", "UPPER", "a/b", "a" * 41):
            with self.subTest(value=value), self.assertRaises(ValueError):
                lab.valid_name(value)

    def test_future_or_missing_migrations_are_not_registered_as_c0(self):
        names = [f"BOOT-INF/classes/db/migration/V{n}__migration.sql" for n in range(1, 11)]
        lab.check_migrations("C0", names)
        with self.assertRaises(ValueError):
            lab.check_migrations("C0", names + ["BOOT-INF/classes/db/migration/V11__drop.sql"])
        with self.assertRaises(ValueError):
            lab.check_migrations("C0", names[1:])

    def test_saved_artifact_is_not_rebuilt_or_overwritten(self):
        (self.root / "jars").mkdir()
        jar = self.root / "jars/C0.jar"
        jar.write_bytes(b"original artifact")
        with patch.object(lab.subprocess, "run") as command, self.assertRaises(ValueError):
            self.run.build("C0")
        command.assert_not_called()
        self.assertEqual(b"original artifact", jar.read_bytes())

    def test_active_slot_cannot_be_stopped(self):
        with patch.object(self.run, "compose") as command, self.assertRaises(ValueError):
            self.run.stop("blue")
        command.assert_not_called()

    def test_seed_does_not_touch_existing_listings(self):
        with patch.object(self.run, "query", side_effect=["10", "2"]) as query:
            with patch.object(self.run, "create") as create, self.assertRaises(ValueError):
                self.run.seed()
        self.assertEqual(2, query.call_count)
        create.assert_not_called()

    def test_failed_nginx_check_restores_previous_config_and_active_slot(self):
        config = self.root / "nginx/active-upstream.conf"
        config.parent.mkdir()
        before = "upstream setty_schema_lab { server blue:8080; }\n"
        config.write_text(before)
        with patch.object(self.run, "healthy"), patch.object(self.run, "running", return_value=["nginx"]):
            with patch.object(self.run, "compose", side_effect=[subprocess.CalledProcessError(1, "nginx -t"),
                                                               subprocess.CompletedProcess([], 0)]):
                with self.assertRaises(subprocess.CalledProcessError):
                    self.run.route("green")
        self.assertEqual(before, config.read_text())
        self.assertEqual("blue", self.run.state["active"])

    def test_http_error_is_recorded_without_authorization(self):
        error = urllib.error.HTTPError("http://localhost", 500, "failed", {}, io.BytesIO(b'{"error":"failure"}'))
        with patch.object(lab.urllib.request, "urlopen", side_effect=error), self.assertRaises(urllib.error.HTTPError):
            self.run.request("POST", "/api/listings", "failed-request", payload={})
        records = list((self.root / "evidence").glob("*.json"))
        self.assertEqual(1, len(records))
        self.assertEqual(500, json.loads(records[0].read_text())["status"])
        self.assertNotIn(lab.TOKEN, records[0].read_text())

    def test_connection_failure_is_also_recorded(self):
        with patch.object(lab.urllib.request, "urlopen", side_effect=urllib.error.URLError("timeout")):
            with self.assertRaises(urllib.error.URLError):
                self.run.request("GET", "/api/listings", "timed-out")
        record = json.loads(next((self.root / "evidence").glob("*.json")).read_text())
        self.assertEqual(0, record["status"])
        self.assertIn("timeout", record["error"])


if __name__ == "__main__":
    unittest.main()
