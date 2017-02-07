// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.base.Strings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HashedPasswordTest {

  @Test
  public void encodeOneLine() throws Exception {
    String password = "secret";
    HashedPassword hpw = HashedPassword.fromPassword(password);
    assertThat(hpw.encode()).doesNotContain("\n");
  }

  @Test
  public void encodeDecode() throws Exception {
    String password = "secret";
    HashedPassword hpw = HashedPassword.fromPassword(password);
    HashedPassword roundtrip = HashedPassword.decode(hpw.encode());
    assertThat(hpw.encode()).isEqualTo(roundtrip.encode());
  }

  @Test
  public void lengthLimit() throws Exception {
    String pw = Strings.repeat("1", 70);
    assertThat(HashedPassword.fromPassword(pw).encode().length()).isLessThan(255);
  }

  @Test
  public void basicFunctionality() throws Exception {
    String password = "secret";
    HashedPassword hpw = HashedPassword.fromPassword(password);

    assertThat(hpw.checkPassword("false")).isFalse();
    assertThat(hpw.checkPassword(password)).isTrue();
  }
}
