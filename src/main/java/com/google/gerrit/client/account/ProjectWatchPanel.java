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

import com.google.gerrit.client.reviewdb.AccountProjectWatch;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gerrit.client.ui.ProjectOpenLink;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.TextBox;
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
      box.addFocusHandler(new FocusHandler() {
        @Override
        public void onFocus(FocusEvent event) {
          if (Util.C.defaultProjectName().equals(box.getText())) {
            box.setText("");
            box.removeStyleName("gerrit-InputFieldTypeHint");
          }
        }
      });
      box.addBlurHandler(new BlurHandler() {
        @Override
        public void onBlur(BlurEvent event) {
          if ("".equals(box.getText())) {
            box.setText(Util.C.defaultProjectName());
            box.addStyleName("gerrit-InputFieldTypeHint");
          }
        }
      });
      fp.add(nameTxt);

      addNew = new Button(Util.C.buttonWatchProject());
      addNew.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
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
      delSel.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
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
    if (projectName == null || projectName.length() == 0
        || Util.C.defaultProjectName().equals(projectName)) {
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
          }
        });
  }

  private class WatchTable extends FancyFlexTable<AccountProjectWatchInfo> {
    WatchTable() {
      table.insertRow(1);
      table.setText(0, 2, com.google.gerrit.client.changes.Util.C
          .changeTableColumnProject());
      table.setText(0, 3, Util.C.watchedProjectColumnEmailNotifications());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, S_ICON_HEADER);
      fmt.addStyleName(0, 2, S_DATA_HEADER);
      fmt.addStyleName(0, 3, S_DATA_HEADER);
      fmt.setRowSpan(0, 0, 2);
      fmt.setRowSpan(0, 1, 2);
      fmt.setRowSpan(0, 2, 2);
      DOM.setElementProperty(fmt.getElement(0, 3), "align", "center");

      fmt.setColSpan(0, 3, 3);
      table.setText(1, 0, Util.C.watchedProjectColumnNewChanges());
      table.setText(1, 1, Util.C.watchedProjectColumnAllComments());
      table.setText(1, 2, Util.C.watchedProjectColumnSubmittedChanges());
      fmt.addStyleName(1, 0, S_DATA_HEADER);
      fmt.addStyleName(1, 1, S_DATA_HEADER);
      fmt.addStyleName(1, 2, S_DATA_HEADER);
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

    void populate(final int row, final AccountProjectWatchInfo k) {
      table.setWidget(row, 1, new CheckBox());
      table.setWidget(row, 2, new ProjectOpenLink(k.getProject().getNameKey()));
      {
        final CheckBox notifyNewChanges = new CheckBox();
        notifyNewChanges.addClickHandler(new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
            final boolean oldVal = k.getWatch().isNotifyNewChanges();
            k.getWatch().setNotifyNewChanges(notifyNewChanges.getValue());
            Util.ACCOUNT_SVC.updateProjectWatch(k.getWatch(),
                new GerritCallback<VoidResult>() {
                  public void onSuccess(final VoidResult result) {
                  }

                  @Override
                  public void onFailure(final Throwable caught) {
                    k.getWatch().setNotifyNewChanges(oldVal);
                    notifyNewChanges.setValue(oldVal);
                    super.onFailure(caught);
                  }
                });
          }
        });
        notifyNewChanges.setValue(k.getWatch().isNotifyNewChanges());
        table.setWidget(row, 3, notifyNewChanges);
      }
      {
        final CheckBox notifyAllComments = new CheckBox();
        notifyAllComments.addClickHandler(new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
            final boolean oldVal = k.getWatch().isNotifyAllComments();
            k.getWatch().setNotifyAllComments(notifyAllComments.getValue());
            Util.ACCOUNT_SVC.updateProjectWatch(k.getWatch(),
                new GerritCallback<VoidResult>() {
                  public void onSuccess(final VoidResult result) {
                  }

                  @Override
                  public void onFailure(final Throwable caught) {
                    k.getWatch().setNotifyAllComments(oldVal);
                    notifyAllComments.setValue(oldVal);
                    super.onFailure(caught);
                  }
                });
          }
        });
        notifyAllComments.setValue(k.getWatch().isNotifyAllComments());
        table.setWidget(row, 4, notifyAllComments);
      }
      {
        final CheckBox notifySubmittedChanges = new CheckBox();
        notifySubmittedChanges.addClickHandler(new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
            final boolean oldVal = k.getWatch().isNotifySubmittedChanges();
            k.getWatch().setNotifySubmittedChanges(
                notifySubmittedChanges.getValue());
            Util.ACCOUNT_SVC.updateProjectWatch(k.getWatch(),
                new GerritCallback<VoidResult>() {
                  public void onSuccess(final VoidResult result) {
                  }

                  @Override
                  public void onFailure(final Throwable caught) {
                    k.getWatch().setNotifySubmittedChanges(oldVal);
                    notifySubmittedChanges.setValue(oldVal);
                    super.onFailure(caught);
                  }
                });
          }
        });
        notifySubmittedChanges
            .setValue(k.getWatch().isNotifySubmittedChanges());
        table.setWidget(row, 5, notifySubmittedChanges);
      }

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, S_ICON_CELL);
      fmt.addStyleName(row, 2, S_DATA_CELL);
      fmt.addStyleName(row, 3, S_DATA_CELL);
      fmt.addStyleName(row, 4, S_DATA_CELL);
      fmt.addStyleName(row, 5, S_DATA_CELL);

      setRowItem(row, k);
    }
  }
}
