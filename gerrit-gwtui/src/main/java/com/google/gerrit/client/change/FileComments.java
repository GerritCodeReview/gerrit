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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwtorm.client.KeyUtil;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class FileComments extends Composite {
  interface Binder extends UiBinder<HTMLPanel, FileComments> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  private final String url;
  @UiField Anchor path;
  @UiField FlowPanel comments;

  FileComments(CommentLinkProcessor clp,
      PatchSet.Id ps,
      String title,
      List<CommentInfo> list) {
    initWidget(uiBinder.createAndBindUi(this));

    url = url(ps, list.get(0));
    path.setHref("#" + url);
    path.setText(title);

    Collections.sort(list, new Comparator<CommentInfo>() {
      @Override
      public int compare(CommentInfo a, CommentInfo b) {
        return a.line() - b.line();
      }
    });
    for (CommentInfo c : list) {
      comments.add(new LineComment(clp, c));
    }
  }

  @UiHandler("path")
  void onClick(ClickEvent e) {
    e.preventDefault();
    e.stopPropagation();
    Gerrit.display(url);
  }

  private static String url(PatchSet.Id ps, CommentInfo info) {
    // TODO(sop): Switch to Dispatcher.toPatchSideBySide.
    Change.Id c = ps.getParentKey();
    return new StringBuilder()
      .append("/c/").append(c.get()).append('/')
      .append(ps.get()).append('/')
      .append(KeyUtil.encode(info.path()))
      .append(",cm")
      .toString();
  }
}
