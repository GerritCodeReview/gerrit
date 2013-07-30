// Copyright (C) 2010 The Android Open Source Project
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
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtorm.client.KeyUtil;

class NavLinks2 extends Composite {
  interface Binder extends UiBinder<HTMLPanel, NavLinks2> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField Anchor prevLink;
  @UiField Anchor nextLink;
  @UiField Anchor upLink;

  private final KeyCommandSet keys;
  private final PatchSet.Id patchSetId;
  private final String path;

  NavLinks2(KeyCommandSet keys, PatchSet.Id patchSetId, String path) {
    initWidget(uiBinder.createAndBindUi(this));
    this.keys = keys;
    this.patchSetId = patchSetId;
    this.path = path;
  }

  @Override
  protected void onLoad() {
    ChangeApi.revision(patchSetId).view("files").get(
        new GerritCallback<NativeMap<FileInfo>>() {
      @Override
      public void onSuccess(NativeMap<FileInfo> result) {
        result.copyKeysIntoChildren("path");
        JsArray<FileInfo> files = result.values();
        FileInfo.sortFileInfoByPath(files);
        int index = 0; // TODO: Maybe use patchIndex.
        for (int i = 0; i < files.length(); i++) {
          if (path.equals(files.get(i).path())) {
            index = i;
          }
        }
        upLink.setHref("#" + PageLinks.toChange2(
            patchSetId.getParentKey(),
            String.valueOf(patchSetId.get())));
        setupNav(prevLink, '[', PatchUtil.C.previousFileHelp(),
            index == 0 ? null : files.get(index - 1));
        setupNav(nextLink, ']', PatchUtil.C.nextFileHelp(),
            index == files.length() - 1 ? null : files.get(index + 1));
      }
    });
  }

  private String url(FileInfo info) {
    if (info == null) {
      return null;
    }
    Change.Id c = patchSetId.getParentKey();
    StringBuilder p = new StringBuilder();
    p.append("/c/").append(c).append('/');
    p.append(patchSetId.get()).append('/').append(KeyUtil.encode(info.path()));
    p.append(info.binary() ? ",unified" : ",cm");
    return p.toString();
  }

  private void setupNav(Anchor anchor, int key, String help, FileInfo info) {
    final String url = url(info);
    if (info != null) {
      String fileName = getFileNameOnly(info.path());
      anchor.setHref("#" + url);
      if (anchor == prevLink) {
        anchor.setHTML(new SafeHtmlBuilder()
        .append(SafeHtml.asis(Util.C.prevPatchLinkIcon()))
        .append(SafeHtml.asis(fileName))
        .toSafeHtml());
      } else {
        anchor.setHTML(new SafeHtmlBuilder()
        .append(SafeHtml.asis(fileName))
        .append(SafeHtml.asis(Util.C.nextPatchLinkIcon()))
        .toSafeHtml());
      }
    }
    if (url != null) {
      keys.add(new KeyCommand(0, key, help) {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          Gerrit.display(url);
        }
      });
    } else {
      keys.add(new UpToChangeCommand2(patchSetId, 0, key));
    }
  }

  private static String getFileNameOnly(String path) {
    String fileName = Patch.COMMIT_MSG.equals(path)
        ? Util.C.commitMessage()
        : path;
    int s = fileName.lastIndexOf('/');
    return s >= 0 ? fileName.substring(s + 1) : fileName;
  }
}
