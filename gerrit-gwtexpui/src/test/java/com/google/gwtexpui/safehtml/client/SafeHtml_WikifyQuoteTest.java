// Copyright (C) 2013 The Android Open Source Project
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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class SafeHtml_WikifyQuoteTest {
  private static final String B = "<blockquote class=\"wikiQuote\">";
  private static final String E = "</blockquote>";

  private static String quote(String raw) {
    return B + raw + E;
  }

  @Test
  public void testQuote1() {
    final SafeHtml o = html("> I'm happy\n > with quotes!\n\nSee above.");
    final SafeHtml n = o.wikify();
    assertNotSame(o, n);
    assertEquals(
        quote("I&#39;m happy\nwith quotes!")
        + "<p>See above.</p>",
      n.asString());
  }

  @Test
  public void testQuote2() {
    final SafeHtml o = html("See this said:\n\n > a quoted\n > string block\n\nOK?");
    final SafeHtml n = o.wikify();
    assertNotSame(o, n);
    assertEquals(
        "<p>See this said:</p>"
        + quote("a quoted\nstring block")
        + "<p>OK?</p>",
      n.asString());
  }

  @Test
  public void testNestedQuotes1() {
    final SafeHtml o = html(" > > prior\n > \n > next\n");
    final SafeHtml n = o.wikify();
    assertEquals(
      quote(quote("prior") + "next\n"),
      n.asString());
  }

  private static SafeHtml html(String text) {
    return new SafeHtmlBuilder().append(text).toSafeHtml();
  }
}
