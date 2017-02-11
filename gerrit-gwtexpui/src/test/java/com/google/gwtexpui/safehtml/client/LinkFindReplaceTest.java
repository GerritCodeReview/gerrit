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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.Test;

public class LinkFindReplaceTest {
  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void noEscaping() {
    String find = "find";
    String link = "link";
    LinkFindReplace a = new LinkFindReplace(find, link);
    assertThat(a.pattern().getSource()).isEqualTo(find);
    assertThat(a.replace(find)).isEqualTo("<a href=\"link\">find</a>");
    assertThat(a.toString()).isEqualTo("find = " + find + ", link = " + link);
  }

  @Test
  public void backreference() {
    LinkFindReplace l = new LinkFindReplace("(bug|issue)\\s*([0-9]+)", "/bug?id=$2");
    assertThat(l.replace("issue 123")).isEqualTo("<a href=\"/bug?id=123\">issue 123</a>");
  }

  @Test
  public void hasValidScheme() {
    assertThat(LinkFindReplace.hasValidScheme("/absolute/path")).isTrue();
    assertThat(LinkFindReplace.hasValidScheme("relative/path")).isTrue();
    assertThat(LinkFindReplace.hasValidScheme("http://url/")).isTrue();
    assertThat(LinkFindReplace.hasValidScheme("HTTP://url/")).isTrue();
    assertThat(LinkFindReplace.hasValidScheme("https://url/")).isTrue();
    assertThat(LinkFindReplace.hasValidScheme("mailto://url/")).isTrue();
    assertThat(LinkFindReplace.hasValidScheme("ftp://url/")).isFalse();
    assertThat(LinkFindReplace.hasValidScheme("data:evil")).isFalse();
    assertThat(LinkFindReplace.hasValidScheme("javascript:alert(1)")).isFalse();
  }

  @Test
  public void invalidSchemeInReplace() {
    exception.expect(IllegalArgumentException.class);
    new LinkFindReplace("find", "javascript:alert(1)").replace("find");
  }

  @Test
  public void invalidSchemeWithBackreference() {
    exception.expect(IllegalArgumentException.class);
    new LinkFindReplace(".*(script:[^;]*)", "java$1").replace("Look at this script: alert(1);");
  }

  @Test
  public void replaceEscaping() {
    assertThat(new LinkFindReplace("find", "a\"&'<>b").replace("find"))
        .isEqualTo("<a href=\"a&quot;&amp;&#39;&lt;&gt;b\">find</a>");
  }

  @Test
  public void htmlInFind() {
    String rawFind = "<b>&quot;bold&quot;</b>";
    LinkFindReplace a = new LinkFindReplace(rawFind, "/bold");
    assertThat(a.pattern().getSource()).isEqualTo(rawFind);
    assertThat(a.replace(rawFind)).isEqualTo("<a href=\"/bold\">" + rawFind + "</a>");
  }
}
