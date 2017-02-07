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
import static org.junit.Assert.fail;

import com.google.common.base.Strings;
import org.apache.commons.codec.DecoderException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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
    assertThat(roundtrip.checkPassword("not the password")).isFalse();
  }

  @Test
  public void invalidDecode() throws Exception {
    try {
      HashedPassword.decode("invalid");
      fail("expect DecoderException");
    } catch (DecoderException e) {
      // everything OK.
    }
  }

  @Test
  public void lengthLimit() throws Exception {
    String password = Strings.repeat("1", 72);

    // make sure it fits in varchar(255).
    assertThat(HashedPassword.fromPassword(password).encode().length()).isLessThan(255);
  }

  @Test
  public void basicFunctionality() throws Exception {
    String password = "secret";
    HashedPassword hashed = HashedPassword.fromPassword(password);

    assertThat(hashed.checkPassword("false")).isFalse();
    assertThat(hashed.checkPassword(password)).isTrue();
  }
}
