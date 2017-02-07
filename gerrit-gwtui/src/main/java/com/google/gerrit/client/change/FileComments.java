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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import java.util.List;

class FileComments extends Composite {
  interface Binder extends UiBinder<HTMLPanel, FileComments> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField InlineHyperlink path;
  @UiField FlowPanel comments;

  FileComments(
      CommentLinkProcessor clp, PatchSet.Id defaultPs, String title, List<CommentInfo> list) {
    initWidget(uiBinder.createAndBindUi(this));

    path.setTargetHistoryToken(url(defaultPs, list.get(0)));
    path.setText(title);
    for (CommentInfo c : list) {
      comments.add(new LineComment(clp, defaultPs, c));
    }
  }

  private static String url(PatchSet.Id ps, CommentInfo info) {
    return Dispatcher.toPatch(null, ps, info.path());
  }
}
