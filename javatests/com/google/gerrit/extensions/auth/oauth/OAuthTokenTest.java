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

package com.google.gerrit.extensions.auth.oauth;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OAuthTokenTest {
  @Test
  public void toStringRedactsCredentials() {
    OAuthToken token =
        new OAuthToken(
            "access-token-value", "secret-value", "raw-response-value", 1234L, "provider-x");
    String s = token.toString();

    // The three credential fields must never appear (raw may carry a refresh token).
    assertThat(s).doesNotContain("access-token-value");
    assertThat(s).doesNotContain("secret-value");
    assertThat(s).doesNotContain("raw-response-value");
    assertThat(s).contains("redacted");
    // Non-sensitive fields are kept for diagnostics.
    assertThat(s).contains("1234");
    assertThat(s).contains("provider-x");
  }
}
