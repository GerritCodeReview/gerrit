// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gwt.core.client.GWT;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

public class Util {
  public static final UIConstants C = GWT.create(UIConstants.class);
  public static final UIMessages M = GWT.create(UIMessages.class);

  public static String highlight(String text, String toHighlight) {
    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    if (toHighlight == null || "".equals(toHighlight)) {
      b.append(text);
      return b.toSafeHtml().asString();
    }

    int pos = 0;
    int endPos = 0;
    while ((pos = text.toLowerCase().indexOf(toHighlight.toLowerCase(), pos)) > -1) {
      if (pos > endPos) {
        b.append(text.substring(endPos, pos));
      }
      endPos = pos + toHighlight.length();
      b.openElement("b");
      b.append(text.substring(pos, endPos));
      b.closeElement("b");
      pos = endPos;
    }
    if (endPos < text.length()) {
      b.append(text.substring(endPos));
    }
    return b.toSafeHtml().asString();
  }
}
