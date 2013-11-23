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
import com.google.gerrit.client.changes.ReviewInfo;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtorm.client.KeyUtil;

class Header extends Composite {
  interface Binder extends UiBinder<HTMLPanel, Header> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField CheckBox reviewed;
  @UiField Element filePath;

  @UiField Element noDiff;

  @UiField InlineHyperlink prev;
  @UiField InlineHyperlink up;
  @UiField InlineHyperlink next;

  private final KeyCommandSet keys;
  private final PatchSet.Id base;
  private final PatchSet.Id patchSetId;
  private final String path;
  private boolean hasPrev;
  private boolean hasNext;
  private String nextPath;

  Header(KeyCommandSet keys, PatchSet.Id base, PatchSet.Id patchSetId,
      String path) {
    initWidget(uiBinder.createAndBindUi(this));
    this.keys = keys;
    this.base = base;
    this.patchSetId = patchSetId;
    this.path = path;

    SafeHtml.setInnerHTML(filePath, formatPath(path));
    up.setTargetHistoryToken(PageLinks.toChange(
        patchSetId.getParentKey(),
        base != null ? String.valueOf(base.get()) : null,
        String.valueOf(patchSetId.get())));
  }

  private static SafeHtml formatPath(String path) {
    SafeHtmlBuilder b = new SafeHtmlBuilder();
    if (Patch.COMMIT_MSG.equals(path)) {
      return b.append(Util.C.commitMessage());
    }

    int s = path.lastIndexOf('/') + 1;
    b.append(path.substring(0, s));
    b.openElement("b");
    b.append(path.substring(s));
    b.closeElement("b");
    return b;
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
            break;
          }
        }
        FileInfo nextInfo = index == files.length() - 1
            ? null
            : files.get(index + 1);
        setupNav(prev, '[', PatchUtil.C.previousFileHelp(),
            index == 0 ? null : files.get(index - 1));
        setupNav(next, ']', PatchUtil.C.nextFileHelp(), nextInfo);
        nextPath = nextInfo != null ? nextInfo.path() : null;
      }
    });

    if (Gerrit.isSignedIn()) {
      ChangeApi.revision(patchSetId).view("files")
        .addParameterTrue("reviewed")
        .get(new AsyncCallback<JsArrayString>() {
            @Override
            public void onSuccess(JsArrayString result) {
              for (int i = 0; i < result.length(); i++) {
                if (path.equals(result.get(i))) {
                  reviewed.setValue(true, false);
                  break;
                }
              }
            }

            @Override
            public void onFailure(Throwable caught) {
            }
          });
    }
  }

  void setReviewed(boolean r) {
    reviewed.setValue(r, true);
  }

  boolean isReviewed() {
    return reviewed.getValue();
  }

  @UiHandler("reviewed")
  void onValueChange(ValueChangeEvent<Boolean> event) {
    RestApi api = ChangeApi.revision(patchSetId)
        .view("files")
        .id(path)
        .view("reviewed");
    if (event.getValue()) {
      api.put(CallbackGroup.<ReviewInfo>emptyCallback());
    } else {
      api.delete(CallbackGroup.<ReviewInfo>emptyCallback());
    }
  }

  private String url(FileInfo info) {
    Change.Id c = patchSetId.getParentKey();
    StringBuilder p = new StringBuilder();
    p.append("/c/").append(c).append('/');
    if (base != null) {
      p.append(base.get()).append("..");
    }
    p.append(patchSetId.get()).append('/').append(KeyUtil.encode(info.path()));
    p.append(info.binary() ? ",unified" : ",cm");
    return p.toString();
  }

  private void setupNav(InlineHyperlink link, int key, String help, FileInfo info) {
    if (info != null) {
      final String url = url(info);
      link.setTargetHistoryToken(url);
      link.setTitle(FileInfo.getFileName(info.path()));
      keys.add(new KeyCommand(0, key, help) {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          Gerrit.display(url);
        }
      });
      if (link == prev) {
        hasPrev = true;
      } else {
        hasNext = true;
      }
    } else {
      link.getElement().getStyle().setVisibility(Visibility.HIDDEN);
      keys.add(new UpToChangeCommand2(patchSetId, 0, key));
    }
  }

  boolean hasPrev() {
    return hasPrev;
  }

  boolean hasNext() {
    return hasNext;
  }

  String getNextPath() {
    return nextPath;
  }

  void removeNoDiff() {
    noDiff.removeFromParent();
  }
}
