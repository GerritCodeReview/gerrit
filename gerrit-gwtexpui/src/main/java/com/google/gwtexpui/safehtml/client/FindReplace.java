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

/** A Find/Replace pair used against the {@link SafeHtml} block of text. */
public interface FindReplace {
  /** @return regular expression to match substrings with; should be treated as immutable. */
  RegExp pattern();

  /**
   * Find and replace a single instance of this pattern in an input.
   *
   * <p><b>WARNING:</b> No XSS sanitization is done on the return value of this method, e.g. this
   * value may be passed directly to {@link SafeHtml#replaceAll(String, String)}. Implementations
   * must sanitize output appropriately.
   *
   * @param input input string.
   * @return result of regular expression replacement.
   * @throws IllegalArgumentException if the input could not be safely sanitized.
   */
  String replace(String input);
}
