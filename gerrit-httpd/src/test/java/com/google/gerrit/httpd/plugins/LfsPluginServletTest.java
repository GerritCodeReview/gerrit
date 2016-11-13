// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.httpd.plugins;

import static com.google.common.truth.Truth.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

public class LfsPluginServletTest {

  @Test
  public void noLfsEndPoint_noMatch() {
    Pattern p = Pattern.compile(LfsPluginServlet.URL_REGEX);
    doesNotMatch(p, "/foo");
    doesNotMatch(p, "/a/foo");
    doesNotMatch(p, "/p/foo");
    doesNotMatch(p, "/a/p/foo");

    doesNotMatch(p, "/info/lfs/objects/batch");
    doesNotMatch(p, "/info/lfs/objects/batch/foo");
  }

  @Test
  public void matchingLfsEndpoint_projectNameCaptured() {
    Pattern p = Pattern.compile(LfsPluginServlet.URL_REGEX);
    matches(p, "/foo/bar/info/lfs/objects/batch", "foo/bar");
    matches(p, "/a/foo/bar/info/lfs/objects/batch", "foo/bar");
    matches(p, "/p/foo/bar/info/lfs/objects/batch", "foo/bar");
    matches(p, "/a/p/foo/bar/info/lfs/objects/batch", "foo/bar");
  }

  private void doesNotMatch(Pattern p, String input) {
    Matcher m = p.matcher(input);
    assertThat(m.matches()).isFalse();
  }

  private void matches(Pattern p, String input, String expectedProjectName) {
    Matcher m = p.matcher(input);
    assertThat(m.matches()).isTrue();
    assertThat(m.group(1)).isEqualTo(expectedProjectName);
  }
}
