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

import com.google.gwt.regexp.shared.RegExp;

/**
 * A Find/Replace pair whose replacement string is a link.
 *
 * <p>It is safe to pass arbitrary user-provided links to this class. Links are sanitized as
 * follows:
 *
 * <ul>
 *   <li>Only http(s) and mailto links are supported; any other scheme results in an {@link
 *       IllegalArgumentException} from {@link #replace(String)}.
 *   <li>Special characters in the link after regex replacement are escaped with {@link
 *       SafeHtmlBuilder}.
 * </ul>
 */
public class LinkFindReplace implements FindReplace {
  public static boolean hasValidScheme(String link) {
    int colon = link.indexOf(':');
    if (colon < 0) {
      return true;
    }
    String scheme = link.substring(0, colon);
    return "http".equalsIgnoreCase(scheme)
        || "https".equalsIgnoreCase(scheme)
        || "mailto".equalsIgnoreCase(scheme);
  }

  private RegExp pat;
  private String link;

  protected LinkFindReplace() {}

  /**
   * @param find regular expression pattern to match substrings with.
   * @param link replacement link href. Capture groups within {@code find} can be referenced with
   *     {@code $<i>n</i>}.
   */
  public LinkFindReplace(String find, String link) {
    this.pat = RegExp.compile(find);
    this.link = link;
  }

  @Override
  public RegExp pattern() {
    return pat;
  }

  @Override
  public String replace(String input) {
    String href = pat.replace(input, link);
    if (!hasValidScheme(href)) {
      throw new IllegalArgumentException("Invalid scheme (" + toString() + "): " + href);
    }
    return new SafeHtmlBuilder()
        .openAnchor()
        .setAttribute("href", href)
        .append(SafeHtml.asis(input))
        .closeAnchor()
        .asString();
  }

  @Override
  public String toString() {
    return "find = " + pat.getSource() + ", link = " + link;
  }
}
