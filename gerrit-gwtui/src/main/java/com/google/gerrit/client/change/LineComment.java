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

package com.google.gerrit.client.change;

import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

class LineComment extends Composite {
  interface Binder extends UiBinder<HTMLPanel, LineComment> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField Element location;
  @UiField Element message;

  LineComment(CommentLinkProcessor clp, CommentInfo info) {
    initWidget(uiBinder.createAndBindUi(this));

    location.setInnerText(info.has_line()
        ? Util.M.lineHeader(info.line())
        : Util.C.fileCommentHeader());
    if (info.message() != null) {
      message.setInnerSafeHtml(clp.apply(new SafeHtmlBuilder()
          .append(info.message().trim()).wikify()));
    }
  }
}
