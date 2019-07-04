// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.httpd.restapi;

import static com.google.common.truth.Truth8.assertThat;

import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.reviewdb.client.Project;
import org.junit.Test;

public class RestApiServletTest {
  @Test
  public void parseChangeId() {
    testParseChangeId("123");
    testParseChangeId("foo~123");
    testParseChangeId("foo%2Fbar~123");
    testParseChangeId("foo~master~123");
    testParseChangeId("foo%2Fbar~refs%2Fheads%2Fmaster~123");
    testParseChangeId("I972e8e8f9339b811c78157abef771b8c32be078c");
  }

  @Test
  public void parseProjectName() {
    testParseProjectName("foo");
    testParseProjectName("foo/bar");
  }

  @Test
  public void extractProjectNameFromChangeId() {
    assertThat(RestApiServlet.extractProjectNameFromChangeId("foo~123"))
        .hasValue(Project.nameKey("foo"));
    assertThat(RestApiServlet.extractProjectNameFromChangeId("foo%2Fbar~123"))
        .hasValue(Project.nameKey("foo/bar"));
    assertThat(RestApiServlet.extractProjectNameFromChangeId("foo~master~123"))
        .hasValue(Project.nameKey("foo"));
    assertThat(RestApiServlet.extractProjectNameFromChangeId("foo%2Fbar~refs%2Fheads%2Fmaster~123"))
        .hasValue(Project.nameKey("foo/bar"));
    assertThat(RestApiServlet.extractProjectNameFromChangeId("123")).isEmpty();
    assertThat(RestApiServlet.extractProjectNameFromChangeId("abc")).isEmpty();
  }

  private static void testParseChangeId(String id) {
    assertParseChangeId("/changes/%s", id);
    assertParseChangeId("/changes/%s/", id);
    assertParseChangeId("/changes/%s/detail", id);
    assertParseChangeId("/changes/%s/revisions/", id);
    assertParseChangeId("/changes/%s/revisions/0", id);

    assertParseChangeIdNoMatch("/changes//%s", id);
    assertParseChangeIdNoMatch("/accounts/%s", id);
    assertParseChangeIdNoMatch("/groups/%s", id);
    assertParseChangeIdNoMatch("/projects/%s", id);

    assertParseChangeIdNoMatch("/changes");
    assertParseChangeIdNoMatch("/changes/");
  }

  private static void assertParseChangeId(String urlFormat, String id) {
    assertThat(
            RestApiServlet.parseChangeId(
                String.format(urlFormat, IdString.fromDecoded(id).encoded())))
        .hasValue(id);
  }

  private static void assertParseChangeIdNoMatch(String urlFormat, String id) {
    assertThat(
            RestApiServlet.parseChangeId(
                String.format(urlFormat, IdString.fromDecoded(id).encoded())))
        .isEmpty();
  }

  private static void assertParseChangeIdNoMatch(String url) {
    assertThat(RestApiServlet.parseChangeId(url)).isEmpty();
  }

  private static void testParseProjectName(String name) {
    assertParseProjectName("/projects/%s", name);
    assertParseProjectName("/projects/%s/", name);
    assertParseProjectName("/projects/%s/description", name);
    assertParseProjectName("/projects/%s/files/", name);
    assertParseProjectName("/projects/%s/files/a.txt", name);

    assertParseProjectNameNoMatch("/projects//%s", name);
    assertParseProjectNameNoMatch("/accounts/%s", name);
    assertParseProjectNameNoMatch("/changes/%s", name);
    assertParseProjectNameNoMatch("/groups/%s", name);

    assertParseProjectNameNoMatch("/projects");
    assertParseProjectNameNoMatch("/projects/");
  }

  private static void assertParseProjectName(String urlFormat, String name) {
    assertThat(
            RestApiServlet.parseProjectName(
                String.format(urlFormat, IdString.fromDecoded(name).encoded())))
        .hasValue(Project.nameKey(name));
  }

  private static void assertParseProjectNameNoMatch(String urlFormat, String name) {
    assertThat(
            RestApiServlet.parseProjectName(
                String.format(urlFormat, IdString.fromDecoded(name).encoded())))
        .isEmpty();
  }

  private static void assertParseProjectNameNoMatch(String url) {
    assertThat(RestApiServlet.parseProjectName(url)).isEmpty();
  }
}
