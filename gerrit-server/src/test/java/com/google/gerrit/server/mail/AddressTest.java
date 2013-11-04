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

import org.junit.Test;

import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class AddressTest {
  @Test
  public void testParse_NameEmail1() {
    final Address a = Address.parse("A U Thor <author@example.com>");
    assertEquals("A U Thor", a.name);
    assertEquals("author@example.com", a.email);
  }

  @Test
  public void testParse_NameEmail2() {
    final Address a = Address.parse("A <a@b>");
    assertEquals("A", a.name);
    assertEquals("a@b", a.email);
  }

  @Test
  public void testParse_NameEmail3() {
    final Address a = Address.parse("<a@b>");
    assertNull(a.name);
    assertEquals("a@b", a.email);
  }

  @Test
  public void testParse_NameEmail4() {
    final Address a = Address.parse("A U Thor<author@example.com>");
    assertEquals("A U Thor", a.name);
    assertEquals("author@example.com", a.email);
  }

  @Test
  public void testParse_NameEmail5() {
    final Address a = Address.parse("A U Thor  <author@example.com>");
    assertEquals("A U Thor", a.name);
    assertEquals("author@example.com", a.email);
  }

  @Test
  public void testParse_Email1() {
    final Address a = Address.parse("author@example.com");
    assertNull(a.name);
    assertEquals("author@example.com", a.email);
  }

  @Test
  public void testParse_Email2() {
    final Address a = Address.parse("a@b");
    assertNull(a.name);
    assertEquals("a@b", a.email);
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

  private static void assertInvalid(final String in) {
    try {
      Address.parse(in);
      fail("Incorrectly accepted " + in);
    } catch (IllegalArgumentException e) {
      assertEquals("Invalid email address: " + in, e.getMessage());
    }
  }

  @Test
  public void testToHeaderString_NameEmail1() {
    assertEquals("A <a@a>", format("A", "a@a"));
  }

  @Test
  public void testToHeaderString_NameEmail2() {
    assertEquals("A B <a@a>", format("A B", "a@a"));
  }

  @Test
  public void testToHeaderString_NameEmail3() {
    assertEquals("\"A B. C\" <a@a>", format("A B. C", "a@a"));
  }

  @Test
  public void testToHeaderString_NameEmail4() {
    assertEquals("\"A B, C\" <a@a>", format("A B, C", "a@a"));
  }

  @Test
  public void testToHeaderString_NameEmail5() {
    assertEquals("\"A \\\" C\" <a@a>", format("A \" C", "a@a"));
  }

  @Test
  public void testToHeaderString_NameEmail6() {
    assertEquals("=?UTF-8?Q?A_=E2=82=AC_B?= <a@a>", format("A \u20ac B", "a@a"));
  }

  @Test
  public void testToHeaderString_Email1() {
    assertEquals("a@a", format(null, "a@a"));
  }

  @Test
  public void testToHeaderString_Email2() {
    assertEquals("<a,b@a>", format(null, "a,b@a"));
  }

  private static String format(final String name, final String email) {
    try {
      return new Address(name, email).toHeaderString();
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Cannot encode address", e);
    }
  }
}
