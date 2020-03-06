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

package com.google.gerrit.server.account;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.common.base.Strings;
import org.apache.commons.codec.DecoderException;
import org.junit.Test;

public class HashedPasswordTest {

  @Test
  public void encodeOneLine() throws Exception {
    String password = "secret";
    HashedPassword hashed = HashedPassword.fromPassword(password);
    assertThat(hashed.encode()).doesNotContain("\n");
    assertThat(hashed.encode()).doesNotContain("\r");
  }

  @Test
  public void encodeDecode() throws Exception {
    String password = "secret";
    HashedPassword hashed = HashedPassword.fromPassword(password);
    HashedPassword roundtrip = HashedPassword.decode(hashed.encode());
    assertThat(hashed.encode()).isEqualTo(roundtrip.encode());
    assertThat(roundtrip.checkPassword(password)).isTrue();
    assertThat(roundtrip.checkPassword(password + password)).isFalse();
    assertThat(roundtrip.checkPassword("not the password")).isFalse();
  }

  @Test(expected = DecoderException.class)
  public void invalidDecode() throws Exception {
    HashedPassword.decode("invalid");
  }

  @Test
  public void lengthLimit() throws Exception {
    String password = "";
    HashedPassword hashed = HashedPassword.fromPassword(password);
    assertThat(hashed.encode().length()).isLessThan(255);

    assertThat(hashed.checkPassword("\0")).isTrue();
    assertThat(hashed.checkPassword("a\0")).isFalse();

    password = Strings.repeat("1", 71);
    hashed = HashedPassword.fromPassword(password);

    String passwordTrailingZero = password + "\0";
    assertThat(hashed.checkPassword(passwordTrailingZero)).isTrue();
    assertThat(hashed.encode().length()).isLessThan(255);

    try {
      password = Strings.repeat("1", 72);
      hashed = HashedPassword.fromPassword(password);
      assert_().fail("expected exception");
    } catch (java.lang.IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().contains("BCrypt password must be <= 72 bytes");
    }
  }

  @Test
  public void basicFunctionality() throws Exception {
    String password = "secret";
    HashedPassword hashed = HashedPassword.fromPassword(password);

    assertThat(hashed.checkPassword("false")).isFalse();
    assertThat(hashed.checkPassword(password)).isTrue();
  }
}
