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

package com.google.gwtexpui.safehtml.client;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

public class SafeHtml_ReplaceTest extends TestCase {
  public void testReplaceOneLink() {
    SafeHtml o = html("A\nissue 42\nB");
    SafeHtml n = o.replaceAll(repls(
        new RawFindReplace("(issue\\s(\\d+))", "<a href=\"?$2\">$1</a>")));
    assertNotSame(o, n);
    assertEquals("A\n<a href=\"?42\">issue 42</a>\nB", n.asString());
  }

  public void testReplaceNoLeadingOrTrailingText() {
    SafeHtml o = html("issue 42");
    SafeHtml n = o.replaceAll(repls(
        new RawFindReplace("(issue\\s(\\d+))", "<a href=\"?$2\">$1</a>")));
    assertNotSame(o, n);
    assertEquals("<a href=\"?42\">issue 42</a>", n.asString());
  }

  public void testReplaceTwoLinks() {
    SafeHtml o = html("A\nissue 42\nissue 9918\nB");
    SafeHtml n = o.replaceAll(repls(
        new RawFindReplace("(issue\\s(\\d+))", "<a href=\"?$2\">$1</a>")));
    assertNotSame(o, n);
    assertEquals("A\n"
        + "<a href=\"?42\">issue 42</a>\n"
        + "<a href=\"?9918\">issue 9918</a>\n"
        + "B"
    , n.asString());
  }

  public void testReplaceInOrder() {
    SafeHtml o = html("A\nissue 42\nReally GWTEXPUI-9918 is better\nB");
    SafeHtml n = o.replaceAll(repls(
        new RawFindReplace("(GWTEXPUI-(\\d+))",
            "<a href=\"gwtexpui-bug?$2\">$1</a>"),
        new RawFindReplace("(issue\\s+(\\d+))",
            "<a href=\"generic-bug?$2\">$1</a>")));
    assertNotSame(o, n);
    assertEquals("A\n"
        + "<a href=\"generic-bug?42\">issue 42</a>\n"
        + "Really <a href=\"gwtexpui-bug?9918\">GWTEXPUI-9918</a> is better\n"
        + "B"
    , n.asString());
  }

  public void testReplaceOverlappingAfterFirstChar() {
    SafeHtml o = html("abcd");
    RawFindReplace ab = new RawFindReplace("ab", "AB");
    RawFindReplace bc = new RawFindReplace("bc", "23");
    RawFindReplace cd = new RawFindReplace("cd", "YZ");

    assertEquals("ABcd", o.replaceAll(repls(ab, bc)).asString());
    assertEquals("ABcd", o.replaceAll(repls(bc, ab)).asString());
    assertEquals("ABYZ", o.replaceAll(repls(ab, bc, cd)).asString());
  }

  public void testReplaceOverlappingAtFirstCharLongestMatch() {
    SafeHtml o = html("abcd");
    RawFindReplace ab = new RawFindReplace("ab", "AB");
    RawFindReplace abc = new RawFindReplace("[^d][^d][^d]", "234");

    assertEquals("ABcd", o.replaceAll(repls(ab, abc)).asString());
    assertEquals("234d", o.replaceAll(repls(abc, ab)).asString());
  }

  public void testReplaceOverlappingAtFirstCharFirstMatch() {
    SafeHtml o = html("abcd");
    RawFindReplace ab1 = new RawFindReplace("ab", "AB");
    RawFindReplace ab2 = new RawFindReplace("[^cd][^cd]", "12");

    assertEquals("ABcd", o.replaceAll(repls(ab1, ab2)).asString());
    assertEquals("12cd", o.replaceAll(repls(ab2, ab1)).asString());
  }

  public void testFailedSanitization() {
    SafeHtml o = html("abcd");
    LinkFindReplace evil = new LinkFindReplace("(b)", "javascript:alert('$1')");
    LinkFindReplace ok = new LinkFindReplace("(b)", "/$1");
    assertEquals("abcd", o.replaceAll(repls(evil)).asString());
    String linked = "a<a href=\"/b\">b</a>cd";
    assertEquals(linked, o.replaceAll(repls(ok)).asString());
    assertEquals(linked, o.replaceAll(repls(evil, ok)).asString());
  }

  private static SafeHtml html(String text) {
    return new SafeHtmlBuilder().append(text).toSafeHtml();
  }

  private static List<FindReplace> repls(FindReplace... repls) {
    return Arrays.asList(repls);
  }
}
