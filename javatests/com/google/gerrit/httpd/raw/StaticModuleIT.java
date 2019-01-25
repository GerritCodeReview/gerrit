// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.httpd.raw;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.testing.GerritBaseTests;
import java.util.regex.Pattern;
import java.util.StringJoiner;
import org.junit.Test;

public class StaticModuleIT extends GerritBaseTests {
  static final String REGEX =
      new StringJoiner("|")
          .add("/p/.*(?!/info/refs(.*)?)")
          .toString();

  private static final Pattern REGEX_PATTERN = Pattern.compile(REGEX);

  @Test
  public void assertInfoRefsGitUploadPackWorks() throws Exception {
    String dollar = "\\$";
    String result = REGEX_PATTERN.matcher("/p/test/test").replaceAll(dollar);
    assertThat(result).named("regex").isEqualTo("/plugins/lfs/content/default/$");
  }
}
