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

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountLink;
import com.google.gerrit.client.ui.AccountLink;
import com.google.gerrit.client.ui.BranchLink;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.CommentedActionDialog;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.client.ui.ItemListBox;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.ApprovalDetail;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.clippy.client.CopyableLabel;

import java.util.ArrayList;
import java.util.List;

public class ChangeInfoBlock extends Composite {
  private static final int R_CHANGE_ID = 0;
  private static final int R_OWNER = 1;
  private static final int R_PROJECT = 2;
  private static final int R_BRANCH = 3;
  private static final int R_TOPIC = 4;
  private static final int R_UPLOADED = 5;
  private static final int R_UPDATED = 6;
  private static final int R_ASSIGNED_TO = 7;
  private static final int R_STATUS = 8;
  private static final int R_MERGE_TEST = 9;
  private int R_PERMALINK = R_MERGE_TEST;
  private int R_CNT;

  private final Grid table;

  public ChangeInfoBlock() {
    if (Gerrit.getConfig().testChangeMerge()) {
      R_PERMALINK++;
    }
    R_CNT = R_PERMALINK + 1;

    table = new Grid(R_CNT, 2);

    table.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    table.addStyleName(Gerrit.RESOURCES.css().changeInfoBlock());

    initRow(R_CHANGE_ID, "Change-Id: ");
    initRow(R_OWNER, Util.C.changeInfoBlockOwner());
    initRow(R_PROJECT, Util.C.changeInfoBlockProject());
    initRow(R_BRANCH, Util.C.changeInfoBlockBranch());
    initRow(R_TOPIC, Util.C.changeInfoBlockTopic());
    initRow(R_UPLOADED, Util.C.changeInfoBlockUploaded());
    initRow(R_UPDATED, Util.C.changeInfoBlockUpdated());
    initRow(R_ASSIGNED_TO, Util.C.changeInfoBlockAssignedTo());
    initRow(R_STATUS, Util.C.changeInfoBlockStatus());
    if (Gerrit.getConfig().testChangeMerge()) {
      initRow(R_MERGE_TEST, Util.C.changeInfoBlockCanMerge());
    }

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

  public void display(final ChangeDetail detail) {
    display(detail.getChange(), detail, detail.getAccounts());
  }

  public void display(final Change chg, final AccountInfoCache acc) {
    display(chg, null, acc);
  }

  public void display(final Change chg, final ChangeDetail detail,
      final AccountInfoCache acc) {
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
    if (Gerrit.getConfig().testChangeMerge()) {
      table.setText(R_MERGE_TEST, 1, chg.isMergeable() ? Util.C
          .changeInfoBlockCanMergeYes() : Util.C.changeInfoBlockCanMergeNo());
    }
    table.setWidget(R_ASSIGNED_TO, 1, assigned(chg, acc, detail));

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

  public Widget assigned(final Change chg, final AccountInfoCache acc,
      final ChangeDetail detail) {
    FlowPanel fp = new FlowPanel();
    if (chg.getAssigned() == null) {
      fp.add(new InlineLabel(Assign.UNASSIGNED.info.getFullName()));
    } else {
      fp.add(AccountLink.link(acc, chg.getAssigned()));
    }

    if (detail != null && Gerrit.isSignedIn()) {
      final Image edit = new Image(Gerrit.RESOURCES.edit());
      edit.addClickHandler(new  ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          new AssignChangeDialog(chg, acc, detail,
              new AsyncCallback<ChangeDetail>() {
              @Override
              public void onSuccess(ChangeDetail detail) {
                ChangeCache.get(chg.getId())
                    .getChangeDetailCache().set(detail);
              }

              @Override
              public void onFailure(Throwable caught) {
              }
            }).center();
        }
      });
      fp.add(edit);
    }

    return fp;
  }

  private class UserListBox extends ItemListBox<AccountInfo> {
    public UserListBox(List<AccountInfo> accounts) {
      super(accounts);
    }

    public String getItem(AccountInfo ai) {
      return FormatUtil.nameEmail(ai);
    }

    public Account.Id getSelectedAccountId() {
      return getSelectedItem().getId();
    }
  }

  public static enum Assign {
    UNASSIGNED(null, "--unassigned--");

    public AccountInfo info;

    Assign(Account.Id aid, String fullName) {
      Account account = new Account(null);
      account.setFullName(fullName);
      info = new AccountInfo(account);
    }
  }

  private class AssignChangeDialog extends CommentedActionDialog {
    UserListBox users;
    Change change;

    AssignChangeDialog(Change chg, final AccountInfoCache acc,
        final ChangeDetail detail, AsyncCallback<ChangeDetail> cb) {
      super(Util.C.assignChangeTitle(), Util.C.headingAssignChangeMessage(),
          new ChangeDetailCache.IgnoreErrorCallback());
      sendButton.setText(Util.C.buttonAssignChangeSend());

      change = chg;
      List<ApprovalDetail> details = detail.getApprovals();
      List<AccountInfo> accounts = new ArrayList(details.size());
      accounts.add(Assign.UNASSIGNED.info);
      for (ApprovalDetail ad : details) {
        accounts.add(acc.get(ad.getAccount()));
      }

      accounts.add(new AccountInfo(Gerrit.getUserAccount()));

      users = new UserListBox(accounts);
      panel.insert(users, 0);
      panel.insert(new InlineLabel("Assign Change to:"), 0);
    }

    @Override
    public void onSend() {
      Util.MANAGE_SVC.assignChange(change.currentPatchSetId(), users.getSelectedAccountId(),
          getMessageText(), createCallback());
    }
  }
}
