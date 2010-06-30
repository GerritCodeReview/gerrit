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

package com.google.gerrit.client.changes;

import static com.google.gerrit.client.FormatUtil.mediumFormat;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.ComplexPanel;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.clippy.client.CopyableLabel;

public class ChangeInfoBlock extends Composite {
  private static final int R_CHANGE_ID = 0;
  private static final int R_OWNER = 1;
  private static final int R_PROJECT = 2;
  private static final int R_BRANCH = 3;
  private static final int R_UPLOADED = 4;
  private static final int R_UPDATED = 5;
  private static final int R_STATUS = 6;
  private static final int R_PERMALINK = 7;
  private static final int R_CNT = 8;

  private final Grid table;

  public ChangeInfoBlock() {
    table = new Grid(R_CNT, 2);
    table.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    table.addStyleName(Gerrit.RESOURCES.css().changeInfoBlock());

    initRow(R_CHANGE_ID, "Change-Id: ");
    initRow(R_OWNER, Util.C.changeInfoBlockOwner());
    initRow(R_PROJECT, Util.C.changeInfoBlockProject());
    initRow(R_BRANCH, Util.C.changeInfoBlockBranch());
    initRow(R_UPLOADED, Util.C.changeInfoBlockUploaded());
    initRow(R_UPDATED, Util.C.changeInfoBlockUpdated());
    initRow(R_STATUS, Util.C.changeInfoBlockStatus());

    final CellFormatter fmt = table.getCellFormatter();
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(R_CHANGE_ID, 1, Gerrit.RESOURCES.css().changeid());
    fmt.addStyleName(R_CNT - 2, 0, Gerrit.RESOURCES.css().bottomheader());
    fmt.addStyleName(R_PERMALINK, 0, Gerrit.RESOURCES.css().permalink());
    fmt.addStyleName(R_PERMALINK, 1, Gerrit.RESOURCES.css().permalink());

    initWidget(table);
  }

  private void initRow(final int row, final String name) {
    table.setText(row, 0, name);
    table.getCellFormatter().addStyleName(row, 0, Gerrit.RESOURCES.css().header());
  }

  public void display(final Change chg, final AccountInfoCache acc) {
    final Branch.NameKey dst = chg.getDest();

    /*
     * Display the change-id together with a copy to clipboard button.
     * The copy to clipboard button will copy the Change-ID line into the clipboard.
     * It is important that selecting and copying the Change-ID row in the table
     * can also be used to copy a valid Change-ID line. This is why we can't use a
     * FlowPanel here for displaying the change-id and the copy to clipboard button.
     * Using a FlowPanel would result in inserting a newline between the Change-ID
     * tag and the change-id when selecting and copying.
     */
    final ComplexPanel changeIdPanel = new ComplexPanel() {
      {
        // use span element (which is an inline element) to avoid inserting of a newline
        // between the Change-ID tag and the change-id when selecting and copying
        setElement(DOM.createSpan());
        add(new InlineLabel(chg.getKey().get()), getElement());
        add(new CopyableLabel("Change-Id: " + chg.getKey().get(), false), getElement());
      }
    };
    table.setWidget(R_CHANGE_ID, 1, changeIdPanel);

    table.setWidget(R_OWNER, 1, AccountDashboardLink.link(acc, chg.getOwner()));
    table.setWidget(R_PROJECT, 1, new ProjectLink(chg.getProject(), chg.getStatus()));
    table.setText(R_BRANCH, 1, dst.getShortName());
    table.setText(R_UPLOADED, 1, mediumFormat(chg.getCreatedOn()));
    table.setText(R_UPDATED, 1, mediumFormat(chg.getLastUpdatedOn()));
    table.setText(R_STATUS, 1, Util.toLongString(chg.getStatus()));

    if (chg.getStatus().isClosed()) {
      table.getCellFormatter().addStyleName(R_STATUS, 1, Gerrit.RESOURCES.css().closedstate());
    } else {
      table.getCellFormatter().removeStyleName(R_STATUS, 1, Gerrit.RESOURCES.css().closedstate());
    }

    final FlowPanel fp = new FlowPanel();
    fp.add(new ChangeLink(Util.C.changePermalink(), chg.getId()));
    fp.add(new CopyableLabel(ChangeLink.permalink(chg.getId()), false));
    table.setWidget(R_PERMALINK, 1, fp);
  }
}

