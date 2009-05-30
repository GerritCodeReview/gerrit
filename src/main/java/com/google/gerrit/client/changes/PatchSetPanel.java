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
import com.google.gerrit.client.SignOutEvent;
import com.google.gerrit.client.SignOutHandler;
import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.ChangeDetail;
import com.google.gerrit.client.data.PatchSetDetail;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.UserIdentity;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.RefreshListener;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.OpenEvent;
import com.google.gwt.event.logical.shared.OpenHandler;
import com.google.gwt.event.shared.HandlerRegistration;
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
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class PatchSetPanel extends Composite implements OpenHandler<DisclosurePanel> {
  private static final int R_AUTHOR = 0;
  private static final int R_COMMITTER = 1;
  private static final int R_DOWNLOAD = 2;
  private static final int R_CNT = 3;

  private final ChangeDetail changeDetail;
  private final PatchSet patchSet;
  private final FlowPanel body;
  private List<RefreshListener> refreshListeners;

  private Grid infoTable;
  private Panel actionsPanel;
  private PatchTable patchTable;
  private HandlerRegistration regSignOut;

  PatchSetPanel(final ChangeDetail detail, final PatchSet ps) {
    changeDetail = detail;
    patchSet = ps;
    body = new FlowPanel();
    initWidget(body);
  }

  public void addRefreshListener(final RefreshListener r) {
    if (refreshListeners == null) {
      refreshListeners = new ArrayList<RefreshListener>();
    }
    if (!refreshListeners.contains(r)) {
      refreshListeners.add(r);
    }
  }

  public void removeRefreshListener(final RefreshListener r) {
    if (refreshListeners != null) {
      refreshListeners.remove(r);
    }
  }

  protected void fireOnSuggestRefresh() {
    if (refreshListeners != null) {
      for (final RefreshListener r : refreshListeners) {
        r.onSuggestRefresh();
      }
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    if (regSignOut == null && Gerrit.isSignedIn()) {
      regSignOut = Gerrit.addSignOutHandler(new SignOutHandler() {
        public void onSignOut(final SignOutEvent event) {
          actionsPanel.clear();
          actionsPanel.setVisible(false);
          regSignOut.removeHandler();
          regSignOut = null;
        }
      });
    }
  }

  @Override
  protected void onUnload() {
    if (regSignOut != null) {
      regSignOut.removeHandler();
      regSignOut = null;
    }
    super.onUnload();
  }

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
        if (changeDetail.canAbandon()) {
          populateAbandonAction();
        }
      }
    }
    body.add(patchTable);
  }

  private void displayDownload() {
    final Branch.NameKey branchKey = changeDetail.getChange().getDest();
    final Project.NameKey projectKey = branchKey.getParentKey();
    final String projectName = projectKey.get();
    final FlowPanel downloads = new FlowPanel();

    if (Common.getGerritConfig().isUseRepoDownload()) {
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
        && Common.getGerritConfig().getGitDaemonUrl() != null) {
      // Anonymous Git is claimed to be available, and this project
      // isn't secured. The anonymous Git daemon will be much more
      // efficient than our own SSH daemon, so prefer offering it.
      //
      final StringBuilder r = new StringBuilder();
      r.append("git pull ");
      r.append(Common.getGerritConfig().getGitDaemonUrl());
      r.append(projectName);
      r.append(" ");
      r.append(patchSet.getRefName());
      downloads.add(new CopyableLabel(r.toString()));

    } else if (Gerrit.isSignedIn() && Gerrit.getUserAccount() != null
        && Gerrit.getUserAccount().getSshUserName() != null
        && Gerrit.getUserAccount().getSshUserName().length() > 0) {
      // The user is signed in and anonymous access isn't allowed.
      // Use our SSH daemon URL as its the only way they can get
      // to the project (that we know of anyway).
      //
      final String sshAddr = Common.getGerritConfig().getSshdAddress();
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
    if (changeDetail.getChange().getStatus().isClosed()) {
      // Generic actions aren't allowed on closed changes.
      //
      return;
    }

    final Set<ApprovalCategory.Id> allowed = changeDetail.getCurrentActions();
    if (allowed == null) {
      // No set of actions, perhaps the user is not signed in?
      return;
    }

    for (final ApprovalType at : Common.getGerritConfig().getActionTypes()) {
      final ApprovalCategoryValue max = at.getMax();
      if (max == null || max.getValue() <= 0) {
        // No positive assertion, don't draw a button.
        continue;
      }
      if (!allowed.contains(at.getCategory().getId())) {
        // User isn't permitted to invoke this.
        continue;
      }

      final Button b =
          new Button(Util.M.patchSetAction(at.getCategory().getName(), detail
              .getPatchSet().getPatchSetId()));
      b.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          b.setEnabled(false);
          Util.MANAGE_SVC.patchSetAction(max.getId(), patchSet.getId(),
              new GerritCallback<VoidResult>() {
                public void onSuccess(VoidResult result) {
                  actionsPanel.remove(b);
                  fireOnSuggestRefresh();
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
  }

  private void populateAbandonAction() {
    final Button b = new Button(Util.C.buttonAbandonChangeBegin());
    b.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        new AbandonChangeDialog(patchSet.getId(), new AsyncCallback<Object>() {
          public void onSuccess(Object result) {
            actionsPanel.remove(b);
            fireOnSuggestRefresh();
          }

          public void onFailure(Throwable caught) {
          }
        }).center();
      }
    });
    actionsPanel.add(b);
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
}
