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
import com.google.gerrit.client.GerritJsApi;
import com.google.gerrit.client.GerritJsApi.NewChangeInfoTableRow;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountLinkPanel;
import com.google.gerrit.client.ui.BranchLink;
import com.google.gerrit.client.ui.CommentedActionDialog;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.client.ui.ProjectSearchLink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.clippy.client.CopyableLabel;

import java.util.List;

public class ChangeInfoBlock extends Composite {
  private static final int R_CHANGE_ID = 0;
  private static final int R_OWNER = 1;
  private static final int R_PROJECT = 2;
  private static final int R_BRANCH = 3;
  private static final int R_TOPIC = 4;
  private static final int R_UPLOADED = 5;
  private static final int R_UPDATED = 6;
  private static final int R_SUBMIT_TYPE = 7;
  private static final int R_STATUS = 8;
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
    initRow(R_SUBMIT_TYPE, Util.C.changeInfoBlockSubmitType());
    if (Gerrit.getConfig().testChangeMerge()) {
      initRow(R_MERGE_TEST, Util.C.changeInfoBlockCanMerge());
    }

    final CellFormatter fmt = table.getCellFormatter();
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(R_CHANGE_ID, 1, Gerrit.RESOURCES.css().changeid());
    fmt.addStyleName(R_CNT - 2, 0, Gerrit.RESOURCES.css().bottomheader());

    initWidget(table);
    getElement().setId("CHANGE_INFO_TABLE");
  }

  private void initRow(final int row, final String name) {
    table.setText(row, 0, name);
    table.getCellFormatter().addStyleName(row, 0, Gerrit.RESOURCES.css().header());
  }

  public void display(final Change chg, final AccountInfoCache acc,
      SubmitTypeRecord submitTypeRecord) {
    final Branch.NameKey dst = chg.getDest();

    CopyableLabel changeIdLabel =
        new CopyableLabel("Change-Id: " + chg.getKey().get());
    changeIdLabel.setPreviewText(chg.getKey().get());
    table.setWidget(R_CHANGE_ID, 1, changeIdLabel);

    table.setWidget(R_OWNER, 1, AccountLinkPanel.link(acc, chg.getOwner()));

    final FlowPanel p = new FlowPanel();
    p.add(new ProjectSearchLink(chg.getProject()));
    p.add(new InlineHyperlink(chg.getProject().get(),
        PageLinks.toProject(chg.getProject())));
    table.setWidget(R_PROJECT, 1, p);

    table.setWidget(R_BRANCH, 1, new BranchLink(dst.getShortName(), chg
        .getProject(), chg.getStatus(), dst.get(), null));
    table.setWidget(R_TOPIC, 1, topic(chg));
    table.setText(R_UPLOADED, 1, mediumFormat(chg.getCreatedOn()));
    table.setText(R_UPDATED, 1, mediumFormat(chg.getLastUpdatedOn()));
    table.setText(R_STATUS, 1, Util.toLongString(chg.getStatus()));
    String submitType;
    if (submitTypeRecord.status == SubmitTypeRecord.Status.OK) {
      submitType = com.google.gerrit.client.admin.Util
              .toLongString(submitTypeRecord.type);
    } else {
      submitType = submitTypeRecord.status.name();
    }
    table.setText(R_SUBMIT_TYPE, 1, submitType);
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
    List<NewChangeInfoTableRow> rows = GerritJsApi.informChangeInfoTableModifiers(chg.getId().get());
    for (NewChangeInfoTableRow row : rows) {
      int insertRow = table.insertRow(row.position);
      initRow(insertRow, row.title);
      table.setWidget(insertRow, 1, row.widget);
    }

    if (status.isClosed()) {
      table.getCellFormatter().addStyleName(R_STATUS, 1, Gerrit.RESOURCES.css().closedstate());
      table.getRowFormatter().setVisible(R_SUBMIT_TYPE, false);
    } else {
      table.getCellFormatter().removeStyleName(R_STATUS, 1, Gerrit.RESOURCES.css().closedstate());
      table.getRowFormatter().setVisible(R_SUBMIT_TYPE, true);
    }
  }

  public Widget topic(final Change chg) {
    final Branch.NameKey dst = chg.getDest();

    FlowPanel fp = new FlowPanel();
    fp.addStyleName(Gerrit.RESOURCES.css().changeInfoTopicPanel());
    fp.add(new BranchLink(chg.getTopic(), chg.getProject(), chg.getStatus(),
           dst.get(), chg.getTopic()));

    ChangeDetailCache detailCache = ChangeCache.get(chg.getId()).getChangeDetailCache();
    ChangeDetail changeDetail = detailCache.get();

    if (changeDetail.canEditTopicName()) {
      final Image edit = new Image(Gerrit.RESOURCES.edit());
      edit.addStyleName(Gerrit.RESOURCES.css().link());
      edit.setTitle(Util.C.changeInfoBlockTopicAlterTopicToolTip());
      edit.addClickHandler(new  ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          new AlterTopicDialog(chg).center();
        }
      });
      fp.add(edit);
    }

    return fp;
  }

  private class AlterTopicDialog extends CommentedActionDialog<ChangeDetail>
      implements KeyPressHandler {
    TextBox newTopic;
    Change change;

    AlterTopicDialog(Change chg) {
      super(Util.C.alterTopicTitle(), Util.C.headingAlterTopicMessage(),
          new ChangeDetailCache.IgnoreErrorCallback());
      change = chg;

      newTopic = new TextBox();
      newTopic.addKeyPressHandler(this);
      setFocusOn(newTopic);
      panel.insert(newTopic, 0);
      panel.insert(new InlineLabel(Util.C.alterTopicLabel()), 0);
    }

    @Override
    protected void onLoad() {
      super.onLoad();
      newTopic.setText(change.getTopic());
    }

    private void doTopicEdit() {
      String topic = newTopic.getText();
      ChangeApi.topic(change.getId().get(), topic, getMessageText(),
        new GerritCallback<String>() {
        @Override
        public void onSuccess(String result) {
          sent = true;
          Gerrit.display(PageLinks.toChange(change.getId()));
          hide();
        }

        @Override
        public void onFailure(final Throwable caught) {
          enableButtons(true);
          super.onFailure(caught);
        }});
    }

    @Override
    public void onSend() {
      doTopicEdit();
    }

    @Override
    public void onKeyPress(KeyPressEvent event) {
      if (event.getSource() == newTopic
          && event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
        doTopicEdit();
      }
    }
  }
}
