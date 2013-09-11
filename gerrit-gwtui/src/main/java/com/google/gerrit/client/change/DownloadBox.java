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

import static com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme.REPO_DOWNLOAD;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountApi;
import com.google.gerrit.client.changes.ChangeInfo.FetchInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.clippy.client.CopyableLabel;

class DownloadBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, DownloadBox> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  private final NativeMap<FetchInfo> fetch;
  private final String revision;
  private final String project;
  private final PatchSet.Id psId;

  @UiField ListBox scheme;
  @UiField CopyableLabel checkout;
  @UiField CopyableLabel cherryPick;
  @UiField CopyableLabel pull;
  @UiField AnchorElement patchBase64;
  @UiField AnchorElement patchZip;
  @UiField Element repoSection;
  @UiField CopyableLabel repoDownload;

  DownloadBox(NativeMap<FetchInfo> fetch, String revision,
      String project, PatchSet.Id psId) {
    this.fetch = fetch;
    this.revision = revision;
    this.project = project;
    this.psId = psId;
    initWidget(uiBinder.createAndBindUi(this));
  }

  @Override
  protected void onLoad() {
    if (scheme.getItemCount() == 0) {
      renderScheme(fetch);
    }
  }

  @UiHandler("scheme")
  void onScheme(ChangeEvent event) {
    renderCommands();

    if (Gerrit.isSignedIn()) {
      saveScheme();
    }
  }

  private void renderCommands() {
    FetchInfo info = fetch.get(scheme.getValue(scheme.getSelectedIndex()));
    checkout(info);
    cherryPick(info);
    pull(info);
    patch(info);
    repo(info);
  }

  private void checkout(FetchInfo info) {
    checkout.setText(
        "git fetch " + info.url() + " " + info.ref()
        + " && git checkout FETCH_HEAD");
  }

  private void cherryPick(FetchInfo info) {
    cherryPick.setText(
        "git fetch " + info.url() + " " + info.ref()
        + " && git cherry-pick FETCH_HEAD");
  }

  private void pull(FetchInfo info) {
    pull.setText("git pull " + info.url() + " " + info.ref());
  }

  private void patch(FetchInfo info) {
    String id = revision.substring(0, 7);
    patchBase64.setInnerText(id + ".diff.base64");
    patchBase64.setHref(new RestApi("/changes/")
      .id(psId.getParentKey().get())
      .view("revisions")
      .id(revision)
      .view("patch")
      .addParameterTrue("download")
      .url());

    patchZip.setInnerText(id + ".diff.zip");
    patchZip.setHref(new RestApi("/changes/")
      .id(psId.getParentKey().get())
      .view("revisions")
      .id(revision)
      .view("patch")
      .addParameterTrue("zip")
      .url());
  }

  private void repo(FetchInfo info) {
    if (Gerrit.getConfig().getDownloadSchemes().contains(REPO_DOWNLOAD)) {
      UIObject.setVisible(repoSection, true);
      repoDownload.setText("repo download "
          + project
          + " " + psId.getParentKey().get() + "/" + psId.get());
    }
  }

  private void renderScheme(NativeMap<FetchInfo> fetch) {
    for (String id : fetch.keySet()) {
      FetchInfo info = fetch.get(id);
      String u = info.url();
      int css = u.indexOf("://");
      if (css > 0) {
        int s = u.indexOf('/', css + 3);
        if (s > 0) {
          u = u.substring(0, s + 1);
        }
      }
      scheme.addItem(u, id);
    }
    if (scheme.getItemCount() == 1) {
      scheme.setSelectedIndex(0);
      scheme.setVisible(false);
    } else {
      int select = 0;
      String find = getUserPreference();
      if (find != null) {
        for (int i = 0; i < scheme.getItemCount(); i++) {
          if (find.equals(scheme.getValue(i))) {
            select = i;
            break;
          }
        }
      }
      scheme.setSelectedIndex(select);
    }
    renderCommands();
  }

  private static String getUserPreference() {
    if (Gerrit.isSignedIn()) {
      DownloadScheme pref =
          Gerrit.getUserAccount().getGeneralPreferences().getDownloadUrl();
      if (pref != null) {
        switch (pref) {
          case ANON_GIT:
            return "git";
          case HTTP:
          case ANON_HTTP:
            return "http";
          case SSH:
            return "ssh";
          default:
            return null;
        }
      }
    }
    return null;
  }

  private void saveScheme() {
    DownloadScheme scheme = getSelectedScheme();
    AccountGeneralPreferences pref =
        Gerrit.getUserAccount().getGeneralPreferences();

    if (scheme != null && scheme != pref.getDownloadUrl()) {
      pref.setDownloadUrl(scheme);
      PreferenceInput in = PreferenceInput.create();
      in.download_scheme(scheme);
      AccountApi.self().view("preferences")
          .post(in, new AsyncCallback<JavaScriptObject>() {
            @Override
            public void onSuccess(JavaScriptObject result) {
            }

            @Override
            public void onFailure(Throwable caught) {
            }
          });
    }
  }

  private DownloadScheme getSelectedScheme() {
    String id = scheme.getValue(scheme.getSelectedIndex());
    if ("git".equals(id)) {
      return DownloadScheme.ANON_GIT;
    } else if ("http".equals(id)) {
      return DownloadScheme.HTTP;
    } else if ("ssh".equals(id)) {
      return DownloadScheme.SSH;
    }
    return null;
  }

  private static class PreferenceInput extends JavaScriptObject {
    static PreferenceInput create() {
      return createObject().cast();
    }

    final void download_scheme(DownloadScheme s) {
      download_scheme0(s.name());
    }

    private final native void download_scheme0(String n) /*-{
      this.download_scheme = n;
    }-*/;

    protected PreferenceInput() {
    }
  }
}
