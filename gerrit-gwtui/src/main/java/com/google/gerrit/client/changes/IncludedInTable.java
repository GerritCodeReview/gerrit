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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.data.IncludedInDetail;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Topic;
import com.google.gwt.event.logical.shared.OpenEvent;
import com.google.gwt.event.logical.shared.OpenHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;


/** Displays a table of Branches and Tags containing the change record. */
public class IncludedInTable extends Composite implements
    OpenHandler<DisclosurePanel> {
  private final Grid table;
  private final Change.Id changeId;
  private final Topic.Id topicId;
  private boolean loaded = false;

  public IncludedInTable(final Change.Id chId) {
    changeId = chId;
    topicId = null;
    table = new Grid(1, 1);
    initWidget(table);
  }

  public IncludedInTable(final Topic.Id tId) {
    changeId = null;
    topicId = tId;
    table = new Grid(1, 1);
    initWidget(table);
  }

  public void loadTable(final IncludedInDetail detail) {
    int row = 0;
    table.resizeRows(detail.getBranches().size() + 1);
    table.addStyleName(Gerrit.RESOURCES.css().changeTable());
    final CellFormatter fmt = table.getCellFormatter();
    fmt.addStyleName(row, 0, Gerrit.RESOURCES.css().dataHeader());
    table.setText(row, 0, Util.C.includedInTableBranch());

    for (final String branch : detail.getBranches()) {
      fmt.addStyleName(++row, 0, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 0, Gerrit.RESOURCES.css().leftMostCell());
      table.setText(row, 0, branch);
    }

    if (!detail.getTags().isEmpty()) {
      table.resizeRows(table.getRowCount() + 2 + detail.getTags().size());
      row++;
      fmt.addStyleName(++row, 0, Gerrit.RESOURCES.css().dataHeader());
      table.setText(row, 0, Util.C.includedInTableTag());

      for (final String tag : detail.getTags()) {
        fmt.addStyleName(++row, 0, Gerrit.RESOURCES.css().dataCell());
        fmt.addStyleName(row, 0, Gerrit.RESOURCES.css().leftMostCell());
        table.setText(row, 0, tag);
      }
    }

    table.setVisible(true);
    loaded = true;
  }

  @Override
  public void onOpen(OpenEvent<DisclosurePanel> event) {
    if (!loaded) {
      if (changeId != null) {
        Util.DETAIL_SVC.includedInDetail(changeId,
            new GerritCallback<IncludedInDetail>() {
              @Override
              public void onSuccess(final IncludedInDetail result) {
                loadTable(result);
              }
            });
      } else {
        Util.T_DETAIL_SVC.includedInDetail(topicId,
            new GerritCallback<IncludedInDetail>() {
              @Override
              public void onSuccess(final IncludedInDetail result) {
                loadTable(result);
              }
            });
      }
    }
  }
}
