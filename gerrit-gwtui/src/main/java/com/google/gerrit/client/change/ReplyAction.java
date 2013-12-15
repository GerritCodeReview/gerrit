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

import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.LabelInfo;
import com.google.gerrit.client.changes.ChangeInfo.MessageInfo;
import com.google.gerrit.client.changes.ReviewInput;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
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
import com.google.gwtexpui.user.client.PluginSafePopupPanel;

class ReplyAction {
  private final PatchSet.Id psId;
  private final String revision;
  private final ChangeScreen2.Style style;
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
      ChangeScreen2.Style style,
      CommentLinkProcessor clp,
      Widget replyButton,
      Widget quickApproveButton) {
    RevisionInfo revisionInfo = info.revisions().get(revision);
    this.psId = new PatchSet.Id(
        info.legacy_id(),
        revisionInfo._number(),
        revisionInfo.edit());
    this.revision = revision;
    this.style = style;
    this.clp = clp;
    this.replyButton = replyButton;
    this.quickApproveButton = quickApproveButton;

    boolean current = revision.equals(info.current_revision());
    allLabels = info.all_labels();
    permittedLabels = current && info.has_permitted_labels()
        ? info.permitted_labels()
        : NativeMap.<JsArrayString> create();
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
      replyBox = new ReplyBox(
          clp,
          psId,
          revision,
          allLabels,
          permittedLabels);
      allLabels = null;
      permittedLabels = null;
    }
    if (msg != null) {
      replyBox.replyTo(msg);
    }

    final PluginSafePopupPanel p = new PluginSafePopupPanel(true, false);
    p.setStyleName(style.replyBox());
    p.addAutoHidePartner(replyButton.getElement());
    p.addAutoHidePartner(quickApproveButton.getElement());
    p.addCloseHandler(new CloseHandler<PopupPanel>() {
      @Override
      public void onClose(CloseEvent<PopupPanel> event) {
        if (popup == p) {
          popup = null;
        }
      }
    });
    p.add(replyBox);
    Window.scrollTo(0, 0);
    p.showRelativeTo(replyButton);
    GlobalKey.dialog(p);
    popup = p;
  }
}
