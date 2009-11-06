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

public class SafeHtml_ReplaceTest extends TestCase {
  public void testReplaceTwoLinks() {
    final RegexFindReplace[] repl = {//
        new RegexFindReplace("(issue\\s(\\d+))", "<a href=\"?$2\">$1</a>") //
        };
    final SafeHtml o = html("A\nissue 42\nissue 9918\nB");
    final SafeHtml n = o.replaceAll(Arrays.asList(repl));
    assertNotSame(o, n);
    assertEquals("A\n" //
        + "<a href=\"?42\">issue 42</a>\n" //
        + "<a href=\"?9918\">issue 9918</a>\n" //
        + "B" //
    , n.asString());
  }

  public void testReplaceInOrder1() {
    final RegexFindReplace[] repl = {//
            new RegexFindReplace("(GWTEXPUI-(\\d+))",
                "<a href=\"gwtexpui-bug?$2\">$1</a>"), //
            new RegexFindReplace("(issue\\s+(\\d+))",
                "<a href=\"generic-bug?$2\">$1</a>"), //
        };
    final SafeHtml o = html("A\nissue 42\nReally GWTEXPUI-9918 is better\nB");
    final SafeHtml n = o.replaceAll(Arrays.asList(repl));
    assertNotSame(o, n);
    assertEquals("A\n" //
        + "<a href=\"generic-bug?42\">issue 42</a>\n" //
        + "Really <a href=\"gwtexpui-bug?9918\">GWTEXPUI-9918</a> is better\n"
        + "B" //
    , n.asString());
  }

  private static SafeHtml html(String text) {
    return new SafeHtmlBuilder().append(text).toSafeHtml();
  }
}
