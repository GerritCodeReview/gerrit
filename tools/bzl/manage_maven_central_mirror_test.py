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

"""Unit tests for manage_maven_central_mirror."""

import unittest

import manage_maven_central_mirror as m


class MavenPathTest(unittest.TestCase):
    def test_jar_classifier_has_no_suffix(self):
        self.assertEqual(
            m.maven_path("com.google.guava:guava", "jar", "33.5.0-jre"),
            "com/google/guava/guava/33.5.0-jre/guava-33.5.0-jre.jar",
        )

    def test_sources_classifier_appends_suffix(self):
        self.assertEqual(
            m.maven_path("com.google.guava:guava", "sources", "33.5.0-jre"),
            "com/google/guava/guava/33.5.0-jre/guava-33.5.0-jre-sources.jar",
        )

    def test_classifier_only_artifact(self):
        # guice pins the -classes.jar, not a plain jar.
        self.assertEqual(
            m.maven_path("com.google.inject:guice", "classes", "6.0.0"),
            "com/google/inject/guice/6.0.0/guice-6.0.0-classes.jar",
        )

    def test_three_part_coordinate_sets_extension(self):
        self.assertEqual(
            m.maven_path("com.example:thing:pom", "jar", "1.0"),
            "com/example/thing/1.0/thing-1.0.pom",
        )

    def test_unsupported_coordinate_raises(self):
        with self.assertRaises(ValueError):
            m.maven_path("a:b:c:d", "jar", "1.0")


class ArtifactDirTest(unittest.TestCase):
    def test_dir_from_coordinate(self):
        self.assertEqual(
            m.artifact_dir("org.eclipse.jetty:jetty-http", "12.1.12"),
            "org/eclipse/jetty/jetty-http/12.1.12",
        )


class ClassifyTest(unittest.TestCase):
    def test_200_is_ok(self):
        self.assertEqual(m.classify(200), "ok")

    def test_only_404_is_missing(self):
        self.assertEqual(m.classify(404), "missing")

    def test_other_http_statuses_are_errors(self):
        for status in (403, 429, 500, 502, 503):
            self.assertEqual(m.classify(status), "error")

    def test_transport_error_string_is_error(self):
        self.assertEqual(m.classify("Connection refused"), "error")


if __name__ == "__main__":
    unittest.main()
