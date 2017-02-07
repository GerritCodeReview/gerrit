// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.client.account;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.info.GpgKeyInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.http.client.Response;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MyGpgKeysScreen extends SettingsScreen {
  interface Binder extends UiBinder<HTMLPanel, MyGpgKeysScreen> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField(provided = true)
  GpgKeyTable keys;

  @UiField Button deleteKey;
  @UiField Button addKey;

  @UiField VerticalPanel addKeyBlock;
  @UiField NpTextArea keyText;

  @UiField VerticalPanel errorPanel;
  @UiField Label errorText;

  @UiField Button clearButton;
  @UiField Button addButton;
  @UiField Button closeButton;

  @Override
  protected void onInitUI() {
    super.onInitUI();
    keys = new GpgKeyTable();
    add(uiBinder.createAndBindUi(this));
    keys.updateDeleteButton();
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    refreshKeys();
  }

  @UiHandler("deleteKey")
  void onDeleteKey(@SuppressWarnings("unused") ClickEvent e) {
    keys.deleteChecked();
  }

  @UiHandler("addKey")
  void onAddKey(@SuppressWarnings("unused") ClickEvent e) {
    showAddKeyBlock(true);
  }

  @UiHandler("clearButton")
  void onClearButton(@SuppressWarnings("unused") ClickEvent e) {
    keyText.setText("");
    keyText.setFocus(true);
    errorPanel.setVisible(false);
  }

  @UiHandler("closeButton")
  void onCloseButton(@SuppressWarnings("unused") ClickEvent e) {
    showAddKeyBlock(false);
  }

  @UiHandler("addButton")
  void onAddButton(@SuppressWarnings("unused") ClickEvent e) {
    doAddKey();
  }

  private void refreshKeys() {
    AccountApi.self()
        .view("gpgkeys")
        .get(
            NativeMap.copyKeysIntoChildren(
                "id",
                new GerritCallback<NativeMap<GpgKeyInfo>>() {
                  @Override
                  public void onSuccess(NativeMap<GpgKeyInfo> result) {
                    List<GpgKeyInfo> list = Natives.asList(result.values());
                    // TODO(dborowitz): Sort on something more meaningful, like
                    // created date?
                    Collections.sort(
                        list,
                        new Comparator<GpgKeyInfo>() {
                          @Override
                          public int compare(GpgKeyInfo a, GpgKeyInfo b) {
                            return a.id().compareTo(b.id());
                          }
                        });
                    keys.clear();
                    keyText.setText("");
                    errorPanel.setVisible(false);
                    addButton.setEnabled(true);
                    if (!list.isEmpty()) {
                      keys.setVisible(true);
                      for (GpgKeyInfo k : list) {
                        keys.addOneKey(k);
                      }
                      showKeyTable(true);
                      showAddKeyBlock(false);
                    } else {
                      keys.setVisible(false);
                      showAddKeyBlock(true);
                      showKeyTable(false);
                    }

                    display();
                  }
                }));
  }

  private void showAddKeyBlock(boolean show) {
    addKey.setVisible(!show);
    addKeyBlock.setVisible(show);
  }

  private void showKeyTable(boolean show) {
    keys.setVisible(show);
    deleteKey.setVisible(show);
    addKey.setVisible(show);
  }

  private void doAddKey() {
    if (keyText.getText().isEmpty()) {
      return;
    }
    addButton.setEnabled(false);
    keyText.setEnabled(false);
    AccountApi.addGpgKey(
        "self",
        keyText.getText(),
        new AsyncCallback<NativeMap<GpgKeyInfo>>() {
          @Override
          public void onSuccess(NativeMap<GpgKeyInfo> result) {
            keyText.setEnabled(true);
            refreshKeys();
          }

          @Override
          public void onFailure(Throwable caught) {
            keyText.setEnabled(true);
            addButton.setEnabled(true);
            if (caught instanceof StatusCodeException) {
              StatusCodeException sce = (StatusCodeException) caught;
              if (sce.getStatusCode() == Response.SC_CONFLICT
                  || sce.getStatusCode() == Response.SC_BAD_REQUEST) {
                errorText.setText(sce.getEncodedResponse());
              } else {
                errorText.setText(sce.getMessage());
              }
            } else {
              errorText.setText("Unexpected error saving key: " + caught.getMessage());
            }
            errorPanel.setVisible(true);
          }
        });
  }

  private class GpgKeyTable extends FancyFlexTable<GpgKeyInfo> {
    private final ValueChangeHandler<Boolean> updateDeleteHandler;

    GpgKeyTable() {
      table.setWidth("");
      table.setText(0, 1, Util.C.gpgKeyId());
      table.setText(0, 2, Util.C.gpgKeyFingerprint());
      table.setText(0, 3, Util.C.gpgKeyUserIds());

      FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());

      updateDeleteHandler =
          new ValueChangeHandler<Boolean>() {
            @Override
            public void onValueChange(ValueChangeEvent<Boolean> event) {
              updateDeleteButton();
            }
          };
    }

    private void addOneKey(GpgKeyInfo k) {
      int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);

      CheckBox sel = new CheckBox();
      sel.addValueChangeHandler(updateDeleteHandler);
      table.setWidget(row, 0, sel);
      table.setWidget(row, 1, new CopyableLabel(k.id()));
      table.setText(row, 2, k.fingerprint());

      VerticalPanel userIds = new VerticalPanel();
      for (int i = 0; i < k.userIds().length(); i++) {
        userIds.add(new InlineLabel(k.userIds().get(i)));
      }
      table.setWidget(row, 3, userIds);

      FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 0, Gerrit.RESOURCES.css().iconCell());
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());

      setRowItem(row, k);
    }

    private void updateDeleteButton() {
      for (int row = 1; row < table.getRowCount(); row++) {
        if (isChecked(row)) {
          deleteKey.setEnabled(true);
          return;
        }
      }
      deleteKey.setEnabled(false);
    }

    private void deleteChecked() {
      deleteKey.setEnabled(false);
      List<String> toDelete = new ArrayList<>(table.getRowCount());
      for (int row = 1; row < table.getRowCount(); row++) {
        if (isChecked(row)) {
          toDelete.add(getRowItem(row).fingerprint());
        }
      }
      AccountApi.deleteGpgKeys(
          "self",
          toDelete,
          new GerritCallback<NativeMap<GpgKeyInfo>>() {
            @Override
            public void onSuccess(NativeMap<GpgKeyInfo> result) {
              refreshKeys();
            }

            @Override
            public void onFailure(Throwable caught) {
              deleteKey.setEnabled(true);
              super.onFailure(caught);
            }
          });
    }

    private boolean isChecked(int row) {
      return ((CheckBox) table.getWidget(row, 0)).getValue();
    }

    private void clear() {
      while (table.getRowCount() > 1) {
        table.removeRow(1);
      }
      for (int i = table.getRowCount() - 1; i >= 1; i++) {
        table.removeRow(i);
      }
    }
  }
}
