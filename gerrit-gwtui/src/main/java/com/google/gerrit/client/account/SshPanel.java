// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.data.SshHostKey;
import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.reviewdb.AccountSshKey;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtjsonrpc.client.RemoteJsonException;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

class SshPanel extends Composite {
  private SshKeyTable keys;

  private Button showAddKeyBlock;
  private Panel addKeyBlock;
  private Button closeAddKeyBlock;
  private Button clearNew;
  private Button addNew;
  private NpTextArea addTxt;
  private Button deleteKey;

  private Panel serverKeys;

  private int loadCount;

  SshPanel() {
    final FlowPanel body = new FlowPanel();

    showAddKeyBlock = new Button(Util.C.buttonShowAddSshKey());
    showAddKeyBlock.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        showAddKeyBlock(true);
      }
    });

    keys = new SshKeyTable();
    body.add(keys);
    {
      final FlowPanel fp = new FlowPanel();
      deleteKey = new Button(Util.C.buttonDeleteSshKey());
      deleteKey.setEnabled(false);
      deleteKey.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          keys.deleteChecked();
        }
      });
      fp.add(deleteKey);
      fp.add(showAddKeyBlock);
      body.add(fp);
    }

    addKeyBlock = new VerticalPanel();
    addKeyBlock.setVisible(false);
    addKeyBlock.setStyleName(Gerrit.RESOURCES.css().addSshKeyPanel());
    addKeyBlock.add(new SmallHeading(Util.C.addSshKeyPanelHeader()));
    addKeyBlock.add(new HTML(Util.C.addSshKeyHelp()));

    addTxt = new NpTextArea();
    addTxt.setVisibleLines(12);
    addTxt.setCharacterWidth(80);
    addTxt.setSpellCheck(false);
    addKeyBlock.add(addTxt);

    final HorizontalPanel buttons = new HorizontalPanel();
    addKeyBlock.add(buttons);

    clearNew = new Button(Util.C.buttonClearSshKeyInput());
    clearNew.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        addTxt.setText("");
        addTxt.setFocus(true);
      }
    });
    buttons.add(clearNew);

    addNew = new Button(Util.C.buttonAddSshKey());
    addNew.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doAddNew();
      }
    });
    buttons.add(addNew);

    closeAddKeyBlock = new Button(Util.C.buttonCloseAddSshKey());
    closeAddKeyBlock.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        showAddKeyBlock(false);
      }
    });
    buttons.add(closeAddKeyBlock);
    buttons.setCellWidth(closeAddKeyBlock, "100%");
    buttons.setCellHorizontalAlignment(closeAddKeyBlock,
        HasHorizontalAlignment.ALIGN_RIGHT);

    body.add(addKeyBlock);

    serverKeys = new FlowPanel();
    body.add(serverKeys);

    initWidget(body);
  }

  void setKeyTableVisible(final boolean on) {
    keys.setVisible(on);
    deleteKey.setVisible(on);
    closeAddKeyBlock.setVisible(on);
  }

  void doAddNew() {
    final String txt = addTxt.getText();
    if (txt != null && txt.length() > 0) {
      addNew.setEnabled(false);
      Util.ACCOUNT_SEC.addSshKey(txt, new GerritCallback<AccountSshKey>() {
        public void onSuccess(final AccountSshKey result) {
          addNew.setEnabled(true);
          addTxt.setText("");
          keys.addOneKey(result);
          if (!keys.isVisible()) {
            showAddKeyBlock(false);
            setKeyTableVisible(true);
            keys.updateDeleteButton();
          }
        }

        @Override
        public void onFailure(final Throwable caught) {
          addNew.setEnabled(true);

          if (isInvalidSshKey(caught)) {
            new ErrorDialog(Util.C.invalidSshKeyError()).center();

          } else {
            super.onFailure(caught);
          }
        }

        private boolean isInvalidSshKey(final Throwable caught) {
          if (caught instanceof InvalidSshKeyException) {
            return true;
          }
          return caught instanceof RemoteJsonException
              && InvalidSshKeyException.MESSAGE.equals(caught.getMessage());
        }
      });
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    Util.ACCOUNT_SEC.mySshKeys(new GerritCallback<List<AccountSshKey>>() {
      public void onSuccess(final List<AccountSshKey> result) {
        keys.display(result);
        if (result.isEmpty() && keys.isVisible()) {
          showAddKeyBlock(true);
        }
        if (++loadCount == 2) {
          display();
        }
      }
    });

    Gerrit.SYSTEM_SVC.daemonHostKeys(new GerritCallback<List<SshHostKey>>() {
      public void onSuccess(final List<SshHostKey> result) {
        serverKeys.clear();
        for (final SshHostKey keyInfo : result) {
          serverKeys.add(new SshHostKeyPanel(keyInfo));
        }
        if (++loadCount == 2) {
          display();
        }
      }
    });
  }

  void display() {
  }

  private void showAddKeyBlock(final boolean show) {
    showAddKeyBlock.setVisible(!show);
    addKeyBlock.setVisible(show);
  }

  private class SshKeyTable extends FancyFlexTable<AccountSshKey> {
    private ValueChangeHandler<Boolean> updateDeleteHandler;

    SshKeyTable() {
      table.setWidth("");
      table.setText(0, 2, Util.C.sshKeyStatus());
      table.setText(0, 3, Util.C.sshKeyAlgorithm());
      table.setText(0, 4, Util.C.sshKeyKey());
      table.setText(0, 5, Util.C.sshKeyComment());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 5, Gerrit.RESOURCES.css().dataHeader());

      updateDeleteHandler = new ValueChangeHandler<Boolean>() {
        @Override
        public void onValueChange(ValueChangeEvent<Boolean> event) {
          updateDeleteButton();
        }
      };
    }

    void deleteChecked() {
      final HashSet<AccountSshKey.Id> ids = new HashSet<AccountSshKey.Id>();
      for (int row = 1; row < table.getRowCount(); row++) {
        final AccountSshKey k = getRowItem(row);
        if (k != null && ((CheckBox) table.getWidget(row, 1)).getValue()) {
          ids.add(k.getKey());
        }
      }
      if (ids.isEmpty()) {
        updateDeleteButton();
      } else {
        Util.ACCOUNT_SEC.deleteSshKeys(ids, new GerritCallback<VoidResult>() {
          public void onSuccess(final VoidResult result) {
            for (int row = 1; row < table.getRowCount();) {
              final AccountSshKey k = getRowItem(row);
              if (k != null && ids.contains(k.getKey())) {
                table.removeRow(row);
              } else {
                row++;
              }
            }
            if (table.getRowCount() == 1) {
              display(Collections.<AccountSshKey> emptyList());
            } else {
              updateDeleteButton();
            }
          }
        });
      }
    }

    void display(final List<AccountSshKey> result) {
      if (result.isEmpty()) {
        setKeyTableVisible(false);
        showAddKeyBlock(true);
      } else {
        while (1 < table.getRowCount())
          table.removeRow(table.getRowCount() - 1);
        for (final AccountSshKey k : result) {
          addOneKey(k);
        }
        setKeyTableVisible(true);
        deleteKey.setEnabled(false);
      }
    }

    void addOneKey(final AccountSshKey k) {
      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);

      final CheckBox sel = new CheckBox();
      sel.addValueChangeHandler(updateDeleteHandler);

      table.setWidget(row, 1, sel);
      if (k.isValid()) {
        table.setText(row, 2, "");
        fmt.removeStyleName(row, 2, //
            Gerrit.RESOURCES.css().sshKeyPanelInvalid());
      } else {
        table.setText(row, 2, Util.C.sshKeyInvalid());
        fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().sshKeyPanelInvalid());
      }
      table.setText(row, 3, k.getAlgorithm());

      CopyableLabel keyLabel = new CopyableLabel(k.getSshPublicKey());
      keyLabel.setPreviewText(elide(k.getEncodedKey(), 40));
      table.setWidget(row, 4, keyLabel);

      table.setText(row, 5, k.getComment());

      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
      fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().sshKeyPanelEncodedKey());
      for (int c = 2; c <= 5; c++) {
        fmt.addStyleName(row, c, Gerrit.RESOURCES.css().dataCell());
      }

      setRowItem(row, k);
    }

    void updateDeleteButton() {
      boolean on = false;
      for (int row = 1; row < table.getRowCount(); row++) {
        CheckBox sel = (CheckBox) table.getWidget(row, 1);
        if (sel.getValue()) {
          on = true;
          break;
        }
      }
      deleteKey.setEnabled(on);
    }
  }

  static String elide(final String s, final int len) {
    if (s == null || s.length() < len || len <= 10) {
      return s;
    }
    return s.substring(0, len - 10) + "..." + s.substring(s.length() - 10);
  }
}
