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

import com.google.gerrit.client.reviewdb.AccountProjectWatch;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusListenerAdapter;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.TableListener;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.HashSet;
import java.util.List;

class ProjectWatchPanel extends Composite {
  private WatchTable watches;

  private Button addNew;
  private SuggestBox nameTxt;
  private Button delSel;

  ProjectWatchPanel() {
    final FlowPanel body = new FlowPanel();

    {
      final FlowPanel fp = new FlowPanel();
      fp.setStyleName("gerrit-ProjectWatchPanel-AddPanel");

      final TextBox box = new TextBox();
      nameTxt = new SuggestBox(new ProjectNameSuggestOracle(), box);
      box.setVisibleLength(50);
      box.setText(Util.C.defaultProjectName());
      box.addStyleName("gerrit-InputFieldTypeHint");
      box.addFocusListener(new FocusListenerAdapter() {
        @Override
        public void onFocus(Widget sender) {
          if (Util.C.defaultProjectName().equals(box.getText())) {
            box.setText("");
            box.removeStyleName("gerrit-InputFieldTypeHint");
          }
        }

        @Override
        public void onLostFocus(Widget sender) {
          if ("".equals(box.getText())) {
            box.setText(Util.C.defaultProjectName());
            box.addStyleName("gerrit-InputFieldTypeHint");
          }
        }
      });
      fp.add(nameTxt);

      addNew = new Button(Util.C.buttonWatchProject());
      addNew.addClickListener(new ClickListener() {
        public void onClick(final Widget sender) {
          doAddNew();
        }
      });
      fp.add(addNew);
      body.add(fp);
    }

    watches = new WatchTable();
    body.add(watches);
    {
      final FlowPanel fp = new FlowPanel();
      delSel = new Button(Util.C.buttonDeleteSshKey());
      delSel.addClickListener(new ClickListener() {
        public void onClick(final Widget sender) {
          watches.deleteChecked();
        }
      });
      fp.add(delSel);
      body.add(fp);
    }

    initWidget(body);
  }

  void doAddNew() {
    final String projectName = nameTxt.getText();
    if (projectName == null || projectName.length() == 0) {
      return;
    }

    if (watches.moveToExistingProject(projectName)) {
      nameTxt.setText("");
      return;
    }

    addNew.setEnabled(false);
    Util.ACCOUNT_SVC.addProjectWatch(projectName,
        new GerritCallback<AccountProjectWatchInfo>() {
          public void onSuccess(final AccountProjectWatchInfo result) {
            addNew.setEnabled(true);
            nameTxt.setText("");
            watches.insertWatch(result);
          }

          @Override
          public void onFailure(final Throwable caught) {
            addNew.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  @Override
  public void onLoad() {
    super.onLoad();
    Util.ACCOUNT_SVC
        .myProjectWatch(new GerritCallback<List<AccountProjectWatchInfo>>() {
          public void onSuccess(final List<AccountProjectWatchInfo> result) {
            watches.display(result);
            watches.finishDisplay(true);
          }
        });
  }

  private class WatchTable extends FancyFlexTable<AccountProjectWatchInfo> {
    WatchTable() {
      table.setText(0, 2, com.google.gerrit.client.changes.Util.C
          .changeTableColumnProject());
      table.addTableListener(new TableListener() {
        public void onCellClicked(SourcesTableEvents sender, int row, int cell) {
          if (cell != 1 && getRowItem(row) != null) {
            movePointerTo(row);
          }
        }
      });

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, S_ICON_HEADER);
      fmt.addStyleName(0, 2, S_DATA_HEADER);
    }

    @Override
    protected Object getRowItemKey(final AccountProjectWatchInfo item) {
      return item.getWatch().getKey();
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
    protected void onOpenItem(final AccountProjectWatchInfo item) {
      toggleCurrentRow();
    }

    private void toggleCurrentRow() {
      final CheckBox cb = (CheckBox) table.getWidget(getCurrentRow(), 1);
      cb.setChecked(!cb.isChecked());
    }

    void deleteChecked() {
      final HashSet<AccountProjectWatch.Key> ids =
          new HashSet<AccountProjectWatch.Key>();
      for (int row = 1; row < table.getRowCount(); row++) {
        final AccountProjectWatchInfo k = getRowItem(row);
        if (k != null && ((CheckBox) table.getWidget(row, 1)).isChecked()) {
          ids.add(k.getWatch().getKey());
        }
      }
      if (!ids.isEmpty()) {
        Util.ACCOUNT_SVC.deleteProjectWatches(ids,
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                for (int row = 1; row < table.getRowCount();) {
                  final AccountProjectWatchInfo k = getRowItem(row);
                  if (k != null && ids.contains(k.getWatch().getKey())) {
                    table.removeRow(row);
                  } else {
                    row++;
                  }
                }
              }
            });
      }
    }

    boolean moveToExistingProject(final String projectName) {
      for (int row = 1; row < table.getRowCount(); row++) {
        final AccountProjectWatchInfo i = getRowItem(row);
        if (i != null && i.getProject().getName().equals(projectName)) {
          movePointerTo(row);
          return true;
        }
      }
      return false;
    }

    void insertWatch(final AccountProjectWatchInfo k) {
      final String newName = k.getProject().getName();
      int row = 1;
      for (; row < table.getRowCount(); row++) {
        final AccountProjectWatchInfo i = getRowItem(row);
        if (i != null && i.getProject().getName().compareTo(newName) >= 0) {
          break;
        }
      }

      table.insertRow(row);
      populate(row, k);
    }

    void display(final List<AccountProjectWatchInfo> result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final AccountProjectWatchInfo k : result) {
        final int row = table.getRowCount();
        table.insertRow(row);
        populate(row, k);
      }
    }

    void populate(final int row, final AccountProjectWatchInfo k) {
      table.setWidget(row, 1, new CheckBox());
      table.setText(row, 2, k.getProject().getName());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, S_ICON_CELL);
      fmt.addStyleName(row, 2, S_DATA_CELL);

      setRowItem(row, k);
    }
  }
}
