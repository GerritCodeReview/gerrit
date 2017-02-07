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

import org.junit.Test;

public class SafeHtml_LinkifyTest {
  @Test
  public void testLinkify_SimpleHttp1() {
    final SafeHtml o = html("A http://go.here/ B");
    final SafeHtml n = o.linkify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "A <a href=\"http://go.here/\" target=\"_blank\" rel=\"nofollow\""
                + ">http://go.here/</a> B");
  }

  @Test
  public void testLinkify_SimpleHttps2() {
    final SafeHtml o = html("A https://go.here/ B");
    final SafeHtml n = o.linkify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "A <a href=\"https://go.here/\" target=\"_blank\" rel=\"nofollow\""
                + ">https://go.here/</a> B");
  }

  @Test
  public void testLinkify_Parens1() {
    final SafeHtml o = html("A (http://go.here/) B");
    final SafeHtml n = o.linkify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "A (<a href=\"http://go.here/\" target=\"_blank\" rel=\"nofollow\""
                + ">http://go.here/</a>) B");
  }

  @Test
  public void testLinkify_Parens() {
    final SafeHtml o = html("A http://go.here/#m() B");
    final SafeHtml n = o.linkify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "A <a href=\"http://go.here/#m()\" target=\"_blank\" rel=\"nofollow\""
                + ">http://go.here/#m()</a> B");
  }

  @Test
  public void testLinkify_AngleBrackets1() {
    final SafeHtml o = html("A <http://go.here/> B");
    final SafeHtml n = o.linkify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "A &lt;<a href=\"http://go.here/\" target=\"_blank\" rel=\"nofollow\""
                + ">http://go.here/</a>&gt; B");
  }

  @Test
  public void testLinkify_TrailingPlainLetter() {
    final SafeHtml o = html("A http://go.here/foo B");
    final SafeHtml n = o.linkify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "A <a href=\"http://go.here/foo\" target=\"_blank\" rel=\"nofollow\""
                + ">http://go.here/foo</a> B");
  }

  @Test
  public void testLinkify_TrailingDot() {
    final SafeHtml o = html("A http://go.here/. B");
    final SafeHtml n = o.linkify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "A <a href=\"http://go.here/\" target=\"_blank\" rel=\"nofollow\""
                + ">http://go.here/</a>. B");
  }

  @Test
  public void testLinkify_TrailingComma() {
    final SafeHtml o = html("A http://go.here/, B");
    final SafeHtml n = o.linkify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "A <a href=\"http://go.here/\" target=\"_blank\" rel=\"nofollow\""
                + ">http://go.here/</a>, B");
  }

  @Test
  public void testLinkify_TrailingDotDot() {
    final SafeHtml o = html("A http://go.here/.. B");
    final SafeHtml n = o.linkify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "A <a href=\"http://go.here/.\" target=\"_blank\" rel=\"nofollow\""
                + ">http://go.here/.</a>. B");
  }

  private static SafeHtml html(String text) {
    return new SafeHtmlBuilder().append(text).toSafeHtml();
  }
}
