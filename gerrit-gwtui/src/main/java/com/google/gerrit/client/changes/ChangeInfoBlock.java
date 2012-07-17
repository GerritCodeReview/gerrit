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
import com.google.gerrit.client.ui.AccountLink;
import com.google.gerrit.client.ui.BranchLink;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.UListElement;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwtexpui.clippy.client.CopyableLabel;

import java.util.Set;

public class ChangeInfoBlock extends Composite {
  private static final int R_CHANGE_ID = 0;
  private static final int R_OWNER = 1;
  private static final int R_PROJECT = 2;
  private static final int R_BRANCH = 3;
  private static final int R_TOPIC = 4;
  private static final int R_UPLOADED = 5;
  private static final int R_UPDATED = 6;
  private static final int R_STATUS = 7;
  private static final int R_ERRORS = 8;
  private static final int R_MERGE_TEST = 9;
  private static final int R_CNT = 10;

  private final Grid table;

  public ChangeInfoBlock() {
    if (Gerrit.getConfig().testChangeMerge()) {
      table = new Grid(R_CNT, 2);
    } else {
      table = new Grid(R_CNT - 1, 2);
    }
    table.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    table.addStyleName(Gerrit.RESOURCES.css().changeInfoBlock());

    initRow(R_CHANGE_ID, "Change-Id: ");
    initRow(R_OWNER, Util.C.changeInfoBlockOwner());
    initRow(R_PROJECT, Util.C.changeInfoBlockProject());
    initRow(R_BRANCH, Util.C.changeInfoBlockBranch());
    initRow(R_TOPIC, Util.C.changeInfoBlockTopic());
    initRow(R_UPLOADED, Util.C.changeInfoBlockUploaded());
    initRow(R_UPDATED, Util.C.changeInfoBlockUpdated());
    initRow(R_STATUS, Util.C.changeInfoBlockStatus());
    initRow(R_ERRORS, Util.C.changeInfoErrors());
    if (Gerrit.getConfig().testChangeMerge()) {
      initRow(R_MERGE_TEST, Util.C.changeInfoBlockCanMerge());
    }

    final CellFormatter fmt = table.getCellFormatter();
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(R_CHANGE_ID, 1, Gerrit.RESOURCES.css().changeid());
    fmt.addStyleName(R_CNT - 2, 0, Gerrit.RESOURCES.css().bottomheader());

    initWidget(table);
  }

  private void initRow(final int row, final String name) {
    table.setText(row, 0, name);
    table.getCellFormatter().addStyleName(row, 0, Gerrit.RESOURCES.css().header());
  }

  public void display(final Change chg, final AccountInfoCache acc,
      Set<Change.DependencyError> errors) {
    final Branch.NameKey dst = chg.getDest();

    CopyableLabel changeIdLabel =
        new CopyableLabel("Change-Id: " + chg.getKey().get());
    changeIdLabel.setPreviewText(chg.getKey().get());
    table.setWidget(R_CHANGE_ID, 1, changeIdLabel);

    table.setWidget(R_OWNER, 1, AccountLink.link(acc, chg.getOwner()));
    table.setWidget(R_PROJECT, 1, new ProjectLink(chg.getProject(), chg.getStatus()));
    table.setWidget(R_BRANCH, 1, new BranchLink(dst.getShortName(), chg
        .getProject(), chg.getStatus(), dst.get(), null));
    table.setWidget(R_TOPIC, 1, new BranchLink(chg.getTopic(),
        chg.getProject(), chg.getStatus(), dst.get(), chg.getTopic()));
    table.setText(R_UPLOADED, 1, mediumFormat(chg.getCreatedOn()));
    table.setText(R_UPDATED, 1, mediumFormat(chg.getLastUpdatedOn()));
    table.setText(R_STATUS, 1, Util.toLongString(chg.getStatus()));

    if (errors != null && !errors.isEmpty()) {
      table.setWidget(R_ERRORS, 1, new Widget() {
        {
          setElement(DOM.createElement("ul"));
        }
      });

      for (Change.DependencyError error : errors) {
        Element li = Document.get().createLIElement().cast();
        li.setInnerText(error.toString().replace("_", " "));
        table.getWidget(R_ERRORS, 1).getElement().appendChild(li);
      }
    } else {
      table.getRowFormatter().setVisible(R_ERRORS, false);
    }

    final Change.Status status = chg.getStatus();
    if (Gerrit.getConfig().testChangeMerge()) {
      if (status.equals(Change.Status.NEW) || status.equals(Change.Status.DRAFT)) {
        table.getRowFormatter().setVisible(R_MERGE_TEST, true);
        table.setText(R_MERGE_TEST, 1, chg.isMergeable() ? Util.C
            .changeInfoBlockCanMergeYes() : Util.C.changeInfoBlockCanMergeNo());
      } else {
        table.getRowFormatter().setVisible(R_MERGE_TEST, false);
      }
    }

    if (status.isClosed()) {
      table.getCellFormatter().addStyleName(R_STATUS, 1, Gerrit.RESOURCES.css().closedstate());
    } else {
      table.getCellFormatter().removeStyleName(R_STATUS, 1, Gerrit.RESOURCES.css().closedstate());
    }
  }
}
