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

import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HTMLPanel;

import net.codemirror.lib.CodeMirror;

/** An HtmlPanel for displaying a published comment */
class PublishedBox extends CommentBox {
  interface Binder extends UiBinder<HTMLPanel, PublishedBox> {}
  private static UiBinder<HTMLPanel, CommentBox> uiBinder =
      GWT.create(Binder.class);

  private DraftBox replyBox;

  PublishedBox(
      SideBySide2 host,
      CodeMirror cm,
      PatchSet.Id id,
      CommentInfo info,
      CommentLinkProcessor linkProcessor) {
    super(host, cm, uiBinder, id, info, linkProcessor, false);
  }

  void registerReplyBox(DraftBox box) {
    replyBox = box;
    box.registerReplyToBox(this);
  }

  void unregisterReplyBox() {
    replyBox = null;
  }

  private void openReplyBox() {
    replyBox.setOpen(true);
    replyBox.setEdit(true);
  }

  DraftBox addReplyBox(String initMessage, boolean doSave) {
    DraftBox box = getDiffView().addReply(getOriginal(), initMessage, doSave);
    registerReplyBox(box);
    return box;
  }

  private void checkAndAddReply(String initMessage, boolean doSave) {
    if (replyBox == null) {
      DraftBox box = addReplyBox(initMessage, doSave);
      if (isFileComment()) {
        getDiffTable().addFileCommentBox(box, getSide());
      }
    } else {
      openReplyBox();
    }
  }

  @UiHandler("reply")
  void onReply(ClickEvent e) {
    checkAndAddReply("", false);
  }

  @UiHandler("replyDone")
  void onReplyDone(ClickEvent e) {
    checkAndAddReply(PatchUtil.C.cannedReplyDone(), true);
  }
}
