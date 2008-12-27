// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.reviewdb.AccountSshKey;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.TableListener;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.HashSet;
import java.util.List;

public class SshKeyPanel extends Composite {
  private SshKeyTable keys;

  private Button addNew;
  private TextArea addTxt;
  private Button delSel;

  public SshKeyPanel() {
    final FlowPanel body = new FlowPanel();

    keys = new SshKeyTable();
    body.add(keys);
    {
      final FlowPanel fp = new FlowPanel();
      delSel = new Button(Util.C.buttonDeleteSshKey());
      delSel.addClickListener(new ClickListener() {
        public void onClick(final Widget sender) {
          keys.deleteChecked();
        }
      });
      fp.add(delSel);
      body.add(fp);
    }

    {
      final VerticalPanel fp = new VerticalPanel();
      fp.setStyleName("gerrit-AddSshKeyPanel");
      fp.add(new Label(Util.C.addSshKeyPanelHeader()));

      addTxt = new TextArea();
      addTxt.setVisibleLines(12);
      addTxt.setCharacterWidth(80);
      fp.add(addTxt);

      addNew = new Button(Util.C.buttonAddSshKey());
      addNew.addClickListener(new ClickListener() {
        public void onClick(final Widget sender) {
          doAddNew();
        }
      });
      fp.add(addNew);
      body.add(fp);
    }

    initWidget(body);
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
        }

        @Override
        public void onFailure(final Throwable caught) {
          addNew.setEnabled(true);
          super.onFailure(caught);
        }
      });
    }
  }

  @Override
  public void setVisible(final boolean visible) {
    if (!isVisible()) {
      update();
    }
    super.setVisible(visible);
  }

  public void update() {
    Util.ACCOUNT_SEC.mySshKeys(new GerritCallback<List<AccountSshKey>>() {
      public void onSuccess(final List<AccountSshKey> result) {
        keys.display(result);
        keys.finishDisplay(true);
      }
    });
  }

  private class SshKeyTable extends FancyFlexTable<AccountSshKey> {
    SshKeyTable() {
      table.setText(0, 3, Util.C.sshKeyAlgorithm());
      table.setText(0, 4, Util.C.sshKeyKey());
      table.setText(0, 5, Util.C.sshKeyComment());
      table.setText(0, 6, Util.C.sshKeyLastUsed());
      table.setText(0, 7, Util.C.sshKeyStored());
      table.addTableListener(new TableListener() {
        public void onCellClicked(SourcesTableEvents sender, int row, int cell) {
          if (cell != 1 && getRowItem(row) != null) {
            movePointerTo(row);
          }
        }
      });

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, S_ICON_HEADER);
      fmt.addStyleName(0, 2, S_ICON_HEADER);

      fmt.addStyleName(0, 3, S_DATA_HEADER);
      fmt.addStyleName(0, 4, S_DATA_HEADER);
      fmt.addStyleName(0, 5, S_DATA_HEADER);
      fmt.addStyleName(0, 6, S_DATA_HEADER);
      fmt.addStyleName(0, 7, S_DATA_HEADER);
    }

    @Override
    protected Object getRowItemKey(final AccountSshKey item) {
      return item.getKey();
    }

    @Override
    protected boolean onKeyPress(final char keyCode, final int modifiers) {
      if (super.onKeyPress(keyCode, modifiers)) {
        return true;
      }
      if (modifiers == 0) {
        switch (keyCode) {
          case 's':
          case 'c':
            toggleCurrentRow();
            return true;
        }
      }
      return false;
    }

    @Override
    protected void onOpenItem(final AccountSshKey item) {
      toggleCurrentRow();
    }

    private void toggleCurrentRow() {
      final CheckBox cb = (CheckBox) table.getWidget(getCurrentRow(), 1);
      cb.setChecked(!cb.isChecked());
    }

    void deleteChecked() {
      final HashSet<AccountSshKey.Id> ids = new HashSet<AccountSshKey.Id>();
      for (int row = 1; row < table.getRowCount(); row++) {
        final AccountSshKey k = getRowItem(row);
        if (k != null && ((CheckBox) table.getWidget(row, 1)).isChecked()) {
          ids.add(k.getKey());
        }
      }
      if (!ids.isEmpty()) {
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
          }
        });
      }
    }

    void display(final List<AccountSshKey> result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final AccountSshKey k : result) {
        addOneKey(k);
      }
    }

    void addOneKey(final AccountSshKey k) {
      final int row = table.getRowCount();
      table.insertRow(row);

      table.setWidget(row, 1, new CheckBox());
      table.setWidget(row, 2, k.isValid() ? Gerrit.ICONS.greenCheck()
          .createImage() : Gerrit.ICONS.redNot().createImage());
      table.setText(row, 3, k.getAlgorithm());
      table.setText(row, 4, elide(k.getEncodedKey()));
      table.setText(row, 5, k.getComment());
      table.setText(row, 6, FormatUtil.mediumFormat(k.getLastUsedOn()));
      table.setText(row, 7, FormatUtil.mediumFormat(k.getStoredOn()));

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, S_ICON_CELL);
      fmt.addStyleName(row, 2, S_ICON_CELL);
      fmt.addStyleName(row, 4, "gerrit-SshKeyPanel-EncodedKey");
      for (int c = 3; c <= 7; c++) {
        fmt.addStyleName(row, c, S_DATA_CELL);
      }

      setRowItem(row, k);
    }

    String elide(final String s) {
      if (s == null || s.length() < 40) {
        return null;
      }
      return s.substring(0, 30) + "..." + s.substring(s.length() - 10);
    }
  }
}
