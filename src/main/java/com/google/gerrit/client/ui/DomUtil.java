// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.ui;

import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

/** Utilities for dealing with the DOM. */
public abstract class DomUtil {
  /** Escape XML/HTML special characters in the input string. */
  public static String escape(final String in) {
    return new SafeHtmlBuilder().append(in).asString();
  }

  /** Convert bare URLs into &lt;a href&gt; tags. */
  public static String linkify(final String in) {
    return in.replaceAll("(https?://[^ \n\r\t]*)", "<a href=\"$1\">$1</a>");
  }

  /** Do wiki style formatting to make it pretty */
  public static String wikify(String in) {
    in = escape(in);
    in = linkify(in);
    in = in.replaceAll("(^|\n)([ \t][^\n]*)", "$1<span class=\"gerrit-preformat\">$2</span><br />");
    in = in.replaceAll("\n\n", "<p>\n");
    return in;
  }

  private DomUtil() {
  }
}
