//Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.diff;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.common.changes.Side;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;

import java.util.ArrayList;
import java.util.List;

/**
 * HTMLPanel to hold file comments.
 * TODO: Need to resize CodeMirror if this is resized since we don't have the
 * system scrollbar.
 */
class FileCommentPanel extends Composite {
  interface Binder extends UiBinder<HTMLPanel, FileCommentPanel> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  private SideBySide2 parent;
  private DiffTable table;
  private String path;
  private Side side;
  private List<CommentBox> boxes;

  FileCommentPanel(SideBySide2 host, DiffTable table, String path, Side side) {
    initWidget(uiBinder.createAndBindUi(this));
    this.parent = host;
    this.table = table;
    this.path = path;
    this.side = side;
    boxes = new ArrayList<CommentBox>();
  }

  void createOrEditFileComment() {
    if (!Gerrit.isSignedIn()) {
      Gerrit.doSignIn(parent.getToken());
    }
    if (boxes.isEmpty()) {
      CommentInfo info = CommentInfo.create(
          path,
          side,
          null,
          null,
          null);
      addFileComment(parent.addDraftBox(info));
    } else {
      CommentBox box = boxes.get(boxes.size() - 1);
      if (box instanceof DraftBox) {
        ((DraftBox) box).setEdit(true);
      } else {
        addFileComment(((PublishedBox) box).addReplyBox());
      }
    }
  }

  int getBoxCount() {
    return boxes.size();
  }

  void addFileComment(CommentBox box) {
    boxes.add(box);
    table.updateFileCommentVisibility(false);
    ((HTMLPanel) getWidget()).add(box);
  }

  void onRemoveDraftBox(DraftBox box) {
    boxes.remove(box);
    table.updateFileCommentVisibility(false);
  }
}
