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

package com.google.gerrit.common;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.launcher.GerritLauncher;
import java.util.regex.Pattern;
import org.junit.Test;

public final class VersionTest {
  private static final Pattern DEV_PATTERN =
      Pattern.compile("^" + Pattern.quote(Version.DEV) + "$");

  private static final Pattern GIT_DESCRIBE_PATTERN =
      Pattern.compile(
          "^[1-9]+\\.[0-9]+(\\.[0-9]+)*(-rc[0-9]+)?(-[0-9]+" + "-g[0-9a-f]{7,})?(-dirty)?$");

  @Test
  public void version() {
    Pattern expected =
        GerritLauncher.isRunningInEclipse()
            ? DEV_PATTERN // Different source line so it shows up in coverage.
            : GIT_DESCRIBE_PATTERN;
    assertThat(Version.getVersion()).matches(expected);
    // Try again in case of caching issues.
    assertThat(Version.getVersion()).matches(expected);
  }

  @Test
  public void gitDescribePattern() {
    for (String suffix : ImmutableList.of("", "-dirty")) {
      assertThat("2.15-rc0" + suffix).matches(GIT_DESCRIBE_PATTERN);
      assertThat("2.15-rc0" + suffix).matches(GIT_DESCRIBE_PATTERN);
      assertThat("2.15-rc1" + suffix).matches(GIT_DESCRIBE_PATTERN);
      assertThat("2.15" + suffix).matches(GIT_DESCRIBE_PATTERN);
      assertThat("2.15.1" + suffix).matches(GIT_DESCRIBE_PATTERN);
      assertThat("2.15.1.2" + suffix).matches(GIT_DESCRIBE_PATTERN);
      assertThat("2.15.1.2.3" + suffix).matches(GIT_DESCRIBE_PATTERN);
      assertThat("2.15.1-rc1" + suffix).matches(GIT_DESCRIBE_PATTERN);
      assertThat("2.15-rc2-123-gabcd123" + suffix).matches(GIT_DESCRIBE_PATTERN);
      assertThat("2.15-123-gabcd123" + suffix).matches(GIT_DESCRIBE_PATTERN);
    }

    assertThat("2.15-ugly").doesNotMatch(GIT_DESCRIBE_PATTERN);
    assertThat("(dev)").doesNotMatch(GIT_DESCRIBE_PATTERN);
    assertThat("1").doesNotMatch(GIT_DESCRIBE_PATTERN);
    assertThat("v2.15").doesNotMatch(GIT_DESCRIBE_PATTERN);
  }
}
