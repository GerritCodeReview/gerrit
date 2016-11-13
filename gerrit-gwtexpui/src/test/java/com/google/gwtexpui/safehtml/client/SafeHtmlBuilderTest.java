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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SafeHtmlBuilderTest {
  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void testEmpty() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b.isEmpty()).isTrue();
    assertThat(b.hasContent()).isFalse();
    assertThat(b.asString()).isEmpty();

    b.append("a");
    assertThat(b.hasContent()).isTrue();
    assertThat(b.asString()).isEqualTo("a");
  }

  @Test
  public void testToSafeHtml() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    b.append(1);

    final SafeHtml h = b.toSafeHtml();
    assertThat(h).isNotNull();
    assertThat(h).isNotSameAs(b);
    assertThat(h).isNotInstanceOf(SafeHtmlBuilder.class);
    assertThat(h.asString()).isEqualTo("1");
  }

  @Test
  public void testAppend_boolean() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.append(true));
    assertThat(b).isSameAs(b.append(false));
    assertThat(b.asString()).isEqualTo("truefalse");
  }

  @Test
  public void testAppend_char() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.append('a'));
    assertThat(b).isSameAs(b.append('b'));
    assertThat(b.asString()).isEqualTo("ab");
  }

  @Test
  public void testAppend_int() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.append(4));
    assertThat(b).isSameAs(b.append(2));
    assertThat(b).isSameAs(b.append(-100));
    assertThat(b.asString()).isEqualTo("42-100");
  }

  @Test
  public void testAppend_long() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.append(4L));
    assertThat(b).isSameAs(b.append(2L));
    assertThat(b.asString()).isEqualTo("42");
  }

  @Test
  public void testAppend_float() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.append(0.0f));
    assertThat(b.asString()).isEqualTo("0.0");
  }

  @Test
  public void testAppend_double() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.append(0.0));
    assertThat(b.asString()).isEqualTo("0.0");
  }

  @Test
  public void testAppend_String() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.append((String) null));
    assertThat(b.asString()).isEmpty();
    assertThat(b).isSameAs(b.append("foo"));
    assertThat(b).isSameAs(b.append("bar"));
    assertThat(b.asString()).isEqualTo("foobar");
  }

  @Test
  public void testAppend_StringBuilder() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.append((StringBuilder) null));
    assertThat(b.asString()).isEmpty();
    assertThat(b).isSameAs(b.append(new StringBuilder("foo")));
    assertThat(b).isSameAs(b.append(new StringBuilder("bar")));
    assertThat(b.asString()).isEqualTo("foobar");
  }

  @Test
  public void testAppend_StringBuffer() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.append((StringBuffer) null));
    assertThat(b.asString()).isEmpty();
    assertThat(b).isSameAs(b.append(new StringBuffer("foo")));
    assertThat(b).isSameAs(b.append(new StringBuffer("bar")));
    assertThat(b.asString()).isEqualTo("foobar");
  }

  @Test
  public void testAppend_Object() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.append((Object) null));
    assertThat(b.asString()).isEmpty();
    assertThat(b)
        .isSameAs(
            b.append(
                new Object() {
                  @Override
                  public String toString() {
                    return "foobar";
                  }
                }));
    assertThat(b.asString()).isEqualTo("foobar");
  }

  @Test
  public void testAppend_CharSequence() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.append((CharSequence) null));
    assertThat(b.asString()).isEmpty();
    assertThat(b).isSameAs(b.append((CharSequence) "foo"));
    assertThat(b).isSameAs(b.append((CharSequence) "bar"));
    assertThat(b.asString()).isEqualTo("foobar");
  }

  @Test
  public void testAppend_SafeHtml() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.append((SafeHtml) null));
    assertThat(b.asString()).isEmpty();
    assertThat(b).isSameAs(b.append(new SafeHtmlString("foo")));
    assertThat(b).isSameAs(b.append(new SafeHtmlBuilder().append("bar")));
    assertThat(b.asString()).isEqualTo("foobar");
  }

  @Test
  public void testHtmlSpecialCharacters() {
    assertThat(escape("&")).isEqualTo("&amp;");
    assertThat(escape("<")).isEqualTo("&lt;");
    assertThat(escape(">")).isEqualTo("&gt;");
    assertThat(escape("\"")).isEqualTo("&quot;");
    assertThat(escape("'")).isEqualTo("&#39;");

    assertThat(escape('&')).isEqualTo("&amp;");
    assertThat(escape('<')).isEqualTo("&lt;");
    assertThat(escape('>')).isEqualTo("&gt;");
    assertThat(escape('"')).isEqualTo("&quot;");
    assertThat(escape('\'')).isEqualTo("&#39;");

    assertThat(escape("<b>")).isEqualTo("&lt;b&gt;");
    assertThat(escape("&lt;b&gt;")).isEqualTo("&amp;lt;b&amp;gt;");
  }

  @Test
  public void testEntityNbsp() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.nbsp());
    assertThat(b.asString()).isEqualTo("&nbsp;");
  }

  @Test
  public void testTagBr() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.br());
    assertThat(b.asString()).isEqualTo("<br />");
  }

  @Test
  public void testTagTableTrTd() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.openElement("table"));
    assertThat(b).isSameAs(b.openTr());
    assertThat(b).isSameAs(b.openTd());
    assertThat(b).isSameAs(b.append("d<a>ta"));
    assertThat(b).isSameAs(b.closeTd());
    assertThat(b).isSameAs(b.closeTr());
    assertThat(b).isSameAs(b.closeElement("table"));
    assertThat(b.asString()).isEqualTo("<table><tr><td>d&lt;a&gt;ta</td></tr></table>");
  }

  @Test
  public void testTagDiv() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.openDiv());
    assertThat(b).isSameAs(b.append("d<a>ta"));
    assertThat(b).isSameAs(b.closeDiv());
    assertThat(b.asString()).isEqualTo("<div>d&lt;a&gt;ta</div>");
  }

  @Test
  public void testTagAnchor() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.openAnchor());

    assertThat(b.getAttribute("href")).isEmpty();
    assertThat(b).isSameAs(b.setAttribute("href", "http://here"));
    assertThat(b.getAttribute("href")).isEqualTo("http://here");
    assertThat(b).isSameAs(b.setAttribute("href", "d<a>ta"));
    assertThat(b.getAttribute("href")).isEqualTo("d<a>ta");

    assertThat(b.getAttribute("target")).isEmpty();
    assertThat(b).isSameAs(b.setAttribute("target", null));
    assertThat(b.getAttribute("target")).isEmpty();

    assertThat(b).isSameAs(b.append("go"));
    assertThat(b).isSameAs(b.closeAnchor());
    assertThat(b.asString()).isEqualTo("<a href=\"d&lt;a&gt;ta\">go</a>");
  }

  @Test
  public void testTagHeightWidth() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.openElement("img"));
    assertThat(b).isSameAs(b.setHeight(100));
    assertThat(b).isSameAs(b.setWidth(42));
    assertThat(b).isSameAs(b.closeSelf());
    assertThat(b.asString()).isEqualTo("<img height=\"100\" width=\"42\" />");
  }

  @Test
  public void testStyleName() {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    assertThat(b).isSameAs(b.openSpan());
    assertThat(b).isSameAs(b.setStyleName("foo"));
    assertThat(b).isSameAs(b.addStyleName("bar"));
    assertThat(b).isSameAs(b.append("d<a>ta"));
    assertThat(b).isSameAs(b.closeSpan());
    assertThat(b.asString()).isEqualTo("<span class=\"foo bar\">d&lt;a&gt;ta</span>");
  }

  @Test
  public void testRejectJavaScript_AnchorHref() {
    final String href = "javascript:window.close();";
    exception.expect(RuntimeException.class);
    exception.expectMessage("javascript unsafe in href: " + href);
    new SafeHtmlBuilder().openAnchor().setAttribute("href", href);
  }

  @Test
  public void testRejectJavaScript_ImgSrc() {
    final String href = "javascript:window.close();";
    exception.expect(RuntimeException.class);
    exception.expectMessage("javascript unsafe in href: " + href);
    new SafeHtmlBuilder().openElement("img").setAttribute("src", href);
  }

  @Test
  public void testRejectJavaScript_FormAction() {
    final String href = "javascript:window.close();";
    exception.expect(RuntimeException.class);
    exception.expectMessage("javascript unsafe in href: " + href);
    new SafeHtmlBuilder().openElement("form").setAttribute("action", href);
  }

  private static String escape(final char c) {
    return new SafeHtmlBuilder().append(c).asString();
  }

  private static String escape(final String c) {
    return new SafeHtmlBuilder().append(c).asString();
  }
}
