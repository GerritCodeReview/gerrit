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
import com.google.gerrit.client.projects.ConfigInfoCache;
import com.google.gerrit.client.projects.ConfigInfo.DownloadCommandInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
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

import java.util.List;

class DownloadBox extends VerticalPanel {
  private final Project.NameKey project;
  private final String ref;
  private final String revision;
  private final PatchSet.Id psId;
  private final FlexTable commandTable;
  private final ListBox scheme;

  private NativeMap<JsArray<DownloadCommandInfo>> downloadCommands;

  DownloadBox(Project.NameKey project,
      String ref, String revision, PatchSet.Id psId) {
    this.project = project;
    this.ref = ref;
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
    ConfigInfoCache.get(
        project,
        new GerritCallback<ConfigInfoCache.Entry>() {
          @Override
          public void onSuccess(ConfigInfoCache.Entry result) {
            downloadCommands = result.getInfo().changeDownloadCommands();
            if (scheme.getItemCount() == 0) {
              renderScheme();
            }
          }
        });
  }

  private void renderCommands() {
    commandTable.removeAllRows();
    List<DownloadCommandInfo> commands =
        Natives.asList(downloadCommands.get(scheme.getValue(scheme
            .getSelectedIndex())));
    if (ref != null && commands != null) {
      for (DownloadCommandInfo cmd : commands) {
        CopyableLabel copyLabel = new CopyableLabel(cmd.command(ref));
        copyLabel.setStyleName(Gerrit.RESOURCES.css().downloadBoxCopyLabel());
        insertCommand(cmd.name(), copyLabel);
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
    for (String id : downloadCommands.keySet()) {
      scheme.addItem(id);
    }
    if (scheme.getItemCount() == 0) {
      scheme.addItem("DEFAULT");
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
            return "Anonymous GIT";
          case ANON_HTTP:
            return "Anonymous HTTP";
          case HTTP:
            return "HTTP";
          case SSH:
            return "SSH";
          case REPO_DOWNLOAD:
            return "REPO";
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
    if ("Anonymous GIT".equals(id)) {
      return DownloadScheme.ANON_GIT;
    } else if ("Anonymous HTTP".equals(id)) {
      return DownloadScheme.ANON_HTTP;
    } else if ("HTTP".equals(id)) {
      return DownloadScheme.HTTP;
    } else if ("SSH".equals(id)) {
      return DownloadScheme.SSH;
    } else if ("REPO".equals(id)) {
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
