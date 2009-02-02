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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.ui.DomUtil;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;

public class LineCommentPanel extends Composite {
  public static String toHTML(final PatchLineComment comment) {
    return DomUtil.wikify(comment.getMessage().trim());
  }

  PatchLineComment comment;
  boolean isRecent;

  public LineCommentPanel(final PatchLineComment msg) {
    comment = msg;
    final HTML l = new HTML(toHTML(comment));
    l.setStyleName("gerrit-PatchLineComment");
    initWidget(l);
  }
}
