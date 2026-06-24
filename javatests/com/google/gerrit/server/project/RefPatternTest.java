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

package com.google.gerrit.server.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class RefPatternTest {

  @Test
  public void testNoPatterns() {
    assertThat(RefPattern.matches("refs/heads/master", null)).isTrue();
    assertThat(RefPattern.matches("refs/heads/master", ImmutableList.of())).isTrue();
  }

  @Test
  public void testOnlyPositiveMatch() {
    assertThat(RefPattern.matches("refs/heads/master", ImmutableList.of("refs/heads/*"))).isTrue();
  }

  @Test
  public void testOnlyPositiveNoMatch() {
    assertThat(RefPattern.matches("refs/heads/master", ImmutableList.of("refs/tags/*"))).isFalse();
  }

  @Test
  public void testOnlyNegativeMatch() {
    assertThat(RefPattern.matches("refs/heads/master", ImmutableList.of("-refs/heads/*")))
        .isFalse();
  }

  @Test
  public void testOnlyNegativeNoMatch() {
    // If there are only negative patterns, and none match, it should return true.
    assertThat(RefPattern.matches("refs/heads/master", ImmutableList.of("-refs/tags/*"))).isTrue();
  }

  @Test
  public void testPositiveAndNegativeMatchNegative() {
    assertThat(
            RefPattern.matches(
                "refs/heads/master", ImmutableList.of("refs/heads/*", "-refs/heads/master")))
        .isFalse();
  }

  @Test
  public void testPositiveAndNegativeMatchPositiveOnly() {
    assertThat(
            RefPattern.matches(
                "refs/heads/master", ImmutableList.of("refs/heads/*", "-refs/heads/release")))
        .isTrue();
  }

  @Test
  public void testPositiveAndNegativeMatchNone() {
    assertThat(
            RefPattern.matches(
                "refs/heads/master", ImmutableList.of("refs/tags/*", "-refs/heads/release")))
        .isFalse();
  }
}
