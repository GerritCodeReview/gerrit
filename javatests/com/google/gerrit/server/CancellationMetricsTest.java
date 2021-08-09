// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class CancellationMetricsTest {
  @Test
  public void redactRequestUri() throws Exception {
    // test with valid request URIs
    assertThat(redact("/")).isEqualTo("/");
    assertThat(redact("/changes")).isEqualTo("/changes");
    assertThat(redact("/changes/")).isEqualTo("/changes/");
    assertThat(redact("/changes/123")).isEqualTo("/changes/*");
    assertThat(redact("/changes/123/detail")).isEqualTo("/changes/*/detail");
    assertThat(redact("/changes/123/detail/")).isEqualTo("/changes/*/detail/");
    assertThat(redact("/foo/123/bar/567")).isEqualTo("/foo/*/bar/*");
    assertThat(redact("/foo/123/bar/567/baz")).isEqualTo("/foo/*/bar/*/baz");
    assertThat(redact("/foo/123/bar/567/baz/")).isEqualTo("/foo/*/bar/*/baz/");
    assertThat(redact("/foo/123/bar/567/baz/890")).isEqualTo("/foo/*/bar/*/baz/*");
    assertThat(redact("changes")).isEqualTo("changes");
    assertThat(redact("changes/")).isEqualTo("changes/");
    assertThat(redact("changes/123")).isEqualTo("changes/*");
    assertThat(redact("changes/123/detail")).isEqualTo("changes/*/detail");
    assertThat(redact("changes/123/detail/")).isEqualTo("changes/*/detail/");
    assertThat(redact("foo/123/bar/567")).isEqualTo("foo/*/bar/*");
    assertThat(redact("foo/123/bar/567/baz")).isEqualTo("foo/*/bar/*/baz");
    assertThat(redact("foo/123/bar/567/baz/")).isEqualTo("foo/*/bar/*/baz/");
    assertThat(redact("foo/123/bar/567/baz/890")).isEqualTo("foo/*/bar/*/baz/*");

    // test with invalid request URIs
    assertThat(redact("")).isEqualTo("");
    assertThat(redact("//")).isEqualTo("//");
    assertThat(redact("///")).isEqualTo("///");
    assertThat(redact("/changes//detail")).isEqualTo("/changes//detail");
    assertThat(redact("//123/detail")).isEqualTo("//*/detail");
  }

  public static String redact(String uri) {
    return CancellationMetrics.redactRequestUri(uri);
  }
}
