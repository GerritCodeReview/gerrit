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
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.ImageResourceRenderer;
import com.google.gwtorm.client.KeyUtil;

/** HTMLPanel to select among patch sets */
class PatchSetSelectBox2 extends Composite {
  interface Binder extends UiBinder<HTMLPanel, PatchSetSelectBox2> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface BoxStyle extends CssResource {
    String selected();
  }

  @UiField Image icon;
  @UiField HTMLPanel linkPanel;
  @UiField BoxStyle style;

  private DiffTable table;
  private DisplaySide side;
  private boolean sideA;
  private String path;
  private Change.Id changeId;
  private PatchSet.Id revision;
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
    this.revision = revision;
    this.idActive = (sideA && revision == null) ? null : revision;
    this.path = path;
  }

  void setUpPatchSetNav(JsArray<RevisionInfo> list, DiffInfo.FileMeta meta) {
    InlineHyperlink baseLink = null;
    InlineHyperlink selectedLink = null;
    if (sideA) {
      baseLink = createLink(PatchUtil.C.patchBase(), null);
      linkPanel.add(baseLink);
    }
    for (int i = 0; i < list.length(); i++) {
      RevisionInfo r = list.get(i);
      InlineHyperlink link = createLink(
          String.valueOf(r._number()), new PatchSet.Id(changeId, r._number()));
      linkPanel.add(link);
      if (revision != null && r._number() == revision.get()) {
        selectedLink = link;
      }
    }
    if (selectedLink != null) {
      selectedLink.setStyleName(style.selected());
    } else if (sideA) {
      baseLink.setStyleName(style.selected());
    }
    if (meta != null && !Patch.COMMIT_MSG.equals(path)) {
      linkPanel.add(createDownloadLink());
    }
  }

  static void link(PatchSetSelectBox2 a, PatchSetSelectBox2 b) {
    a.other = b;
    b.other = a;
  }

  private InlineHyperlink createLink(String label, PatchSet.Id id) {
    assert other != null;
    if (sideA) {
      assert other.idActive != null;
    }
    return new InlineHyperlink(label, Dispatcher.toPatchSideBySide2(
        sideA ? id : other.idActive,
        sideA ? other.idActive : id,
        path));
  }

  private Anchor createDownloadLink() {
    PatchSet.Id id = (idActive == null) ? other.idActive : idActive;
    String sideURL = (idActive == null) ? "1" : "0";
    String base = GWT.getHostPageBaseURL() + "cat/";
    Anchor anchor = new Anchor(
        new ImageResourceRenderer().render(Gerrit.RESOURCES.downloadIcon()),
        base + KeyUtil.encode(id + "," + path) + "^" + sideURL);
    anchor.setTitle(PatchUtil.C.download());
    return anchor;
  }

  @UiHandler("icon")
  void onIconClick(ClickEvent e) {
    table.createOrEditFileComment(side);
  }
}
