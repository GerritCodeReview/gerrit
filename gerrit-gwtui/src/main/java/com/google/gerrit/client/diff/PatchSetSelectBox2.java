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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.diff.SideBySide2.DisplaySide;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.ListBox;

import java.util.EnumSet;

/**
 * HTMLPanel to select among patch sets
 * TODO: Implement download link, and use the space to add more info.
 */
class PatchSetSelectBox2 extends Composite {
  interface Binder extends UiBinder<HTMLPanel, PatchSetSelectBox2> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  @UiField Image icon;
  @UiField ListBox revisionList;

  private DiffTable table;
  private DisplaySide side;
  private boolean sideA;
  private String path;
  private Change.Id changeId;
  private PatchSet.Id idActive;
  private PatchSetSelectBox2 other;

  PatchSetSelectBox2(DiffTable table, final DisplaySide side,
      final Change.Id changeId, final PatchSet.Id revision, String path) {
    initWidget(uiBinder.createAndBindUi(this));
    icon.setTitle(PatchUtil.C.addFileCommentToolTip());
    icon.addStyleName(Gerrit.RESOURCES.css().link());
    this.table = table;
    this.side = side;
    this.sideA = side == DisplaySide.A;
    this.changeId = changeId;
    this.path = path;
    setTitle(PatchUtil.C.addFileCommentByDoubleClick());
    addDomHandler(new DoubleClickHandler() {
      @Override
      public void onDoubleClick(DoubleClickEvent event) {
        onIconClick(null);
      }
    }, DoubleClickEvent.getType());
    RestApi call = ChangeApi.detail(changeId.get());
    ChangeList.addOptions(call, EnumSet.of(
        ListChangesOption.ALL_REVISIONS,
        ListChangesOption.ALL_COMMITS));
    call.get(new GerritCallback<ChangeInfo>() {
      @Override
      public void onSuccess(ChangeInfo info) {
        info.revisions().copyKeysIntoChildren("name");
        JsArray<RevisionInfo> list = info.revisions().values();
        RevisionInfo.sortRevisionInfoByNumber(list);
        int selected = 0;
        if (list.length() > 0 && sideA) {
          if (list.get(0).commit().parents().length() > 1) {
            revisionList.addItem(PatchUtil.C.patchBaseAutoMerge());
          } else {
            revisionList.addItem(PatchUtil.C.patchBase());
          }
        }
        for (int i = 0; i < list.length(); i++) {
          RevisionInfo r = list.get(i);
          revisionList.addItem(
              r._number() + ": " + r.name().substring(0, 6),
              "" + r._number());
          if (revision != null && r._number() == revision.get()) {
            selected = i + 1;
          }
        }
        if (0 < selected) {
          idActive = new PatchSet.Id(changeId, selected);
          revisionList.setSelectedIndex(sideA ? selected : selected - 1);
        } else if (sideA && selected == 0) {
          idActive = null;
          revisionList.setSelectedIndex(0);
        }
      }
    });
  }

  static void link(PatchSetSelectBox2 a, PatchSetSelectBox2 b) {
    a.other = b;
    b.other = a;
  }

  @UiHandler("icon")
  void onIconClick(ClickEvent e) {
    table.createOrEditFileComment(side);
  }

  @UiHandler("revisionList")
  void onChangeRevision(ChangeEvent e) {
    int idx = revisionList.getSelectedIndex();
    if (0 <= idx) {
      revisionList.setEnabled(false);
      if (sideA && idx == 0) {
        idActive = null;
      } else {
        idActive = new PatchSet.Id(changeId, sideA ? idx : idx + 1);
      }
      assert other != null;
      Gerrit.display(Dispatcher.toPatchSideBySide2(
          sideA ? idActive : other.idActive,
          sideA ? other.idActive : idActive,
          FileInfo.getFileName(path)));
    }
  }
}
