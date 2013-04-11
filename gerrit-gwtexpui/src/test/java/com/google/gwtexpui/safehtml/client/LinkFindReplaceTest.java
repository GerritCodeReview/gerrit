// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gwtexpui.safehtml.client;

import static com.google.gwtexpui.safehtml.client.LinkFindReplace.hasValidScheme;

import junit.framework.TestCase;

public class LinkFindReplaceTest extends TestCase {
  public void testNoEscaping() {
    String find = "find";
    String link = "link";
    LinkFindReplace a = new LinkFindReplace(find, link);
    assertEquals(find, a.pattern().getSource());
    assertEquals("<a href=\"link\">find</a>", a.replace(find));
    assertEquals("find = " + find + ", link = " + link, a.toString());
  }

  public void testBackreference() {
    assertEquals("<a href=\"/bug?id=123\">issue 123</a>",
        new LinkFindReplace("(bug|issue)\\s*([0-9]+)", "/bug?id=$2")
            .replace("issue 123"));
  }

  public void testHasValidScheme() {
    assertTrue(hasValidScheme("/absolute/path"));
    assertTrue(hasValidScheme("relative/path"));
    assertTrue(hasValidScheme("http://url/"));
    assertTrue(hasValidScheme("HTTP://url/"));
    assertTrue(hasValidScheme("https://url/"));
    assertTrue(hasValidScheme("mailto://url/"));
    assertFalse(hasValidScheme("ftp://url/"));
    assertFalse(hasValidScheme("data:evil"));
    assertFalse(hasValidScheme("javascript:alert(1)"));
  }

  public void testInvalidSchemeInReplace() {
    try {
      new LinkFindReplace("find", "javascript:alert(1)").replace("find");
      fail("Expected IllegalStateException");
    } catch (IllegalArgumentException expected) {
    }
  }

  public void testInvalidSchemeWithBackreference() {
    try {
      new LinkFindReplace(".*(script:[^;]*)", "java$1")
          .replace("Look at this script: alert(1);");
      fail("Expected IllegalStateException");
    } catch (IllegalArgumentException expected) {
    }
  }

  public void testReplaceEscaping() {
    assertEquals("<a href=\"a&quot;&amp;&#39;&lt;&gt;b\">find</a>",
        new LinkFindReplace("find", "a\"&'<>b").replace("find"));
  }

  public void testHtmlInFind() {
    String rawFind = "<b>&quot;bold&quot;</b>";
    LinkFindReplace a = new LinkFindReplace(rawFind, "/bold");
    assertEquals(rawFind, a.pattern().getSource());
    assertEquals("<a href=\"/bold\">" + rawFind + "</a>", a.replace(rawFind));
  }
}
