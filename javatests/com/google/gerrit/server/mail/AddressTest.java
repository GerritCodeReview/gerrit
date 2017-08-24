// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.mail;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.gerrit.testutil.GerritBaseTests;
import org.junit.Test;

public class AddressTest extends GerritBaseTests {
  @Test
  public void parse_NameEmail1() {
    final Address a = Address.parse("A U Thor <author@example.com>");
    assertThat(a.name).isEqualTo("A U Thor");
    assertThat(a.email).isEqualTo("author@example.com");
  }

  @Test
  public void parse_NameEmail2() {
    final Address a = Address.parse("A <a@b>");
    assertThat(a.name).isEqualTo("A");
    assertThat(a.email).isEqualTo("a@b");
  }

  @Test
  public void parse_NameEmail3() {
    final Address a = Address.parse("<a@b>");
    assertThat(a.name).isNull();
    assertThat(a.email).isEqualTo("a@b");
  }

  @Test
  public void parse_NameEmail4() {
    final Address a = Address.parse("A U Thor<author@example.com>");
    assertThat(a.name).isEqualTo("A U Thor");
    assertThat(a.email).isEqualTo("author@example.com");
  }

  @Test
  public void parse_NameEmail5() {
    final Address a = Address.parse("A U Thor  <author@example.com>");
    assertThat(a.name).isEqualTo("A U Thor");
    assertThat(a.email).isEqualTo("author@example.com");
  }

  @Test
  public void parse_Email1() {
    final Address a = Address.parse("author@example.com");
    assertThat(a.name).isNull();
    assertThat(a.email).isEqualTo("author@example.com");
  }

  @Test
  public void parse_Email2() {
    final Address a = Address.parse("a@b");
    assertThat(a.name).isNull();
    assertThat(a.email).isEqualTo("a@b");
  }

  @Test
  public void parse_NewTLD() {
    Address a = Address.parse("A U Thor <author@example.systems>");
    assertThat(a.name).isEqualTo("A U Thor");
    assertThat(a.email).isEqualTo("author@example.systems");
  }

  @Test
  public void parseInvalid() {
    assertInvalid("");
    assertInvalid("a");
    assertInvalid("a<");
    assertInvalid("<a");
    assertInvalid("<a>");
    assertInvalid("a<a>");
    assertInvalid("a <a>");

    assertInvalid("a");
    assertInvalid("a<@");
    assertInvalid("<a@");
    assertInvalid("<a@>");
    assertInvalid("a<a@>");
    assertInvalid("a <a@>");
    assertInvalid("a <@a>");
  }

  private void assertInvalid(String in) {
    try {
      Address.parse(in);
      fail("Expected IllegalArgumentException for " + in);
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).isEqualTo("Invalid email address: " + in);
    }
  }

  @Test
  public void toHeaderString_NameEmail1() {
    assertThat(format("A", "a@a")).isEqualTo("A <a@a>");
  }

  @Test
  public void toHeaderString_NameEmail2() {
    assertThat(format("A B", "a@a")).isEqualTo("A B <a@a>");
  }

  @Test
  public void toHeaderString_NameEmail3() {
    assertThat(format("A B. C", "a@a")).isEqualTo("\"A B. C\" <a@a>");
  }

  @Test
  public void toHeaderString_NameEmail4() {
    assertThat(format("A B, C", "a@a")).isEqualTo("\"A B, C\" <a@a>");
  }

  @Test
  public void toHeaderString_NameEmail5() {
    assertThat(format("A \" C", "a@a")).isEqualTo("\"A \\\" C\" <a@a>");
  }

  @Test
  public void toHeaderString_NameEmail6() {
    assertThat(format("A \u20ac B", "a@a")).isEqualTo("=?UTF-8?Q?A_=E2=82=AC_B?= <a@a>");
  }

  @Test
  public void toHeaderString_NameEmail7() {
    assertThat(format("A \u20ac B (Code Review)", "a@a"))
        .isEqualTo("=?UTF-8?Q?A_=E2=82=AC_B_=28Code_Review=29?= <a@a>");
  }

  @Test
  public void toHeaderString_Email1() {
    assertThat(format(null, "a@a")).isEqualTo("a@a");
  }

  @Test
  public void toHeaderString_Email2() {
    assertThat(format(null, "a,b@a")).isEqualTo("<a,b@a>");
  }

  private static String format(String name, String email) {
    return new Address(name, email).toHeaderString();
  }
}
