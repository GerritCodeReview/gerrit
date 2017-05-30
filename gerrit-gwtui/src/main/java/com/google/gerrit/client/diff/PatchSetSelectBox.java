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

package com.google.gerrit.client.diff;

import com.google.gerrit.client.DiffObject;
import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.blame.BlameInfo;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.info.WebLinkInfo;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.ImageResourceRenderer;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtorm.client.KeyUtil;
import java.util.List;
import net.codemirror.lib.CodeMirror;

/** HTMLPanel to select among patch sets */
class PatchSetSelectBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, PatchSetSelectBox> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  interface BoxStyle extends CssResource {
    String selected();
  }

  @UiField Image icon;
  @UiField HTMLPanel linkPanel;
  @UiField BoxStyle style;

  private final Project.NameKey project;
  private final Change.Id changeId;

  private DiffScreen parent;
  private DisplaySide side;
  private boolean sideA;
  private String path;
  private PatchSet.Id revision;
  private DiffObject idActive;
  private PatchSetSelectBox other;

  PatchSetSelectBox(
      DiffScreen parent,
      DisplaySide side,
      @Nullable Project.NameKey project,
      Change.Id changeId,
      DiffObject diffObject,
      String path) {
    initWidget(uiBinder.createAndBindUi(this));
    icon.setTitle(PatchUtil.C.addFileCommentToolTip());
    icon.addStyleName(Gerrit.RESOURCES.css().link());

    this.parent = parent;
    this.side = side;
    this.sideA = side == DisplaySide.A;
    this.project = project;
    this.changeId = changeId;
    this.revision = diffObject.asPatchSetId();
    this.idActive = diffObject;
    this.path = path;
  }

  void setUpPatchSetNav(
      JsArray<RevisionInfo> list,
      int parents,
      DiffInfo.FileMeta meta,
      boolean editExists,
      boolean current,
      boolean open,
      boolean binary) {
    InlineHyperlink selectedLink = null;
    if (sideA) {
      if (parents <= 1) {
        InlineHyperlink link = createLink(PatchUtil.C.patchBase(), DiffObject.base());
        linkPanel.add(link);
        selectedLink = link;
      } else {
        for (int i = parents; i > 0; i--) {
          PatchSet.Id id = new PatchSet.Id(changeId, -i);
          InlineHyperlink link = createLink(Util.M.diffBaseParent(i), DiffObject.patchSet(id));
          linkPanel.add(link);
          if (revision != null && id.equals(revision)) {
            selectedLink = link;
          }
        }
        InlineHyperlink link = createLink(Util.C.autoMerge(), DiffObject.autoMerge());
        linkPanel.add(link);
        if (selectedLink == null) {
          selectedLink = link;
        }
      }
    }
    for (int i = 0; i < list.length(); i++) {
      RevisionInfo r = list.get(i);
      InlineHyperlink link =
          createLink(r.id(), DiffObject.patchSet(new PatchSet.Id(changeId, r._number())));
      linkPanel.add(link);
      if (revision != null && r.id().equals(revision.getId())) {
        selectedLink = link;
      }
    }
    if (selectedLink != null) {
      selectedLink.setStyleName(style.selected());
    }

    if (meta == null) {
      return;
    }
    if (!Patch.isMagic(path)) {
      linkPanel.add(createDownloadLink());
    }
    if (!binary && open && !idActive.isBaseOrAutoMerge() && Gerrit.isSignedIn()) {
      if ((editExists && idActive.isEdit()) || (!editExists && current)) {
        linkPanel.add(createEditIcon());
      }
    }
    List<WebLinkInfo> webLinks = Natives.asList(meta.webLinks());
    if (webLinks != null) {
      for (WebLinkInfo webLink : webLinks) {
        linkPanel.add(webLink.toAnchor());
      }
    }
  }

  void setUpBlame(
      final CodeMirror cm, final boolean isBase, final PatchSet.Id rev, final String path) {
    if (!Patch.isMagic(path) && Gerrit.isSignedIn() && Gerrit.info().change().allowBlame()) {
      Anchor blameIcon = createBlameIcon();
      blameIcon.addClickHandler(
          new ClickHandler() {
            @Override
            public void onClick(ClickEvent clickEvent) {
              if (cm.extras().getBlameInfo() != null) {
                cm.extras().toggleAnnotation();
              } else {
                ChangeApi.blame(Project.NameKey.asStringOrNull(project), rev, path, isBase)
                    .get(
                        new GerritCallback<JsArray<BlameInfo>>() {

                          @Override
                          public void onSuccess(JsArray<BlameInfo> lines) {
                            cm.extras().toggleAnnotation(lines);
                          }
                        });
              }
            }
          });
      linkPanel.add(blameIcon);
    }
  }

  private Widget createEditIcon() {
    PatchSet.Id id =
        idActive.isBaseOrAutoMerge() ? other.idActive.asPatchSetId() : idActive.asPatchSetId();
    Anchor anchor =
        new Anchor(
            new ImageResourceRenderer().render(Gerrit.RESOURCES.edit()),
            "#" + Dispatcher.toEditScreen(project, id, path));
    anchor.setTitle(PatchUtil.C.edit());
    return anchor;
  }

  private Anchor createBlameIcon() {
    Anchor anchor = new Anchor(new ImageResourceRenderer().render(Gerrit.RESOURCES.blame()));
    anchor.setTitle(PatchUtil.C.blame());
    return anchor;
  }

  static void link(PatchSetSelectBox a, PatchSetSelectBox b) {
    a.other = b;
    b.other = a;
  }

  private InlineHyperlink createLink(String label, DiffObject id) {
    assert other != null;
    if (sideA) {
      assert !other.idActive.isBaseOrAutoMerge();
    }
    DiffObject diffBase = sideA ? id : other.idActive;
    DiffObject revision = sideA ? other.idActive : id;

    return new InlineHyperlink(
        label,
        parent.isSideBySide()
            ? Dispatcher.toSideBySide(project, diffBase, revision.asPatchSetId(), path)
            : Dispatcher.toUnified(project, diffBase, revision.asPatchSetId(), path));
  }

  private Anchor createDownloadLink() {
    DiffObject diffObject = idActive.isBaseOrAutoMerge() ? other.idActive : idActive;
    String sideURL = idActive.isBaseOrAutoMerge() ? "1" : "0";
    String base = GWT.getHostPageBaseURL() + "cat/";
    Anchor anchor =
        new Anchor(
            new ImageResourceRenderer().render(Gerrit.RESOURCES.downloadIcon()),
            base + KeyUtil.encode(diffObject.asPatchSetId() + "," + path) + "^" + sideURL);
    anchor.setTitle(PatchUtil.C.download());
    return anchor;
  }

  @UiHandler("icon")
  void onIconClick(@SuppressWarnings("unused") ClickEvent e) {
    parent.getCmFromSide(side).scrollToY(0);
    parent.getCommentManager().insertNewDraft(side, 0);
  }
}
