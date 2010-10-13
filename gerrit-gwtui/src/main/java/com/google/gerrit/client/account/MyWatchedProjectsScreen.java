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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gerrit.client.ui.RPCSuggestOracle;
import com.google.gerrit.common.data.AccountProjectWatchInfo;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Change.Status;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.HashSet;
import java.util.List;

public class MyWatchedProjectsScreen extends SettingsScreen {
  private WatchTable watches;

  private Button addNew;
  private HintTextBox nameBox;
  private SuggestBox nameTxt;
  private HintTextBox filterTxt;
  private Button delSel;
  private boolean submitOnSelection;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    {
      nameBox = new HintTextBox();
      nameTxt = new SuggestBox(new RPCSuggestOracle(
          new ProjectNameSuggestOracle()), nameBox);
      nameBox.setVisibleLength(50);
      nameBox.setHintText(Util.C.defaultProjectName());
      nameBox.addKeyPressHandler(new KeyPressHandler() {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          submitOnSelection = false;

          if (event.getCharCode() == KeyCodes.KEY_ENTER) {
            if (nameTxt.isSuggestionListShowing()) {
              submitOnSelection = true;
            } else {
              doAddNew();
            }
          }
        }
      });
      nameTxt.addSelectionHandler(new SelectionHandler<Suggestion>() {
        @Override
        public void onSelection(SelectionEvent<Suggestion> event) {
          if (submitOnSelection) {
            submitOnSelection = false;
            doAddNew();
          }
        }
      });

      filterTxt = new HintTextBox();
      filterTxt.setVisibleLength(50);
      filterTxt.setHintText(Util.C.defaultFilter());
      filterTxt.addKeyPressHandler(new KeyPressHandler() {
        @Override
        public void onKeyPress(KeyPressEvent event) {
          if (event.getCharCode() == KeyCodes.KEY_ENTER) {
            doAddNew();
          }
        }
      });

      addNew = new Button(Util.C.buttonWatchProject());
      addNew.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          doAddNew();
        }
      });

      final Grid grid = new Grid(2, 2);
      grid.setStyleName(Gerrit.RESOURCES.css().infoBlock());
      grid.setText(0, 0, Util.C.watchedProjectName());
      grid.setWidget(0, 1, nameTxt);

      grid.setText(1, 0, Util.C.watchedProjectFilter());
      grid.setWidget(1, 1, filterTxt);

      final CellFormatter fmt = grid.getCellFormatter();
      fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
      fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().header());
      fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().header());
      fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().bottomheader());

      final FlowPanel fp = new FlowPanel();
      fp.setStyleName(Gerrit.RESOURCES.css().addWatchPanel());
      fp.add(grid);
      fp.add(addNew);
      add(fp);
    }

    watches = new WatchTable();
    add(watches);

    delSel = new Button(Util.C.buttonDeleteSshKey());
    delSel.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        watches.deleteChecked();
      }
    });
    add(delSel);
  }

  void doAddNew() {
    final String projectName = nameTxt.getText();
    if ("".equals(projectName)) {
      return;
    }

    String filter = filterTxt.getText();
    if (filter == null || filter.isEmpty()
        || filter.equals(Util.C.defaultFilter())) {
      filter = null;
    }

    addNew.setEnabled(false);
    nameBox.setEnabled(false);
    filterTxt.setEnabled(false);

    Util.ACCOUNT_SVC.addProjectWatch(projectName, filter,
        new GerritCallback<AccountProjectWatchInfo>() {
          public void onSuccess(final AccountProjectWatchInfo result) {
            addNew.setEnabled(true);
            nameBox.setEnabled(true);
            filterTxt.setEnabled(true);

            nameTxt.setText("");
            watches.insertWatch(result);
          }

          @Override
          public void onFailure(final Throwable caught) {
            addNew.setEnabled(true);
            nameBox.setEnabled(true);
            filterTxt.setEnabled(true);

            super.onFailure(caught);
          }
        });
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.ACCOUNT_SVC
        .myProjectWatch(new ScreenLoadCallback<List<AccountProjectWatchInfo>>(
            this) {
          public void preDisplay(final List<AccountProjectWatchInfo> result) {
            watches.display(result);
          }
        });
  }

  private class WatchTable extends FancyFlexTable<AccountProjectWatchInfo> {
    WatchTable() {
      table.setWidth("");
      table.insertRow(1);
      table.setText(0, 2, Util.C.watchedProjectName());
      table.setText(0, 3, Util.C.watchedProjectColumnEmailNotifications());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
      fmt.setRowSpan(0, 0, 2);
      fmt.setRowSpan(0, 1, 2);
      fmt.setRowSpan(0, 2, 2);
      DOM.setElementProperty(fmt.getElement(0, 3), "align", "center");

      fmt.setColSpan(0, 3, 3);
      table.setText(1, 0, Util.C.watchedProjectColumnNewChanges());
      table.setText(1, 1, Util.C.watchedProjectColumnAllComments());
      table.setText(1, 2, Util.C.watchedProjectColumnSubmittedChanges());
      fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(1, 1, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(1, 2, Gerrit.RESOURCES.css().dataHeader());
    }

    void deleteChecked() {
      final HashSet<AccountProjectWatch.Key> ids =
          new HashSet<AccountProjectWatch.Key>();
      for (int row = 1; row < table.getRowCount(); row++) {
        final AccountProjectWatchInfo k = getRowItem(row);
        if (k != null && ((CheckBox) table.getWidget(row, 1)).getValue()) {
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
      applyDataRowStyle(row);
      populate(row, k);
    }

    void display(final List<AccountProjectWatchInfo> result) {
      while (2 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final AccountProjectWatchInfo k : result) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, k);
      }
    }

    void populate(final int row, final AccountProjectWatchInfo info) {
      final FlowPanel fp = new FlowPanel();
      fp.add(new ProjectLink(info.getProject().getNameKey(), Status.NEW));
      if (info.getWatch().getFilter() != null) {
        Label filter = new Label(info.getWatch().getFilter());
        filter.setStyleName(Gerrit.RESOURCES.css().watchedProjectFilter());
        fp.add(filter);
      }

      table.setWidget(row, 1, new CheckBox());
      table.setWidget(row, 2, fp);

      addNotifyButton(AccountProjectWatch.Type.NEW_CHANGES, info, row, 3);
      addNotifyButton(AccountProjectWatch.Type.COMMENTS, info, row, 4);
      addNotifyButton(AccountProjectWatch.Type.SUBMITS, info, row, 5);

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 5, Gerrit.RESOURCES.css().dataCell());

      setRowItem(row, info);
    }

    protected void addNotifyButton(final AccountProjectWatch.Type type,
        final AccountProjectWatchInfo info, final int row, final int col) {
      final CheckBox cbox = new CheckBox();

      cbox.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          final boolean oldVal = info.getWatch().isNotify(type);
          info.getWatch().setNotify(type, cbox.getValue());
          Util.ACCOUNT_SVC.updateProjectWatch(info.getWatch(),
              new GerritCallback<VoidResult>() {
                public void onSuccess(final VoidResult result) {
                }

                @Override
                public void onFailure(final Throwable caught) {
                  info.getWatch().setNotify(type, oldVal);
                  cbox.setValue(oldVal);
                  super.onFailure(caught);
                }
              });
        }
      });

      cbox.setValue(info.getWatch().isNotify(type));
      table.setWidget(row, col, cbox);
    }
  }
}
