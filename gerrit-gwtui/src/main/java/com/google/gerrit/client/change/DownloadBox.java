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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountApi;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.FetchInfo;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.clippy.client.CopyableLabel;

import java.util.EnumSet;

class DownloadBox extends VerticalPanel {
  private final ChangeInfo change;
  private final String revision;
  private final PatchSet.Id psId;
  private final FlexTable commandTable;
  private final ListBox scheme;
  private NativeMap<FetchInfo> fetch;

  DownloadBox(ChangeInfo change, String revision, PatchSet.Id psId) {
    this.change = change;
    this.revision = revision;
    this.psId = psId;
    this.commandTable = new FlexTable();
    this.scheme = new ListBox();
    this.scheme.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        renderCommands();
        if (Gerrit.isSignedIn()) {
          saveScheme();
        }
      }
    });

    setStyleName(Gerrit.RESOURCES.css().downloadBox());
    commandTable.setStyleName(Gerrit.RESOURCES.css().downloadBoxTable());
    scheme.setStyleName(Gerrit.RESOURCES.css().downloadBoxScheme());
    add(commandTable);
  }

  @Override
  protected void onLoad() {
    if (fetch == null) {
      RestApi call = ChangeApi.detail(change.legacy_id().get());
      ChangeList.addOptions(call, EnumSet.of(
          revision.equals(change.current_revision())
             ? ListChangesOption.CURRENT_REVISION
             : ListChangesOption.ALL_REVISIONS,
          ListChangesOption.DOWNLOAD_COMMANDS));
      call.get(new AsyncCallback<ChangeInfo>() {
        @Override
        public void onSuccess(ChangeInfo result) {
          fetch = result.revision(revision).fetch();
          renderScheme();
        }

        @Override
        public void onFailure(Throwable caught) {
        }
      });
    }
  }

  private void renderCommands() {
    commandTable.removeAllRows();

    if (scheme.getItemCount() > 0) {
      FetchInfo fetchInfo =
          fetch.get(scheme.getValue(scheme.getSelectedIndex()));
      for (String commandName : Natives.keys(fetchInfo.commands())) {
        CopyableLabel copyLabel =
            new CopyableLabel(fetchInfo.command(commandName));
        copyLabel.setStyleName(Gerrit.RESOURCES.css().downloadBoxCopyLabel());
        insertCommand(commandName, copyLabel);
      }
    }
    insertPatch();
    insertCommand(null, scheme);
  }

  private void insertPatch() {
    String id = revision.substring(0, 7);
    Anchor patchBase64 = new Anchor(id + ".diff.base64");
    patchBase64.setHref(new RestApi("/changes/")
      .id(psId.getParentKey().get())
      .view("revisions")
      .id(revision)
      .view("patch")
      .addParameterTrue("download")
      .url());

    Anchor patchZip = new Anchor(id + ".diff.zip");
    patchZip.setHref(new RestApi("/changes/")
      .id(psId.getParentKey().get())
      .view("revisions")
      .id(revision)
      .view("patch")
      .addParameterTrue("zip")
      .url());

    HorizontalPanel p = new HorizontalPanel();
    p.add(patchBase64);
    InlineLabel spacer = new InlineLabel("|");
    spacer.setStyleName(Gerrit.RESOURCES.css().downloadBoxSpacer());
    p.add(spacer);
    p.add(patchZip);
    insertCommand("Patch-File", p);
  }

  private void insertCommand(String commandName, Widget w) {
    int row = commandTable.getRowCount();
    commandTable.insertRow(row);
    commandTable.getCellFormatter().addStyleName(row, 0,
        Gerrit.RESOURCES.css().downloadBoxTableCommandColumn());
    if (commandName != null) {
      commandTable.setText(row, 0, commandName);
    }
    if (w != null) {
      commandTable.setWidget(row, 1, w);
    }
  }

  private void renderScheme() {
    for (String id : fetch.keySet()) {
      scheme.addItem(id);
    }
    if (scheme.getItemCount() == 0) {
      scheme.setVisible(false);
    } else {
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
          case ANON_HTTP:
            return "anonymous http";
          case HTTP:
            return "http";
          case SSH:
            return "ssh";
          case REPO_DOWNLOAD:
            return "repo";
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
    } else if ("anonymous http".equals(id)) {
      return DownloadScheme.ANON_HTTP;
    } else if ("http".equals(id)) {
      return DownloadScheme.HTTP;
    } else if ("ssh".equals(id)) {
      return DownloadScheme.SSH;
    } else if ("repo".equals(id)) {
      return DownloadScheme.REPO_DOWNLOAD;
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
