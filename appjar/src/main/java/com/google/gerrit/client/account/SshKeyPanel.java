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
import com.google.gerrit.client.reviewdb.AccountSshKey;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.TableListener;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtjsonrpc.client.VoidResult;

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
      delSel = new Button("Delete");
      delSel.setEnabled(false);
      delSel.addClickListener(new ClickListener() {
        public void onClick(final Widget sender) {
          keys.deleteCurrent();
        }
      });
      fp.add(delSel);
      body.add(fp);
    }

    {
      final FlowPanel fp = new FlowPanel();
      addTxt = new TextArea();
      addTxt.setVisibleLines(3);
      addTxt.setCharacterWidth(60);
      fp.add(addTxt);

      addNew = new Button("Add ...");
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
      Util.ACCOUNT_SVC.addSshKey(txt, new GerritCallback<AccountSshKey>() {
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
    Util.ACCOUNT_SVC.mySshKeys(new GerritCallback<SshKeyList>() {
      public void onSuccess(final SshKeyList result) {
        keys.display(result.keys);
        keys.finishDisplay(true);
      }
    });
  }

  private class SshKeyTable extends FancyFlexTable<AccountSshKey> {
    SshKeyTable() {
      table.setText(0, 1, "Algorithm");
      table.setText(0, 2, "Key");
      table.setText(0, 3, "Comment");
      table.setText(0, 4, "Last Used");
      table.setText(0, 5, "Stored");
      table.addTableListener(new TableListener() {
        public void onCellClicked(SourcesTableEvents sender, int row, int cell) {
          if (getRowItem(row) != null) {
            movePointerTo(row);
          }
        }
      });

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, S_DATA_HEADER);
      fmt.addStyleName(0, 2, S_DATA_HEADER);
      fmt.addStyleName(0, 3, S_DATA_HEADER);
      fmt.addStyleName(0, 4, S_DATA_HEADER);
      fmt.addStyleName(0, 5, S_DATA_HEADER);
    }

    @Override
    protected void movePointerTo(final int newRow) {
      super.movePointerTo(newRow);
      if (0 <= newRow && newRow < table.getRowCount()
          && getRowItem(newRow) != null) {
        delSel.setEnabled(true);
      } else {
        delSel.setEnabled(false);
      }
    }

    @Override
    protected Object getRowItemKey(final AccountSshKey item) {
      return item.getKey();
    }

    void deleteCurrent() {
      final int row = getCurrentRow();
      if (0 <= row && row < table.getRowCount()) {
        final AccountSshKey k = getRowItem(row);
        if (k != null) {
          Util.ACCOUNT_SVC.deleteSshKey(k.getKey(),
              new GerritCallback<VoidResult>() {
                public void onSuccess(final VoidResult result) {
                  int jumpTo = -1;
                  for (int i = 0; i < table.getRowCount(); i++) {
                    if (getRowItem(i) == k) {
                      table.removeRow(i);
                      movePointerTo(jumpTo);
                      break;
                    } else if (getRowItem(i) != null) {
                      jumpTo = i;
                    }
                  }
                }
              });
        }
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

      table.setText(row, 1, k.getAlgorithm());
      table.setText(row, 2, elide(k.getEncodedKey()));
      table.setText(row, 3, k.getComment());
      table.setText(row, 4, FormatUtil.mediumFormat(k.getLastUsedOn()));
      table.setText(row, 5, FormatUtil.mediumFormat(k.getStoredOn()));

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 2, "gerrit-SshKeyPanel-EncodedKey");

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
