// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

public class LineCommentPanel extends Composite {
  public static SafeHtml toSafeHtml(final PatchLineComment msg) {
    return new SafeHtmlBuilder().append(msg.getMessage().trim()).wikify();
  }

  PatchLineComment comment;
  boolean isRecent;
  private VerticalPanel panel;

  public LineCommentPanel(final PatchLineComment msg) {
    comment = msg;
    panel = new VerticalPanel();
    final Widget l = toSafeHtml(msg).toBlockWidget();
    l.setStyleName("gerrit-PatchLineComment");
    panel.add(l);

    panel.setStyleName("gerrit-PatchLineCommentPanel");
    initWidget(panel);
  }

  public Panel getPanel() {
    return panel;
  }

  void update(final PatchLineComment msg) {
    comment = msg;
    SafeHtml.set(getElement(), toSafeHtml(comment));
  }
}
