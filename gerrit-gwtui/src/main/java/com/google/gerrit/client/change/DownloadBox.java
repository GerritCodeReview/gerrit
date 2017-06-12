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
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.EditInfo;
import com.google.gerrit.client.info.ChangeInfo.FetchInfo;
import com.google.gerrit.client.info.GeneralPreferences;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.extensions.client.ListChangesOption;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

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
    this.scheme.addChangeHandler(
        new ChangeHandler() {
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
      if (psId.get() == 0) {
        ChangeApi.editWithCommands(change.legacyId().get())
            .get(
                new AsyncCallback<EditInfo>() {
                  @Override
                  public void onSuccess(EditInfo result) {
                    fetch = result.fetch();
                    renderScheme();
                  }

                  @Override
                  public void onFailure(Throwable caught) {}
                });
      } else {
        RestApi call = ChangeApi.detail(change.legacyId().get());
        ChangeList.addOptions(
            call,
            EnumSet.of(
                revision.equals(change.currentRevision())
                    ? ListChangesOption.CURRENT_REVISION
                    : ListChangesOption.ALL_REVISIONS,
                ListChangesOption.DOWNLOAD_COMMANDS));
        call.get(
            new AsyncCallback<ChangeInfo>() {
              @Override
              public void onSuccess(ChangeInfo result) {
                fetch = result.revision(revision).fetch();
                renderScheme();
              }

              @Override
              public void onFailure(Throwable caught) {}
            });
      }
    }
  }

  private void renderCommands() {
    commandTable.removeAllRows();

    if (scheme.getItemCount() > 0) {
      FetchInfo fetchInfo = fetch.get(scheme.getValue(scheme.getSelectedIndex()));
      for (String commandName : fetchInfo.commands().sortedKeys()) {
        CopyableLabel copyLabel = new CopyableLabel(fetchInfo.command(commandName));
        copyLabel.setStyleName(Gerrit.RESOURCES.css().downloadBoxCopyLabel());
        insertCommand(commandName, copyLabel);
      }
    }
    if (change.revision(revision).commit().parents().length() == 1) {
      insertPatch();
    }
    insertArchive();
    insertCommand(null, scheme);
  }

  private void insertPatch() {
    String id = revision.substring(0, 7);
    Anchor patchBase64 = new Anchor(id + ".diff.base64");
    patchBase64.setHref(
        new RestApi("/changes/")
            .id(psId.getParentKey().get())
            .view("revisions")
            .id(revision)
            .view("patch")
            .addParameterTrue("download")
            .url());

    Anchor patchZip = new Anchor(id + ".diff.zip");
    patchZip.setHref(
        new RestApi("/changes/")
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

  private void insertArchive() {
    List<String> activated = Gerrit.info().download().archives();
    if (activated.isEmpty()) {
      return;
    }

    List<Anchor> anchors = new ArrayList<>(activated.size());
    for (String f : activated) {
      Anchor archive = new Anchor(f);
      archive.setHref(
          new RestApi("/changes/")
              .id(psId.getParentKey().get())
              .view("revisions")
              .id(revision)
              .view("archive")
              .addParameter("format", f)
              .url());
      anchors.add(archive);
    }

    HorizontalPanel p = new HorizontalPanel();
    Iterator<Anchor> it = anchors.iterator();
    while (it.hasNext()) {
      Anchor a = it.next();
      p.add(a);
      if (it.hasNext()) {
        InlineLabel spacer = new InlineLabel("|");
        spacer.setStyleName(Gerrit.RESOURCES.css().downloadBoxSpacer());
        p.add(spacer);
      }
    }
    insertCommand("Archive", p);
  }

  private void insertCommand(String commandName, Widget w) {
    int row = commandTable.getRowCount();
    commandTable.insertRow(row);
    commandTable
        .getCellFormatter()
        .addStyleName(row, 0, Gerrit.RESOURCES.css().downloadBoxTableCommandColumn());
    if (commandName != null) {
      commandTable.setText(row, 0, commandName);
    }
    if (w != null) {
      commandTable.setWidget(row, 1, w);
    }
  }

  private void renderScheme() {
    for (String id : fetch.sortedKeys()) {
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
        String find = Gerrit.getUserPreferences().downloadScheme();
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

  private void saveScheme() {
    String schemeStr = scheme.getValue(scheme.getSelectedIndex());
    GeneralPreferences prefs = Gerrit.getUserPreferences();
    if (Gerrit.isSignedIn() && !schemeStr.equals(prefs.downloadScheme())) {
      prefs.downloadScheme(schemeStr);
      GeneralPreferences in = GeneralPreferences.create();
      in.downloadScheme(schemeStr);
      AccountApi.self()
          .view("preferences")
          .put(
              in,
              new AsyncCallback<JavaScriptObject>() {
                @Override
                public void onSuccess(JavaScriptObject result) {}

                @Override
                public void onFailure(Throwable caught) {}
              });
    }
  }
}
