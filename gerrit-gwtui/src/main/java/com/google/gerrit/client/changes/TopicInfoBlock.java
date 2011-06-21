// Copyright (C) 2011 The Android Open Source Project
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
import com.google.gerrit.client.ui.BranchLink;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.client.ui.TopicLink;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Topic;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.clippy.client.CopyableLabel;

public class TopicInfoBlock extends Composite {
  private static final int R_TOPIC_ID = 0;
  private static final int R_OWNER = 1;
  private static final int R_PROJECT = 2;
  private static final int R_BRANCH = 3;
  private static final int R_UPLOADED = 4;
  private static final int R_UPDATED = 5;
  private static final int R_STATUS = 6;
  private static final int R_PERMALINK = 7;
  private static final int R_CNT = 8;

  private final Grid table;

  public TopicInfoBlock() {
    table = new Grid(R_CNT, 2);
    table.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    table.addStyleName(Gerrit.RESOURCES.css().changeInfoBlock());

    initRow(R_TOPIC_ID, "Topic-Id: ");
    initRow(R_OWNER, Util.C.changeInfoBlockOwner());
    initRow(R_PROJECT, Util.C.changeInfoBlockProject());
    initRow(R_BRANCH, Util.C.changeInfoBlockBranch());
    initRow(R_UPLOADED, Util.C.changeInfoBlockUploaded());
    initRow(R_UPDATED, Util.C.changeInfoBlockUpdated());
    initRow(R_STATUS, Util.C.changeInfoBlockStatus());

    final CellFormatter fmt = table.getCellFormatter();
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(R_TOPIC_ID, 1, Gerrit.RESOURCES.css().changeid());
    fmt.addStyleName(R_CNT - 2, 0, Gerrit.RESOURCES.css().bottomheader());
    fmt.addStyleName(R_PERMALINK, 0, Gerrit.RESOURCES.css().permalink());
    fmt.addStyleName(R_PERMALINK, 1, Gerrit.RESOURCES.css().permalink());

    initWidget(table);
  }

  private void initRow(final int row, final String name) {
    table.setText(row, 0, name);
    table.getCellFormatter().addStyleName(row, 0, Gerrit.RESOURCES.css().header());
  }

  public void display(final Topic topic, final AccountInfoCache acc) {
    final Branch.NameKey dst = topic.getDest();

    CopyableLabel changeIdLabel =
        new CopyableLabel("Topic-Id: " + topic.getKey().get());
    changeIdLabel.setPreviewText(topic.getKey().get());
    table.setWidget(R_TOPIC_ID, 1, changeIdLabel);

    table.setWidget(R_OWNER, 1, AccountDashboardLink.link(acc, topic.getOwner()));
    table.setWidget(R_PROJECT, 1, new ProjectLink(topic.getProject(), topic.getStatus()));
    table.setWidget(R_BRANCH, 1, new BranchLink(dst.getShortName(), topic.getProject(), topic.getStatus(), dst.get(), null));
    table.setText(R_UPLOADED, 1, mediumFormat(topic.getCreatedOn()));
    table.setText(R_UPDATED, 1, mediumFormat(topic.getLastUpdatedOn()));
    table.setText(R_STATUS, 1, Util.toLongString(topic.getStatus()));

    if (topic.getStatus().isClosed()) {
      table.getCellFormatter().addStyleName(R_STATUS, 1, Gerrit.RESOURCES.css().closedstate());
    } else {
      table.getCellFormatter().removeStyleName(R_STATUS, 1, Gerrit.RESOURCES.css().closedstate());
    }

    final FlowPanel fp = new FlowPanel();
    fp.add(new TopicLink(Util.C.changePermalink(), topic.getId()));
    fp.add(new CopyableLabel(TopicLink.permalink(topic.getId()), false));
    table.setWidget(R_PERMALINK, 1, fp);
  }
}
