// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "<p>AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gwtexpui.safehtml.client;

import junit.framework.TestCase;

public class SafeHtml_WikifyListTest extends TestCase {
  private static final String BEGIN_LIST = "<ul class=\"wikiList\">";
  private static final String END_LIST = "</ul>";

  private static String item(String raw) {
    return "<li>" + raw + "</li>";
  }

  public void testBulletList1() {
    final SafeHtml o = html("A\n\n* line 1\n* 2nd line");
    final SafeHtml n = o.wikify();
    assertNotSame(o, n);
    assertEquals("<p>A</p>"//
        + BEGIN_LIST //
        + item("line 1") //
        + item("2nd line") //
        + END_LIST //
    , n.asString());
  }

  public void testBulletList2() {
    final SafeHtml o = html("A\n\n* line 1\n* 2nd line\n\nB");
    final SafeHtml n = o.wikify();
    assertNotSame(o, n);
    assertEquals("<p>A</p>"//
        + BEGIN_LIST //
        + item("line 1") //
        + item("2nd line") //
        + END_LIST //
        + "<p>B</p>" //
    , n.asString());
  }

  public void testBulletList3() {
    final SafeHtml o = html("* line 1\n* 2nd line\n\nB");
    final SafeHtml n = o.wikify();
    assertNotSame(o, n);
    assertEquals(BEGIN_LIST //
        + item("line 1") //
        + item("2nd line") //
        + END_LIST //
        + "<p>B</p>" //
    , n.asString());
  }

  public void testBulletList4() {
    final SafeHtml o = html("To see this bug, you have to:\n" //
        + "* Be on IMAP or EAS (not on POP)\n"//
        + "* Be very unlucky\n");
    final SafeHtml n = o.wikify();
    assertNotSame(o, n);
    assertEquals("<p>To see this bug, you have to:</p>" //
        + BEGIN_LIST //
        + item("Be on IMAP or EAS (not on POP)") //
        + item("Be very unlucky") //
        + END_LIST //
    , n.asString());
  }

  public void testBulletList5() {
    final SafeHtml o = html("To see this bug,\n" //
        + "you have to:\n" //
        + "* Be on IMAP or EAS (not on POP)\n"//
        + "* Be very unlucky\n");
    final SafeHtml n = o.wikify();
    assertNotSame(o, n);
    assertEquals("<p>To see this bug, you have to:</p>" //
        + BEGIN_LIST //
        + item("Be on IMAP or EAS (not on POP)") //
        + item("Be very unlucky") //
        + END_LIST //
    , n.asString());
  }

  public void testDashList1() {
    final SafeHtml o = html("A\n\n- line 1\n- 2nd line");
    final SafeHtml n = o.wikify();
    assertNotSame(o, n);
    assertEquals("<p>A</p>"//
        + BEGIN_LIST //
        + item("line 1") //
        + item("2nd line") //
        + END_LIST //
    , n.asString());
  }

  public void testDashList2() {
    final SafeHtml o = html("A\n\n- line 1\n- 2nd line\n\nB");
    final SafeHtml n = o.wikify();
    assertNotSame(o, n);
    assertEquals("<p>A</p>"//
        + BEGIN_LIST //
        + item("line 1") //
        + item("2nd line") //
        + END_LIST //
        + "<p>B</p>" //
    , n.asString());
  }

  public void testDashList3() {
    final SafeHtml o = html("- line 1\n- 2nd line\n\nB");
    final SafeHtml n = o.wikify();
    assertNotSame(o, n);
    assertEquals(BEGIN_LIST //
        + item("line 1") //
        + item("2nd line") //
        + END_LIST //
        + "<p>B</p>" //
    , n.asString());
  }

  private static SafeHtml html(String text) {
    return new SafeHtmlBuilder().append(text).toSafeHtml();
  }
}
