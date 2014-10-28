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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.GitwebLink;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.changes.ReviewInfo;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.diff.DiffInfo.Region;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

class Header extends Composite {
  interface Binder extends UiBinder<HTMLPanel, Header> {}
  private static final Binder uiBinder = GWT.create(Binder.class);
  static {
    Resources.I.style().ensureInjected();
  }

  private static enum ReviewedState {
    AUTO_REVIEW, LOADED;
  }

  @UiField CheckBox reviewed;
  @UiField Element project;
  @UiField Element filePath;

  @UiField Element noDiff;

  @UiField InlineHyperlink prev;
  @UiField InlineHyperlink up;
  @UiField InlineHyperlink next;
  @UiField Image preferences;

  private final KeyCommandSet keys;
  private final PatchSet.Id base;
  private final PatchSet.Id patchSetId;
  private final String path;
  private boolean hasPrev;
  private boolean hasNext;
  private String nextPath;
  private PreferencesAction prefsAction;
  private ReviewedState reviewedState;

  Header(KeyCommandSet keys, PatchSet.Id base, PatchSet.Id patchSetId,
      String path) {
    initWidget(uiBinder.createAndBindUi(this));
    this.keys = keys;
    this.base = base;
    this.patchSetId = patchSetId;
    this.path = path;

    if (!Gerrit.isSignedIn()) {
      reviewed.getElement().getStyle().setVisibility(Visibility.HIDDEN);
    }
    SafeHtml.setInnerHTML(filePath, formatPath(path, null, null));
    up.setTargetHistoryToken(PageLinks.toChange(
        patchSetId.getParentKey(),
        base != null ? base.getId() : null, patchSetId.getId()));
  }

  private static SafeHtml formatPath(String path, String project, String commit) {
    SafeHtmlBuilder b = new SafeHtmlBuilder();
    if (Patch.COMMIT_MSG.equals(path)) {
      return b.append(Util.C.commitMessage());
    }

    GitwebLink gw = (project != null && commit != null) ? Gerrit.getGitwebLink() : null;
    int s = path.lastIndexOf('/') + 1;
    if (gw != null && s > 0) {
      String base = path.substring(0, s - 1);
      b.openAnchor()
          .setAttribute("href", gw.toFile(project, commit, base))
          .setAttribute("title", gw.getLinkName())
          .append(base)
          .closeAnchor()
          .append('/');
    } else {
      b.append(path.substring(0, s));
    }
    b.openElement("b");
    b.append(path.substring(s));
    b.closeElement("b");
    return b;
  }

  @Override
  protected void onLoad() {
    DiffApi.list(patchSetId, base, new GerritCallback<NativeMap<FileInfo>>() {
      @Override
      public void onSuccess(NativeMap<FileInfo> result) {
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
        KeyCommand p = setupNav(prev, '[', PatchUtil.C.previousFileHelp(),
            index == 0 ? null : files.get(index - 1));
        KeyCommand n = setupNav(next, ']', PatchUtil.C.nextFileHelp(),
            nextInfo);
        if (p != null && n != null) {
          keys.pair(p, n);
        }
        nextPath = nextInfo != null ? nextInfo.path() : null;
      }
    });

    if (Gerrit.isSignedIn()) {
      ChangeApi.revision(patchSetId).view("files")
        .addParameterTrue("reviewed")
        .get(new AsyncCallback<JsArrayString>() {
            @Override
            public void onSuccess(JsArrayString result) {
              boolean b = Natives.asList(result).contains(path);
              reviewed.setValue(b, false);
              if (!b && reviewedState == ReviewedState.AUTO_REVIEW) {
                postAutoReviewed();
              }
              reviewedState = ReviewedState.LOADED;
            }

            @Override
            public void onFailure(Throwable caught) {
            }
          });
    }
  }

  void autoReview() {
    if (reviewedState == ReviewedState.LOADED && !reviewed.getValue()) {
      postAutoReviewed();
    } else {
      reviewedState = ReviewedState.AUTO_REVIEW;
    }
  }

  void setChangeInfo(ChangeInfo info) {
    GitwebLink gw = Gerrit.getGitwebLink();
    if (gw != null) {
      for (RevisionInfo rev : Natives.asList(info.revisions().values())) {
        if (patchSetId.getId().equals(rev.id())) {
          String c = rev.name();
          SafeHtml.setInnerHTML(filePath, formatPath(path, info.project(), c));
          SafeHtml.setInnerHTML(project, new SafeHtmlBuilder()
              .openAnchor()
              .setAttribute("href", gw.toFile(info.project(), c, ""))
              .setAttribute("title", gw.getLinkName())
              .append(info.project())
              .closeAnchor());
          return;
        }
      }
    }

    project.setInnerText(info.project());
  }

  void init(PreferencesAction pa) {
    prefsAction = pa;
    prefsAction.setPartner(preferences);
  }

  @UiHandler("reviewed")
  void onValueChange(ValueChangeEvent<Boolean> event) {
    if (event.getValue()) {
      reviewed().put(CallbackGroup.<ReviewInfo> emptyCallback());
    } else {
      reviewed().delete(CallbackGroup.<ReviewInfo> emptyCallback());
    }
  }

  private void postAutoReviewed() {
    reviewed().background().put(new AsyncCallback<ReviewInfo>() {
        @Override
        public void onSuccess(ReviewInfo result) {
          reviewed.setValue(true, false);
        }

        @Override
        public void onFailure(Throwable caught) {
        }
      });
  }

  private RestApi reviewed() {
    return ChangeApi.revision(patchSetId)
        .view("files")
        .id(path)
        .view("reviewed");
  }

  @UiHandler("preferences")
  void onPreferences(ClickEvent e) {
    prefsAction.show();
  }

  private String url(FileInfo info) {
    return info.binary()
      ? Dispatcher.toUnified(base, patchSetId, info.path())
      : Dispatcher.toSideBySide(base, patchSetId, info.path());
  }

  private KeyCommand setupNav(InlineHyperlink link, char key, String help, FileInfo info) {
    if (info != null) {
      final String url = url(info);
      link.setTargetHistoryToken(url);
      link.setTitle(PatchUtil.M.fileNameWithShortcutKey(
          FileInfo.getFileName(info.path()),
          Character.toString(key)));
      KeyCommand k = new KeyCommand(0, key, help) {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          Gerrit.display(url);
        }
      };
      keys.add(k);
      if (link == prev) {
        hasPrev = true;
      } else {
        hasNext = true;
      }
      return k;
    } else {
      link.getElement().getStyle().setVisibility(Visibility.HIDDEN);
      keys.add(new UpToChangeCommand2(patchSetId, 0, key));
      return null;
    }
  }

  Runnable toggleReviewed() {
    return new Runnable() {
      @Override
      public void run() {
        reviewed.setValue(!reviewed.getValue(), true);
      }
    };
  }

  Runnable navigate(Direction dir) {
    switch (dir) {
      case PREV:
        return new Runnable() {
          @Override
          public void run() {
            (hasPrev ? prev : up).go();
          }
        };
      case NEXT:
        return new Runnable() {
          @Override
          public void run() {
            (hasNext ? next : up).go();
          }
        };
      default:
        return new Runnable() {
          @Override
          public void run() {
          }
        };
    }
  }

  Runnable reviewedAndNext() {
    return new Runnable() {
      @Override
      public void run() {
        if (Gerrit.isSignedIn()) {
          reviewed.setValue(true, true);
        }
        navigate(Direction.NEXT).run();
      }
    };
  }

  String getNextPath() {
    return nextPath;
  }

  void setNoDiff(DiffInfo diff) {
    JsArray<Region> regions = diff.content();
    boolean b = regions.length() == 0
        || (regions.length() == 1 && regions.get(0).ab() != null);
    UIObject.setVisible(noDiff, b);
  }
}
