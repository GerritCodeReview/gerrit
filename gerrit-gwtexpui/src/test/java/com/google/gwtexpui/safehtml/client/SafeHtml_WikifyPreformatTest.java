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

public class SafeHtml_WikifyPreformatTest {
  private static final String B = "<span class=\"wikiPreFormat\">";
  private static final String E = "</span><br />";

  private static String pre(String raw) {
    return B + raw + E;
  }

  @Test
  public void testPreformat1() {
    final SafeHtml o = html("A\n\n  This is pre\n  formatted");
    final SafeHtml n = o.wikify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo("<p>A</p>" + "<p>" + pre("  This is pre") + pre("  formatted") + "</p>");
  }

  @Test
  public void testPreformat2() {
    final SafeHtml o = html("A\n\n  This is pre\n  formatted\n\nbut this is not");
    final SafeHtml n = o.wikify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "<p>A</p>"
                + "<p>"
                + pre("  This is pre")
                + pre("  formatted")
                + "</p>"
                + "<p>but this is not</p>");
  }

  @Test
  public void testPreformat3() {
    final SafeHtml o = html("A\n\n  Q\n    <R>\n  S\n\nB");
    final SafeHtml n = o.wikify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo(
            "<p>A</p>"
                + "<p>"
                + pre("  Q")
                + pre("    &lt;R&gt;")
                + pre("  S")
                + "</p>"
                + "<p>B</p>");
  }

  @Test
  public void testPreformat4() {
    final SafeHtml o = html("  Q\n    <R>\n  S\n\nB");
    final SafeHtml n = o.wikify();
    assertThat(o).isNotSameAs(n);
    assertThat(n.asString())
        .isEqualTo("<p>" + pre("  Q") + pre("    &lt;R&gt;") + pre("  S") + "</p>" + "<p>B</p>");
  }

  private static SafeHtml html(String text) {
    return new SafeHtmlBuilder().append(text).toSafeHtml();
  }
}
