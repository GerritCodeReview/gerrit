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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.UnsupportedEncodingException;

public class AddressTest {
  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Test
  public void testParse_NameEmail1() {
    final Address a = Address.parse("A U Thor <author@example.com>");
    assertThat(a.name).isEqualTo("A U Thor");
    assertThat(a.email).isEqualTo("author@example.com");
  }

  @Test
  public void testParse_NameEmail2() {
    final Address a = Address.parse("A <a@b>");
    assertThat(a.name).isEqualTo("A");
    assertThat(a.email).isEqualTo("a@b");
  }

  @Test
  public void testParse_NameEmail3() {
    final Address a = Address.parse("<a@b>");
    assertThat(a.name).isNull();
    assertThat(a.email).isEqualTo("a@b");
  }

  @Test
  public void testParse_NameEmail4() {
    final Address a = Address.parse("A U Thor<author@example.com>");
    assertThat(a.name).isEqualTo("A U Thor");
    assertThat(a.email).isEqualTo("author@example.com");
  }

  @Test
  public void testParse_NameEmail5() {
    final Address a = Address.parse("A U Thor  <author@example.com>");
    assertThat(a.name).isEqualTo("A U Thor");
    assertThat(a.email).isEqualTo("author@example.com");
  }

  @Test
  public void testParse_Email1() {
    final Address a = Address.parse("author@example.com");
    assertThat(a.name).isNull();
    assertThat(a.email).isEqualTo("author@example.com");
  }

  @Test
  public void testParse_Email2() {
    final Address a = Address.parse("a@b");
    assertThat(a.name).isNull();
    assertThat(a.email).isEqualTo("a@b");
  }

  @Test
  public void testParse_NewTLD() {
    Address a = Address.parse("A U Thor <author@example.systems>");
    assertThat(a.name).isEqualTo("A U Thor");
    assertThat(a.email).isEqualTo("author@example.systems");
  }

  @Test
  public void testParseInvalid() {
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

  private void assertInvalid(final String in) {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Invalid email address: " + in);
    Address.parse(in);
  }

  @Test
  public void testToHeaderString_NameEmail1() {
    assertThat(format("A", "a@a")).isEqualTo("A <a@a>");
  }

  @Test
  public void testToHeaderString_NameEmail2() {
    assertThat(format("A B", "a@a")).isEqualTo("A B <a@a>");
  }

  @Test
  public void testToHeaderString_NameEmail3() {
    assertThat(format("A B. C", "a@a")).isEqualTo("\"A B. C\" <a@a>");
  }

  @Test
  public void testToHeaderString_NameEmail4() {
    assertThat(format("A B, C", "a@a")).isEqualTo("\"A B, C\" <a@a>");
  }

  @Test
  public void testToHeaderString_NameEmail5() {
    assertThat(format("A \" C", "a@a")).isEqualTo("\"A \\\" C\" <a@a>");
  }

  @Test
  public void testToHeaderString_NameEmail6() {
    assertThat(format("A \u20ac B", "a@a"))
      .isEqualTo("=?UTF-8?Q?A_=E2=82=AC_B?= <a@a>");
  }

  @Test
  public void testToHeaderString_NameEmail7() {
    assertThat(format("A \u20ac B (Code Review)", "a@a"))
      .isEqualTo("=?UTF-8?Q?A_=E2=82=AC_B_=28Code_Review=29?= <a@a>");
  }

  @Test
  public void testToHeaderString_Email1() {
    assertThat(format(null, "a@a")).isEqualTo("a@a");
  }

  @Test
  public void testToHeaderString_Email2() {
    assertThat(format(null, "a,b@a")).isEqualTo("<a,b@a>");
  }

  private static String format(final String name, final String email) {
    try {
      return new Address(name, email).toHeaderString();
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Cannot encode address", e);
    }
  }
}
