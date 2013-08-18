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

import com.google.gerrit.client.changes.ChangeInfo.FetchInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.user.client.PluginSafePopupPanel;

class DownloadAction {
  private final NativeMap<FetchInfo> fetch;
  private final String revision;
  private final PatchSet.Id psId;
  private final String project;
  private final ChangeScreen2.Style style;
  private final Widget download;

  private DownloadBox downloadBox;
  private PopupPanel popup;

  DownloadAction(
      Change.Id changeId,
      String project,
      RevisionInfo revision,
      ChangeScreen2.Style style,
      Widget download) {
    this.fetch = revision.has_fetch()
        ? revision.fetch()
        : NativeMap.<FetchInfo>create();
    this.revision = revision.name();
    this.psId = new PatchSet.Id(changeId, revision._number());
    this.project = project;
    this.style = style;
    this.download = download;
  }

  void onDownload() {
    if (popup != null) {
      popup.hide();
      return;
    }

    if (downloadBox == null) {
      downloadBox = new DownloadBox(fetch, revision, project, psId);
    }

    final PluginSafePopupPanel p = new PluginSafePopupPanel(true);
    p.setStyleName(style.replyBox());
    p.addAutoHidePartner(download.getElement());
    p.addCloseHandler(new CloseHandler<PopupPanel>() {
      @Override
      public void onClose(CloseEvent<PopupPanel> event) {
        if (popup == p) {
          popup = null;
        }
      }
    });
    p.add(downloadBox);
    p.showRelativeTo(download);
    GlobalKey.dialog(p);
    popup = p;
  }
}
