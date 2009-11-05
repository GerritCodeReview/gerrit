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

public class SafeHtmlBuilderTest extends TestCase {
  public void testEmpty() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertTrue(b.isEmpty());
    assertFalse(b.hasContent());
    assertEquals("", b.asString());

    b.append("a");
    assertTrue(b.hasContent());
    assertEquals("a", b.asString());
  }

  public void testToSafeHtml() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    b.append(1);

    final SafeHtml h = b.toSafeHtml();
    assertNotNull(h);
    assertNotSame(h, b);
    assertFalse(h instanceof SafeHtmlBuilder);
    assertEquals("1", h.asString());
  }

  public void testAppend_boolean() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.append(true));
    assertSame(b, b.append(false));
    assertEquals("truefalse", b.asString());
  }

  public void testAppend_char() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.append('a'));
    assertSame(b, b.append('b'));
    assertEquals("ab", b.asString());
  }

  public void testAppend_int() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.append(4));
    assertSame(b, b.append(2));
    assertSame(b, b.append(-100));
    assertEquals("42-100", b.asString());
  }

  public void testAppend_long() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.append(4L));
    assertSame(b, b.append(2L));
    assertEquals("42", b.asString());
  }

  public void testAppend_float() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.append(0.0f));
    assertEquals("0.0", b.asString());
  }

  public void testAppend_double() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.append(0.0));
    assertEquals("0.0", b.asString());
  }

  public void testAppend_String() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.append((String) null));
    assertEquals("", b.asString());
    assertSame(b, b.append("foo"));
    assertSame(b, b.append("bar"));
    assertEquals("foobar", b.asString());
  }

  public void testAppend_StringBuilder() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.append((StringBuilder) null));
    assertEquals("", b.asString());
    assertSame(b, b.append(new StringBuilder("foo")));
    assertSame(b, b.append(new StringBuilder("bar")));
    assertEquals("foobar", b.asString());
  }

  public void testAppend_StringBuffer() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.append((StringBuffer) null));
    assertEquals("", b.asString());
    assertSame(b, b.append(new StringBuffer("foo")));
    assertSame(b, b.append(new StringBuffer("bar")));
    assertEquals("foobar", b.asString());
  }

  public void testAppend_Object() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.append((Object) null));
    assertEquals("", b.asString());
    assertSame(b, b.append(new Object() {
      @Override
      public String toString() {
        return "foobar";
      }
    }));
    assertEquals("foobar", b.asString());
  }

  public void testAppend_CharSequence() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.append((CharSequence) null));
    assertEquals("", b.asString());
    assertSame(b, b.append((CharSequence) "foo"));
    assertSame(b, b.append((CharSequence) "bar"));
    assertEquals("foobar", b.asString());
  }

  public void testAppend_SafeHtml() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.append((SafeHtml) null));
    assertEquals("", b.asString());
    assertSame(b, b.append(new SafeHtmlString("foo")));
    assertSame(b, b.append(new SafeHtmlBuilder().append("bar")));
    assertEquals("foobar", b.asString());
  }

  public void testHtmlSpecialCharacters() {
    assertEquals("&amp;", escape("&"));
    assertEquals("&lt;", escape("<"));
    assertEquals("&gt;", escape(">"));
    assertEquals("&quot;", escape("\""));
    assertEquals("&#39;", escape("'"));

    assertEquals("&amp;", escape('&'));
    assertEquals("&lt;", escape('<'));
    assertEquals("&gt;", escape('>'));
    assertEquals("&quot;", escape('"'));
    assertEquals("&#39;", escape('\''));

    assertEquals("&lt;b&gt;", escape("<b>"));
    assertEquals("&amp;lt;b&amp;gt;", escape("&lt;b&gt;"));
  }

  public void testEntityNbsp() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.nbsp());
    assertEquals("&nbsp;", b.asString());
  }

  public void testTagBr() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.br());
    assertEquals("<br />", b.asString());
  }

  public void testTagTableTrTd() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.openElement("table"));
    assertSame(b, b.openTr());
    assertSame(b, b.openTd());
    assertSame(b, b.append("d<a>ta"));
    assertSame(b, b.closeTd());
    assertSame(b, b.closeTr());
    assertSame(b, b.closeElement("table"));
    assertEquals("<table><tr><td>d&lt;a&gt;ta</td></tr></table>", b.asString());
  }

  public void testTagDiv() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.openDiv());
    assertSame(b, b.append("d<a>ta"));
    assertSame(b, b.closeDiv());
    assertEquals("<div>d&lt;a&gt;ta</div>", b.asString());
  }

  public void testTagAnchor() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.openAnchor());

    assertEquals("", b.getAttribute("href"));
    assertSame(b, b.setAttribute("href", "http://here"));
    assertEquals("http://here", b.getAttribute("href"));
    assertSame(b, b.setAttribute("href", "d<a>ta"));
    assertEquals("d<a>ta", b.getAttribute("href"));

    assertEquals("", b.getAttribute("target"));
    assertSame(b, b.setAttribute("target", null));
    assertEquals("", b.getAttribute("target"));

    assertSame(b, b.append("go"));
    assertSame(b, b.closeAnchor());
    assertEquals("<a href=\"d&lt;a&gt;ta\">go</a>", b.asString());
  }

  public void testTagHeightWidth() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.openElement("img"));
    assertSame(b, b.setHeight(100));
    assertSame(b, b.setWidth(42));
    assertSame(b, b.closeSelf());
    assertEquals("<img height=\"100\" width=\"42\" />", b.asString());
  }

  public void testStyleName() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertSame(b, b.openSpan());
    assertSame(b, b.setStyleName("foo"));
    assertSame(b, b.addStyleName("bar"));
    assertSame(b, b.append("d<a>ta"));
    assertSame(b, b.closeSpan());
    assertEquals("<span class=\"foo bar\">d&lt;a&gt;ta</span>", b.asString());
  }

  public void testRejectJavaScript_AnchorHref() {
    final String href = "javascript:window.close();";
    try {
      new SafeHtmlBuilder().openAnchor().setAttribute("href", href);
      fail("accepted javascript in a href");
    } catch (RuntimeException e) {
      assertEquals("javascript unsafe in href: " + href, e.getMessage());
    }
  }

  public void testRejectJavaScript_ImgSrc() {
    final String href = "javascript:window.close();";
    try {
      new SafeHtmlBuilder().openElement("img").setAttribute("src", href);
      fail("accepted javascript in img src");
    } catch (RuntimeException e) {
      assertEquals("javascript unsafe in href: " + href, e.getMessage());
    }
  }

  public void testRejectJavaScript_FormAction() {
    final String href = "javascript:window.close();";
    try {
      new SafeHtmlBuilder().openElement("form").setAttribute("action", href);
      fail("accepted javascript in form action");
    } catch (RuntimeException e) {
      assertEquals("javascript unsafe in href: " + href, e.getMessage());
    }
  }

  private static String escape(final char c) {
    return new SafeHtmlBuilder().append(c).asString();
  }

  private static String escape(final String c) {
    return new SafeHtmlBuilder().append(c).asString();
  }
}
