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

import com.google.gwt.regexp.shared.RegExp;

/**
 * A Find/Replace pair whose replacement string is arbitrary HTML.
 * <p>
 * <b>WARNING:</b> This class is not safe used with user-provided patterns.
 */
public class RawFindReplace implements FindReplace {
  private RegExp pat;
  private String replace;

  protected RawFindReplace() {
  }

  /**
   * @param regex regular expression pattern to match substrings with.
   * @param repl replacement expression. Capture groups within
   *        <code>regex</code> can be referenced with <code>$<i>n</i></code>.
   */
  public RawFindReplace(String find, String replace) {
    this.pat = RegExp.compile(find);
    this.replace = replace;
  }

  @Override
  public RegExp pattern() {
    return pat;
  }

  @Override
  public String replace(String input) {
    return pat.replace(input, replace);
  }

  @Override
  public String toString() {
    return "find = " + pat.getSource() + ", replace = " + replace;
  }
}
