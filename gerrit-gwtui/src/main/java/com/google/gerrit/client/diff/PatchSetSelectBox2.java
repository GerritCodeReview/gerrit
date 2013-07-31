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
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.common.changes.Side;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwtorm.client.KeyUtil;

import java.util.EnumSet;

/**
 * HTMLPanel to select among patch sets
 * TODO: Implement this.
 */
class PatchSetSelectBox2 extends Composite {
  interface Binder extends UiBinder<HTMLPanel, PatchSetSelectBox2> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  @UiField Image icon;
  @UiField FlowPanel linkPanel;

  private DiffTable table;
  private Side side;
  private String path;
  private int numRevisions;
  private int idActive;

  PatchSetSelectBox2(DiffTable table, Side side, PatchSet.Id revision, String path) {
    initWidget(uiBinder.createAndBindUi(this));
    icon.setTitle(PatchUtil.C.addFileCommentToolTip());
    icon.addStyleName(Gerrit.RESOURCES.css().link());
    this.table = table;
    this.side = side;
    this.path = path;
    setTitle(PatchUtil.C.addFileCommentByDoubleClick());
    addDomHandler(new DoubleClickHandler() {
      @Override
      public void onDoubleClick(DoubleClickEvent event) {
        onIconClick(null);
      }
    }, DoubleClickEvent.getType());
    final Change.Id changeId = revision.getParentKey();
    RestApi call = ChangeApi.detail(changeId.get());
    ChangeList.addOptions(call, EnumSet.of(
        ListChangesOption.ALL_REVISIONS));
    call.get(new GerritCallback<ChangeInfo>() {
      @Override
      public void onSuccess(ChangeInfo result) {
        JsArray<RevisionInfo> revisions = result.revisions().values();
        RevisionInfo.sortRevisionInfoByNumber(revisions);
        for (int i = 0; i < revisions.length(); i++) {
          linkPanel.add(createLink(String.valueOf(i + 1),
              new PatchSet.Id(changeId, revisions.get(i)._number())));
        }
      }
    });
  }

  private String url(final PatchSet.Id revision, final String path) {
    final StringBuilder p = new StringBuilder();
    ChangeApi.revision(revision).view("files").get(
        new GerritCallback<NativeMap<FileInfo>>() {
      @Override
      public void onSuccess(NativeMap<FileInfo> result) {
        FileInfo info = result.get(path);
        Change.Id c = revision.getParentKey();
        p.append("/c/").append(c).append('/');
        p.append(revision.get()).append('/').append(KeyUtil.encode(path));
        p.append(info.binary() ? ",unified" : ",cm");
      }
    });
    return p.toString();
  }

  private Anchor createLink(String label, final PatchSet.Id id) {
    final Anchor anchor = new Anchor(label);
    anchor.setHref(url(id, path));
    /*anchor.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        if (side == Side.PARENT) {
          idSideA = id;
        } else {
          idSideB = id;
        }

        Patch.Key keySideB = new Patch.Key(idSideB, patchKey.get());

        Gerrit.display(Dispatcher.toPatchSideBySide(idSideA, keySideB));
      }

    });*/

    return anchor;
  }

  @UiHandler("icon")
  void onIconClick(ClickEvent e) {
    table.createOrEditFileComment(side);
  }
}
