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

  private enum Nav {
    PREV ('[', PatchUtil.C.previousFileHelp(), 0),
    NEXT (']', PatchUtil.C.nextFileHelp(), 1);

    private final int key;
    private final String help;
    private final int cmd;

    private Nav(int k, String h, int i) {
      key = k;
      help = h;
      cmd = i;
    }
  }

  private final PatchSet.Id patchSetId;
  private final KeyCommandSet keys;
  private JsArray<FileInfo> files;

  private KeyCommand cmds[] = new KeyCommand[2];

  NavLinks2(KeyCommandSet kcs, PatchSet.Id forPatch, final String path) {
    initWidget(uiBinder.createAndBindUi(this));
    patchSetId = forPatch;
    keys = kcs;
    ChangeApi.revision(forPatch).view("files").get(
        new GerritCallback<NativeMap<FileInfo>>() {
      @Override
      public void onSuccess(NativeMap<FileInfo> result) {
        result.copyKeysIntoChildren("path");
        files = result.values();
        int index = 0; // TODO: Maybe use patchIndex.
        for (int i = 0; i < files.length(); i++) {
          if (path.equals(files.get(i).path())) {
            index = i;
          }
        }
        upLink.setHref("#" + PageLinks.toChange2(
            patchSetId.getParentKey(),
            String.valueOf(patchSetId.get())));
        setupNav(Nav.PREV, index == 0 ? null : files.get(index - 1));
        setupNav(Nav.NEXT, index == files.length() - 1 ? null : files.get(index + 1));
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

  private void setupNav(Nav nav, FileInfo info) {
    final String url = url(info);
    if (info != null) {
      String fileName = getFileNameOnly(info.path());
      if (nav == Nav.PREV) {
        prevLink.setHref("/#" + url);
        prevLink.setHTML(SafeHtml.asis(Util.C.prevPatchLinkIcon() + fileName));
      } else {
        nextLink.setHref("/#" + url);
        nextLink.setHTML(SafeHtml.asis(fileName + Util.C.nextPatchLinkIcon()));
      }
    }
    if (keys != null) {
      if (url != null) {
        cmds[nav.cmd] = new KeyCommand(0, nav.key, nav.help) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            Gerrit.display(url);
          }
        };
      } else {
        cmds[nav.cmd] = new UpToChangeCommand2(patchSetId, 0, nav.key);
      }
      keys.add(cmds[nav.cmd]);
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
