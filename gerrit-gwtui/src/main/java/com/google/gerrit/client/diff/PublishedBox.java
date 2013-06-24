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
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HTMLPanel;

/** An HtmlPanel for displaying a published comment */
class PublishedBox extends CommentBox {
  interface Binder extends UiBinder<HTMLPanel, PublishedBox> {}
  private static UiBinder<HTMLPanel, CommentBox> uiBinder =
      GWT.create(Binder.class);

  private DraftBox replyBox;

  PublishedBox(CodeMirrorDemo host, PatchSet.Id id, CommentInfo info,
      CommentLinkProcessor linkProcessor) {
    super(host, uiBinder, id, info, linkProcessor, false);
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

  @UiHandler("reply")
  void onReply(ClickEvent e) {
    if (replyBox == null) {
      DraftBox box = getDiffView().addReplyBox(getOriginal(), "", true);
      registerReplyBox(box);
    } else {
      openReplyBox();
    }
  }

  @UiHandler("replyDone")
  void onReplyDone(ClickEvent e) {
    if (replyBox == null) {
      DraftBox box = getDiffView().addReplyBox(getOriginal(), "Done", false);
      registerReplyBox(box);
    } else {
      openReplyBox();
    }
  }
}
