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

package com.google.gerrit.util.crypto;

import static com.google.common.truth.Truth.assertThat;

import java.util.regex.Pattern;
import org.junit.Test;

public class SecureRandomUtilTest {
  private static final Pattern BASE64URL = Pattern.compile("[A-Za-z0-9_-]+");

  @Test
  public void newBytesReturnsRequestedLength() {
    assertThat(SecureRandomUtil.newBytes(1)).hasLength(1);
    assertThat(SecureRandomUtil.newBytes(16)).hasLength(16);
    assertThat(SecureRandomUtil.newBytes(32)).hasLength(32);
  }

  @Test
  public void newBytesOfZeroIsEmpty() {
    assertThat(SecureRandomUtil.newBytes(0)).hasLength(0);
  }

  @Test
  public void newBytesReturnsDifferentValues() {
    assertThat(SecureRandomUtil.newBytes(32)).isNotEqualTo(SecureRandomUtil.newBytes(32));
  }

  @Test
  public void newRandomStringIsUrlSafeAndUnpadded() {
    String s = SecureRandomUtil.newRandomString(32);
    assertThat(s).matches(BASE64URL);
    assertThat(s).doesNotContain("=");
  }

  @Test
  public void newRandomStringLengthExpandsByFourThirds() {
    // base64url is unpadded: ceil(n * 4 / 3) chars. 48 bytes -> 64 chars, the
    // size the signed-push seed relies on.
    assertThat(SecureRandomUtil.newRandomString(48)).hasLength(64);
    assertThat(SecureRandomUtil.newRandomString(48)).matches(BASE64URL);
  }
}
