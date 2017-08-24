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

package com.google.gerrit.pgm.http.jetty;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class HttpLogRedactTest {
  @Test
  public void includeQueryString() {
    assertThat(HttpLog.redactQueryString("/changes/", null)).isEqualTo("/changes/");
    assertThat(HttpLog.redactQueryString("/changes/", "")).isEqualTo("/changes/");
    assertThat(HttpLog.redactQueryString("/changes/", "x")).isEqualTo("/changes/?x");
    assertThat(HttpLog.redactQueryString("/changes/", "x=y")).isEqualTo("/changes/?x=y");
  }

  @Test
  public void redactAuth() {
    assertThat(HttpLog.redactQueryString("/changes/", "query=status:open"))
        .isEqualTo("/changes/?query=status:open");

    assertThat(HttpLog.redactQueryString("/changes/", "query=status:open&access_token=foo"))
        .isEqualTo("/changes/?query=status:open&access_token=*");

    assertThat(HttpLog.redactQueryString("/changes/", "access_token=foo"))
        .isEqualTo("/changes/?access_token=*");

    assertThat(
            HttpLog.redactQueryString(
                "/changes/", "query=status:open&access_token=foo&access_token=bar"))
        .isEqualTo("/changes/?query=status:open&access_token=*&access_token=*");
  }
}
