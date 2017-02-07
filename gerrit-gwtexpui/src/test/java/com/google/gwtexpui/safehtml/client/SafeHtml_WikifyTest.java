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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class SafeHtml_WikifyTest {
  @Test
  public void testWikify_OneLine1() {
    final SafeHtml o = html("A  B");
    final SafeHtml n = o.wikify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString()).isEqualTo("<p>A  B</p>");
  }

  @Test
  public void testWikify_OneLine2() {
    final SafeHtml o = html("A  B\n");
    final SafeHtml n = o.wikify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString()).isEqualTo("<p>A  B\n</p>");
  }

  @Test
  public void testWikify_OneParagraph1() {
    final SafeHtml o = html("A\nB");
    final SafeHtml n = o.wikify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString()).isEqualTo("<p>A\nB</p>");
  }

  @Test
  public void testWikify_OneParagraph2() {
    final SafeHtml o = html("A\nB\n");
    final SafeHtml n = o.wikify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString()).isEqualTo("<p>A\nB\n</p>");
  }

  @Test
  public void testWikify_TwoParagraphs() {
    final SafeHtml o = html("A\nB\n\nC\nD");
    final SafeHtml n = o.wikify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString()).isEqualTo("<p>A\nB</p><p>C\nD</p>");
  }

  @Test
  public void testLinkify_SimpleHttp1() {
    final SafeHtml o = html("A http://go.here/ B");
    final SafeHtml n = o.wikify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "<p>A <a href=\"http://go.here/\" target=\"_blank\" rel=\"nofollow\""
                + ">http://go.here/</a> B</p>");
  }

  @Test
  public void testLinkify_SimpleHttps2() {
    final SafeHtml o = html("A https://go.here/ B");
    final SafeHtml n = o.wikify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "<p>A <a href=\"https://go.here/\" target=\"_blank\" rel=\"nofollow\""
                + ">https://go.here/</a> B</p>");
  }

  @Test
  public void testLinkify_Parens1() {
    final SafeHtml o = html("A (http://go.here/) B");
    final SafeHtml n = o.wikify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "<p>A (<a href=\"http://go.here/\" target=\"_blank\" rel=\"nofollow\""
                + ">http://go.here/</a>) B</p>");
  }

  @Test
  public void testLinkify_Parens() {
    final SafeHtml o = html("A http://go.here/#m() B");
    final SafeHtml n = o.wikify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "<p>A <a href=\"http://go.here/#m()\" target=\"_blank\" rel=\"nofollow\""
                + ">http://go.here/#m()</a> B</p>");
  }

  @Test
  public void testLinkify_AngleBrackets1() {
    final SafeHtml o = html("A <http://go.here/> B");
    final SafeHtml n = o.wikify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "<p>A &lt;<a href=\"http://go.here/\" target=\"_blank\" rel=\"nofollow\""
                + ">http://go.here/</a>&gt; B</p>");
  }

  private static SafeHtml html(String text) {
    return new SafeHtmlBuilder().append(text).toSafeHtml();
  }
}
