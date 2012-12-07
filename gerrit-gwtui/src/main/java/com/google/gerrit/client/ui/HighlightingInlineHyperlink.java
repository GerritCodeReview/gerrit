// Copyright (C) 2012 The Android Open Source Project
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

public class HighlightingInlineHyperlink extends InlineHyperlink {

  private String toHighlight;

  public HighlightingInlineHyperlink(final String text, final String token,
      final String toHighlight) {
    super(text, token);
    this.toHighlight = toHighlight;
    highlight(text, toHighlight);
  }

  @Override
  public void setText(String text) {
    super.setText(text);
    highlight(text, toHighlight);
  }

  private void highlight(final String text, final String toHighlight) {
    if (toHighlight == null || "".equals(toHighlight)) {
      return;
    }

    final SafeHtmlBuilder b = new SafeHtmlBuilder();
    int pos = 0;
    int endPos = 0;
    while ((pos = text.toLowerCase().indexOf(
        toHighlight.toLowerCase(), pos)) > -1) {
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
    setHTML(b.toSafeHtml().asString());
  }
}
