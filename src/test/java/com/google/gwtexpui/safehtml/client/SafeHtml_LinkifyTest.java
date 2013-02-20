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

public class SafeHtml_LinkifyTest extends TestCase {
  public void testLinkify_SimpleHttp1() {
    final SafeHtml o = html("A http://go.here/ B");
    final SafeHtml n = o.linkify();
    assertNotSame(o, n);
    assertEquals("A <a href=\"http://go.here/\" target=\"_blank\">http://go.here/</a> B", n.asString());
  }

  public void testLinkify_SimpleHttps2() {
    final SafeHtml o = html("A https://go.here/ B");
    final SafeHtml n = o.linkify();
    assertNotSame(o, n);
    assertEquals("A <a href=\"https://go.here/\" target=\"_blank\">https://go.here/</a> B", n.asString());
  }

  public void testLinkify_Parens1() {
    final SafeHtml o = html("A (http://go.here/) B");
    final SafeHtml n = o.linkify();
    assertNotSame(o, n);
    assertEquals("A (<a href=\"http://go.here/\" target=\"_blank\">http://go.here/</a>) B", n.asString());
  }

  public void testLinkify_Parens() {
    final SafeHtml o = html("A http://go.here/#m() B");
    final SafeHtml n = o.linkify();
    assertNotSame(o, n);
    assertEquals("A <a href=\"http://go.here/#m()\" target=\"_blank\">http://go.here/#m()</a> B", n.asString());
  }

  public void testLinkify_AngleBrackets1() {
    final SafeHtml o = html("A <http://go.here/> B");
    final SafeHtml n = o.linkify();
    assertNotSame(o, n);
    assertEquals("A &lt;<a href=\"http://go.here/\" target=\"_blank\">http://go.here/</a>&gt; B", n.asString());
  }

  private static SafeHtml html(String text) {
    return new SafeHtmlBuilder().append(text).toSafeHtml();
  }
}
