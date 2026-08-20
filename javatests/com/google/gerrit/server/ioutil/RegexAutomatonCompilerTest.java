// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.ioutil;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class RegexAutomatonCompilerTest {
  private final RegexAutomatonCompiler compiler = new DefaultRegexAutomatonCompiler();

  @Test
  public void compileMatcherIgnoresRedundantAnchors() {
    for (String re : ImmutableList.of("f.*o", "^f.*o", "f.*o$", "^f.*o$")) {
      assertThat(compiler.compileMatcher(re).run("foo")).isTrue();
      assertThat(compiler.compileMatcher(re).run("bar")).isFalse();
    }
  }

  @Test
  public void compileMatcherKeepsEscapedDollar() {
    assertThat(compiler.compileMatcher("^f.*\\$").run("foo$")).isTrue();
    assertThat(compiler.compileMatcher("^f.*\\$").run("foo")).isFalse();
  }

  @Test
  public void compileMatcherMatchesWholeInput() {
    assertThat(compiler.compileMatcher("f.*o").run("xfoox")).isFalse();
  }

  @Test(expected = IllegalArgumentException.class)
  public void compileMatcherRejectsInvalidPattern() {
    compiler.compileMatcher("^[A");
  }
}
