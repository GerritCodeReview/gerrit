// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.extensions.api.lfs;

import static com.google.common.truth.Truth.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

public class LfsDefinitionsTest {
  private static final String[] URL_PREFIXES = new String[] {"/", "/a/", "/p/", "/a/p/"};

  @Test
  public void noLfsEndPoint_noMatch() {
    Pattern p = Pattern.compile(LfsDefinitions.LFS_URL_REGEX);
    doesNotMatch(p, "/foo");
    doesNotMatch(p, "/a/foo");
    doesNotMatch(p, "/p/foo");
    doesNotMatch(p, "/a/p/foo");

    doesNotMatch(p, "/info/lfs/objects/batch");
    doesNotMatch(p, "/info/lfs/objects/batch/foo");
    doesNotMatch(p, "/info/lfs/locks");
    doesNotMatch(p, "/info/lfs/locks/verify");
    doesNotMatch(p, "/info/lfs/locks/unlock");
    doesNotMatch(p, "/info/lfs/locks/lock_id/unlock");
  }

  @Test
  public void matchingLfsEndpoint_projectNameCaptured() {
    Pattern p = Pattern.compile(LfsDefinitions.LFS_URL_REGEX);
    testProjectGetsMatched(p, "foo/bar/info/lfs/objects/batch", "foo/bar");
    testProjectGetsMatched(p, "foo/bar/info/lfs/locks", "foo/bar");
    testProjectGetsMatched(p, "foo/bar/info/lfs/locks/verify", "foo/bar");
    testProjectAndLockIdGetMatched(
        p, "foo/bar/info/lfs/locks/lock_id/unlock", "foo/bar", "lock_id");
  }

  private void testProjectAndLockIdGetMatched(
      Pattern p, String url, String expectedProject, String expectedLockId) {
    for (String prefix : URL_PREFIXES) {
      matches(p, prefix + url, expectedProject, expectedLockId);
    }
  }

  private void testProjectGetsMatched(Pattern p, String url, String expected) {
    for (String prefix : URL_PREFIXES) {
      matches(p, prefix + url, expected);
    }
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

  private void matches(Pattern p, String input, String expectedProjectName, String expectedLockId) {
    Matcher m = p.matcher(input);
    assertThat(m.matches()).isTrue();
    assertThat(m.group(1)).isEqualTo(expectedProjectName);
    assertThat(m.group(2)).isEqualTo(expectedLockId);
  }
}
