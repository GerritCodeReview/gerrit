# Copyright (C) 2026 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Unit tests for gerrit_maven_mirror_check."""

import contextlib
import io
import json
import sys
import tempfile
import unittest

import gerrit_maven_mirror_check as checker


class MavenPathTest(unittest.TestCase):
    def test_jar_classifier_has_no_suffix(self):
        self.assertEqual(
            checker.maven_path("com.google.guava:guava", "jar", "33.5.0-jre"),
            "com/google/guava/guava/33.5.0-jre/guava-33.5.0-jre.jar",
        )

    def test_sources_classifier_appends_suffix(self):
        self.assertEqual(
            checker.maven_path("com.google.guava:guava", "sources", "33.5.0-jre"),
            "com/google/guava/guava/33.5.0-jre/guava-33.5.0-jre-sources.jar",
        )

    def test_three_part_coordinate_sets_extension(self):
        self.assertEqual(
            checker.maven_path("com.example:thing:pom", "jar", "1.0"),
            "com/example/thing/1.0/thing-1.0.pom",
        )

    def test_unsupported_coordinate_raises(self):
        with self.assertRaises(ValueError):
            checker.maven_path("a:b:c:d", "jar", "1.0")


class ClassifyTest(unittest.TestCase):
    def test_200_is_ok(self):
        self.assertEqual(checker.classify(200), "ok")

    def test_only_404_is_missing(self):
        self.assertEqual(checker.classify(404), "missing")

    def test_other_http_statuses_are_errors(self):
        for status in (403, 429, 500, 502, 503):
            self.assertEqual(checker.classify(status), "error")

    def test_transport_error_string_is_error(self):
        self.assertEqual(checker.classify("Connection refused"), "error")


class FailedRunOutputTest(unittest.TestCase):
    def _run_with_stub_status(self, lock, status):
        with tempfile.NamedTemporaryFile("w", suffix=".json") as lock_file:
            json.dump(lock, lock_file)
            lock_file.flush()
            original_head = checker.head
            original_argv = sys.argv
            checker.head = lambda url, timeout: status
            sys.argv = ["checker", "--lock-file", lock_file.name, "--workers", "1"]
            out, err = io.StringIO(), io.StringIO()
            try:
                with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                    code = checker.main()
            finally:
                checker.head = original_head
                sys.argv = original_argv
        return code, out.getvalue(), err.getvalue()

    def test_missing_artifact_prints_request_pointer(self):
        lock = {"artifacts": {"com.example:missing": {"version": "1.0", "shasums": {"jar": "x"}}}}
        code, _, err = self._run_with_stub_status(lock, 404)
        self.assertEqual(code, 1)
        self.assertIn(checker.MIRROR_REQUEST_URL, err)
        self.assertIn("Upgrading Libraries", err)

    def test_mirrored_run_has_no_request_pointer(self):
        lock = {"artifacts": {"com.example:present": {"version": "1.0", "shasums": {"jar": "x"}}}}
        code, _, err = self._run_with_stub_status(lock, 200)
        self.assertEqual(code, 0)
        self.assertNotIn(checker.MIRROR_REQUEST_URL, err)


if __name__ == "__main__":
    unittest.main()
