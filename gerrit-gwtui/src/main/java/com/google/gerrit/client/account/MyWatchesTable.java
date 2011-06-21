// Copyright (C) 2010 The Android Open Source Project
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
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.common.data.AccountProjectWatchInfo;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.AbstractEntity.Status;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MyWatchesTable extends FancyFlexTable<AccountProjectWatchInfo> {

  public MyWatchesTable() {
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

  public void deleteChecked() {
    final Set<AccountProjectWatch.Key> ids = getCheckedIds();
    if (!ids.isEmpty()) {
      Util.ACCOUNT_SVC.deleteProjectWatches(ids,
          new GerritCallback<VoidResult>() {
            public void onSuccess(final VoidResult result) {
              remove(ids);
            }
          });
    }
  }

  protected void remove(Set<AccountProjectWatch.Key> ids) {
    for (int row = 1; row < table.getRowCount();) {
      final AccountProjectWatchInfo k = getRowItem(row);
      if (k != null && ids.contains(k.getWatch().getKey())) {
        table.removeRow(row);
      } else {
        row++;
      }
    }
  }

  protected Set<AccountProjectWatch.Key> getCheckedIds() {
    final Set<AccountProjectWatch.Key> ids =
        new HashSet<AccountProjectWatch.Key>();
    for (int row = 1; row < table.getRowCount(); row++) {
      final AccountProjectWatchInfo k = getRowItem(row);
      if (k != null && ((CheckBox) table.getWidget(row, 1)).getValue()) {
        ids.add(k.getWatch().getKey());
      }
    }
    return ids;
  }

  public void insertWatch(final AccountProjectWatchInfo k) {
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

  public void display(final List<AccountProjectWatchInfo> result) {
    while (2 < table.getRowCount())
      table.removeRow(table.getRowCount() - 1);

    for (final AccountProjectWatchInfo k : result) {
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);
      populate(row, k);
    }
  }

  protected void populate(final int row, final AccountProjectWatchInfo info) {
    final FlowPanel fp = new FlowPanel();
    fp.add(new ProjectLink(info.getProject().getNameKey(), Status.NEW));
    if (info.getWatch().getFilter() != null) {
      Label filter = new Label(info.getWatch().getFilter());
      filter.setStyleName(Gerrit.RESOURCES.css().watchedProjectFilter());
      fp.add(filter);
    }

    table.setWidget(row, 1, new CheckBox());
    table.setWidget(row, 2, fp);

    addNotifyButton(AccountProjectWatch.NotifyType.NEW_CHANGES, info, row, 3);
    addNotifyButton(AccountProjectWatch.NotifyType.ALL_COMMENTS, info, row, 4);
    addNotifyButton(AccountProjectWatch.NotifyType.SUBMITTED_CHANGES, info, row, 5);

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
    fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 5, Gerrit.RESOURCES.css().dataCell());

    setRowItem(row, info);
  }

  protected void addNotifyButton(final AccountProjectWatch.NotifyType type,
      final AccountProjectWatchInfo info, final int row, final int col) {
    final CheckBox cbox = new CheckBox();

    cbox.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        final boolean oldVal = info.getWatch().isNotify(type);
        info.getWatch().setNotify(type, cbox.getValue());
        cbox.setEnabled(false);
        Util.ACCOUNT_SVC.updateProjectWatch(info.getWatch(),
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                cbox.setEnabled(true);
              }

              @Override
              public void onFailure(final Throwable caught) {
                cbox.setEnabled(true);
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
