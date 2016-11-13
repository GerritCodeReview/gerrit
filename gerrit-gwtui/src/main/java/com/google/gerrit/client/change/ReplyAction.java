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

import com.google.gerrit.client.changes.ReviewInput;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.LabelInfo;
import com.google.gerrit.client.info.ChangeInfo.MessageInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.GlobalKey;

class ReplyAction {
  private final PatchSet.Id psId;
  private final String revision;
  private final boolean hasDraftComments;
  private final ChangeScreen.Style style;
  private final CommentLinkProcessor clp;
  private final Widget replyButton;
  private final Widget quickApproveButton;

  private NativeMap<LabelInfo> allLabels;
  private NativeMap<JsArrayString> permittedLabels;

  private ReplyBox replyBox;
  private PopupPanel popup;

  ReplyAction(
      ChangeInfo info,
      String revision,
      boolean hasDraftComments,
      ChangeScreen.Style style,
      CommentLinkProcessor clp,
      Widget replyButton,
      Widget quickApproveButton) {
    this.psId = new PatchSet.Id(info.legacyId(), info.revisions().get(revision)._number());
    this.revision = revision;
    this.hasDraftComments = hasDraftComments;
    this.style = style;
    this.clp = clp;
    this.replyButton = replyButton;
    this.quickApproveButton = quickApproveButton;

    boolean current = revision.equals(info.currentRevision());
    allLabels = info.allLabels();
    permittedLabels =
        current && info.hasPermittedLabels()
            ? info.permittedLabels()
            : NativeMap.<JsArrayString>create();
  }

  boolean isVisible() {
    return popup != null;
  }

  void quickApprove(ReviewInput input) {
    replyBox.quickApprove(input);
  }

  void hide() {
    if (popup != null) {
      popup.hide();
    }
    return;
  }

  void onReply(MessageInfo msg) {
    if (popup != null) {
      popup.hide();
      return;
    }

    if (replyBox == null) {
      replyBox = new ReplyBox(clp, psId, revision, allLabels, permittedLabels);
      allLabels = null;
      permittedLabels = null;
    }
    if (msg != null) {
      replyBox.replyTo(msg);
    }

    final PopupPanel p = new PopupPanel(true, false);
    p.setStyleName(style.replyBox());
    p.addAutoHidePartner(replyButton.getElement());
    p.addAutoHidePartner(quickApproveButton.getElement());
    p.addCloseHandler(
        new CloseHandler<PopupPanel>() {
          @Override
          public void onClose(CloseEvent<PopupPanel> event) {
            if (popup == p) {
              popup = null;
              if (hasDraftComments || replyBox.hasMessage()) {
                replyButton.setStyleName(style.highlight());
              }
            }
          }
        });
    p.add(replyBox);
    Window.scrollTo(0, 0);
    replyButton.removeStyleName(style.highlight());
    p.showRelativeTo(replyButton);
    GlobalKey.dialog(p);
    popup = p;
  }
}
