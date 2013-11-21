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
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;

import java.util.ArrayList;
import java.util.List;

/**
 * HTMLPanel to hold file comments.
 * TODO: Need to resize CodeMirror if this is resized since we don't have the
 * system scrollbar.
 */
class FileCommentPanel extends Composite {

  private SideBySide2 parent;
  private DiffTable table;
  private String path;
  private DisplaySide side;
  private List<CommentBox> boxes;
  private FlowPanel body;

  FileCommentPanel(SideBySide2 host, DiffTable table, String path, DisplaySide side) {
    this.parent = host;
    this.table = table;
    this.path = path;
    this.side = side;
    boxes = new ArrayList<CommentBox>();
    initWidget(body = new FlowPanel());
  }

  void createOrEditFileComment() {
    if (!Gerrit.isSignedIn()) {
      Gerrit.doSignIn(parent.getToken());
      return;
    }
    if (boxes.isEmpty()) {
      CommentInfo info = CommentInfo.createFile(
          path,
          parent.getStoredSideFromDisplaySide(side),
          null,
          null);
      addFileComment(parent.addDraftBox(info, side));
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
    body.add(box);
    table.setHeaderVisible(true);
  }

  void onRemoveDraftBox(DraftBox box) {
    boxes.remove(box);
    table.setHeaderVisible(true);
  }
}
