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

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.data.ChangeDetail;
import com.google.gerrit.client.data.PatchSetDetail;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.UserIdentity;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.OpenEvent;
import com.google.gwt.event.logical.shared.OpenHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.clippy.client.CopyableLabel;

import java.util.Collections;
import java.util.Set;

class PatchSetPanel extends Composite implements OpenHandler<DisclosurePanel> {
  private static final int R_AUTHOR = 0;
  private static final int R_COMMITTER = 1;
  private static final int R_DOWNLOAD = 2;
  private static final int R_CNT = 3;

  private final ChangeScreen changeScreen;
  private final ChangeDetail changeDetail;
  private final PatchSet patchSet;
  private final FlowPanel body;

  private Grid infoTable;
  private Panel actionsPanel;
  private PatchTable patchTable;

  PatchSetPanel(final ChangeScreen parent, final ChangeDetail detail,
      final PatchSet ps) {
    changeScreen = parent;
    changeDetail = detail;
    patchSet = ps;
    body = new FlowPanel();
    initWidget(body);
  }

  /**
   * Display the table showing the Author, Committer and Download links,
   * followed by the action buttons.
   */
  public void ensureLoaded(final PatchSetDetail detail) {
    infoTable = new Grid(R_CNT, 2);
    infoTable.setStyleName("gerrit-InfoBlock");
    infoTable.addStyleName("gerrit-PatchSetInfoBlock");

    initRow(R_AUTHOR, Util.C.patchSetInfoAuthor());
    initRow(R_COMMITTER, Util.C.patchSetInfoCommitter());
    initRow(R_DOWNLOAD, Util.C.patchSetInfoDownload());

    final CellFormatter itfmt = infoTable.getCellFormatter();
    itfmt.addStyleName(0, 0, "topmost");
    itfmt.addStyleName(0, 1, "topmost");
    itfmt.addStyleName(R_CNT - 1, 0, "bottomheader");
    itfmt.addStyleName(R_AUTHOR, 1, "useridentity");
    itfmt.addStyleName(R_COMMITTER, 1, "useridentity");
    itfmt.addStyleName(R_DOWNLOAD, 1, "command");

    final PatchSetInfo info = detail.getInfo();
    displayUserIdentity(R_AUTHOR, info.getAuthor());
    displayUserIdentity(R_COMMITTER, info.getCommitter());
    displayDownload();


    patchTable = new PatchTable();
    patchTable.setSavePointerId("PatchTable " + patchSet.getId());
    patchTable.display(info.getKey(), detail.getPatches());

    body.add(infoTable);

    actionsPanel = new FlowPanel();
    actionsPanel.setStyleName("gerrit-PatchSetActions");
    body.add(actionsPanel);
    if (Gerrit.isSignedIn()) {
      populateCommentAction();
      if (changeDetail.isCurrentPatchSet(detail)) {
        populateActions(detail);
      }
    }
    body.add(patchTable);
  }

  private void displayDownload() {
    final Branch.NameKey branchKey = changeDetail.getChange().getDest();
    final Project.NameKey projectKey = changeDetail.getChange().getProject();
    final String projectName = projectKey.get();
    final FlowPanel downloads = new FlowPanel();

    if (Gerrit.getConfig().isUseRepoDownload()) {
      // This site prefers usage of the 'repo' tool, so suggest
      // that for easy fetch.
      //
      final StringBuilder r = new StringBuilder();
      r.append("repo download ");
      r.append(projectName);
      r.append(" ");
      r.append(changeDetail.getChange().getChangeId());
      r.append("/");
      r.append(patchSet.getPatchSetId());
      downloads.add(new CopyableLabel(r.toString()));
    }

    if (changeDetail.isAllowsAnonymous()
        && Gerrit.getConfig().getGitDaemonUrl() != null) {
      // Anonymous Git is claimed to be available, and this project
      // isn't secured. The anonymous Git daemon will be much more
      // efficient than our own SSH daemon, so prefer offering it.
      //
      final StringBuilder r = new StringBuilder();
      r.append("git pull ");
      r.append(Gerrit.getConfig().getGitDaemonUrl());
      r.append(projectName);
      r.append(" ");
      r.append(patchSet.getRefName());
      downloads.add(new CopyableLabel(r.toString()));

    } else if (Gerrit.isSignedIn() && Gerrit.getUserAccount() != null
        && Gerrit.getConfig().getSshdAddress() != null
        && Gerrit.getUserAccount().getSshUserName() != null
        && Gerrit.getUserAccount().getSshUserName().length() > 0) {
      // The user is signed in and anonymous access isn't allowed.
      // Use our SSH daemon URL as its the only way they can get
      // to the project (that we know of anyway).
      //
      final String sshAddr = Gerrit.getConfig().getSshdAddress();
      final StringBuilder r = new StringBuilder();
      r.append("git pull ssh://");
      r.append(Gerrit.getUserAccount().getSshUserName());
      r.append("@");
      if (sshAddr.startsWith(":") || "".equals(sshAddr)) {
        r.append(Window.Location.getHostName());
      }
      r.append(sshAddr);
      r.append("/");
      r.append(projectName);
      r.append(" ");
      r.append(patchSet.getRefName());
      downloads.add(new CopyableLabel(r.toString()));
    }

    infoTable.setWidget(R_DOWNLOAD, 1, downloads);
  }

  private void displayUserIdentity(final int row, final UserIdentity who) {
    if (who == null) {
      infoTable.clearCell(row, 1);
      return;
    }

    final FlowPanel fp = new FlowPanel();
    fp.setStyleName("gerrit-PatchSetUserIdentity");
    if (who.getName() != null) {
      final Account.Id aId = who.getAccount();
      if (aId != null) {
        fp.add(new AccountDashboardLink(who.getName(), aId));
      } else {
        final InlineLabel lbl = new InlineLabel(who.getName());
        lbl.setStyleName("gerrit-AccountName");
        fp.add(lbl);
      }
    }
    if (who.getEmail() != null) {
      fp.add(new InlineLabel("<" + who.getEmail() + ">"));
    }
    if (who.getDate() != null) {
      fp.add(new InlineLabel(FormatUtil.mediumFormat(who.getDate())));
    }
    infoTable.setWidget(row, 1, fp);
  }

  private void populateActions(final PatchSetDetail detail) {
    final boolean isOpen = changeDetail.getChange().getStatus().isOpen();
    Set<ApprovalCategory.Id> allowed = changeDetail.getCurrentActions();
    if (allowed == null) {
      allowed = Collections.emptySet();
    }

    if (isOpen && allowed.contains(ApprovalCategory.SUBMIT)) {
      final Button b =
          new Button(Util.M
              .submitPatchSet(detail.getPatchSet().getPatchSetId()));
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          Util.MANAGE_SVC.submit(patchSet.getId(),
              new GerritCallback<ChangeDetail>() {
                public void onSuccess(ChangeDetail result) {
                  onSubmitResult(result);
                }

                @Override
                public void onFailure(Throwable caught) {
                  b.setEnabled(true);
                  super.onFailure(caught);
                }
              });
        }
      });
      actionsPanel.add(b);
    }

    if (changeDetail.canAbandon()) {
      final Button b = new Button(Util.C.buttonAbandonChangeBegin());
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          new AbandonChangeDialog(patchSet.getId(),
              new AsyncCallback<ChangeDetail>() {
                public void onSuccess(ChangeDetail result) {
                  changeScreen.display(result);
                }

                public void onFailure(Throwable caught) {
                  b.setEnabled(true);
                }
              }).center();
        }
      });
      actionsPanel.add(b);
    }
  }

  private void populateCommentAction() {
    final Button b = new Button(Util.C.buttonPublishCommentsBegin());
    b.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        Gerrit.display("change,publish," + patchSet.getId().toString(),
            new PublishCommentScreen(patchSet.getId()));
      }
    });
    actionsPanel.add(b);
  }

  @Override
  public void onOpen(final OpenEvent<DisclosurePanel> event) {
    if (infoTable == null) {
      Util.DETAIL_SVC.patchSetDetail(patchSet.getId(),
          new GerritCallback<PatchSetDetail>() {
            public void onSuccess(final PatchSetDetail result) {
              ensureLoaded(result);
            }
          });
    }
  }

  private void initRow(final int row, final String name) {
    infoTable.setText(row, 0, name);
    infoTable.getCellFormatter().addStyleName(row, 0, "header");
  }

  private void onSubmitResult(final ChangeDetail result) {
    if (result.getChange().getStatus() == Change.Status.NEW) {
      // The submit failed. Try to locate the message and display
      // it to the user, it should be the last one created by Gerrit.
      //
      ChangeMessage msg = null;
      if (result.getMessages() != null && result.getMessages().size() > 0) {
        for (int i = result.getMessages().size() - 1; i >= 0; i--) {
          if (result.getMessages().get(i).getAuthor() == null) {
            msg = result.getMessages().get(i);
            break;
          }
        }
      }

      if (msg != null) {
        new SubmitFailureDialog(result, msg).center();
      }
    }
    changeScreen.display(result);
  }
}
