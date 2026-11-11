import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("launcher", Path(__file__).parents[1] / "local-support-web.py")
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)


class LocalSupportLauncherTest(unittest.TestCase):
    def test_literal_synthetic_key_is_parsed_without_expansion(self):
        self.assertEqual(launcher.parse_key('# test only\nOPENAI_API_KEY="fake-local-test"\n'), "fake-local-test")

    def test_shell_instructions_duplicate_and_empty_key_are_rejected(self):
        for value in ["OPENAI_API_KEY=", "OPENAI_API_KEY=$(touch /tmp/never-run)",
                      "OPENAI_API_KEY=`anything`", "OPENAI_API_KEY=a;cmd", "OTHER=abc",
                      "OPENAI_API_KEY=a\nOPENAI_API_KEY=b"]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                launcher.parse_key(value)

    def test_service_auth_database_and_jvm_settings_are_not_inherited(self):
        result = launcher.isolated_environment({"PATH": "/synthetic/bin", "HOME": "/synthetic/home",
            "OPENAI_API_KEY": "fake-ignored", "AWS_PROFILE": "unrelated", "AWS_ACCESS_KEY_ID": "fake-ignored",
            "SPRING_DATASOURCE_URL": "jdbc:never-used", "JAVA_TOOL_OPTIONS": "-Dnever-used=1",
            "SETTY_AI_SUPPORT_ENABLED": "true", "SETTY_NOTIFICATION_SENDER": "fcm"})
        self.assertEqual(result, {"PATH": "/synthetic/bin", "HOME": "/synthetic/home", "AWS_EC2_METADATA_DISABLED": "true"})

    def test_remote_docker_is_not_selected(self):
        with self.assertRaises(ValueError):
            launcher.isolated_environment({"DOCKER_HOST": "tcp://remote.invalid:2375"})


if __name__ == "__main__":
    unittest.main()
