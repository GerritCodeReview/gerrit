// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;

/** A {@code Suggestion} with highlights. */
public class HighlightSuggestion implements Suggestion {

  private final String keyword;
  private final String value;

  public HighlightSuggestion(String keyword, String value) {
    this.keyword = keyword;
    this.value = value;
  }

  @Override
  public String getDisplayString() {
    int start = 0;
    int keyLen = keyword.length();
    SafeHtmlBuilder builder = new SafeHtmlBuilder();
    for (; ; ) {
      int index = value.indexOf(keyword, start);
      if (index == -1) {
        builder.appendEscaped(value.substring(start));
        break;
      }
      builder.appendEscaped(value.substring(start, index));
      builder.appendHtmlConstant("<strong>");
      start = index + keyLen;
      builder.appendEscaped(value.substring(index, start));
      builder.appendHtmlConstant("</strong>");
    }
    return builder.toSafeHtml().asString();
  }

  @Override
  public String getReplacementString() {
    return value;
  }
}
