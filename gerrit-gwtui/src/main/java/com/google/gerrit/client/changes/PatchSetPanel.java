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
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.PatchLink;
import com.google.gerrit.client.ui.PatchLink.SideBySide;
import com.google.gerrit.client.ui.PatchLink.Unified;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.UserIdentity;
import com.google.gerrit.reviewdb.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.AccountGeneralPreferences.DownloadUrl;
import com.google.gwt.core.client.GWT;
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
    infoTable.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    infoTable.addStyleName(Gerrit.RESOURCES.css().patchSetInfoBlock());

    initRow(R_AUTHOR, Util.C.patchSetInfoAuthor());
    initRow(R_COMMITTER, Util.C.patchSetInfoCommitter());
    initRow(R_DOWNLOAD, Util.C.patchSetInfoDownload());

    final CellFormatter itfmt = infoTable.getCellFormatter();
    itfmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    itfmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    itfmt.addStyleName(R_CNT - 1, 0, Gerrit.RESOURCES.css().bottomheader());
    itfmt.addStyleName(R_AUTHOR, 1, Gerrit.RESOURCES.css().useridentity());
    itfmt.addStyleName(R_COMMITTER, 1, Gerrit.RESOURCES.css().useridentity());
    itfmt.addStyleName(R_DOWNLOAD, 1, Gerrit.RESOURCES.css()
        .downloadLinkListCell());

    final PatchSetInfo info = detail.getInfo();
    displayUserIdentity(R_AUTHOR, info.getAuthor());
    displayUserIdentity(R_COMMITTER, info.getCommitter());
    displayDownload();


    patchTable = new PatchTable();
    patchTable.setSavePointerId("PatchTable " + patchSet.getId());
    patchTable.display(info.getKey(), detail.getPatches());

    body.add(infoTable);

    actionsPanel = new FlowPanel();
    actionsPanel.setStyleName(Gerrit.RESOURCES.css().patchSetActions());
    body.add(actionsPanel);
    if (Gerrit.isSignedIn()) {
      populateReviewAction();
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
    final CopyableLabel copyLabel = new CopyableLabel("");
    final DownloadCommandPanel commands = new DownloadCommandPanel();
    final DownloadUrlPanel urls = new DownloadUrlPanel(commands);

    copyLabel.setStyleName(Gerrit.RESOURCES.css().downloadLinkCopyLabel());

    if (changeDetail.isAllowsAnonymous()
        && Gerrit.getConfig().getGitDaemonUrl() != null) {
      StringBuilder r = new StringBuilder();
      r.append(Gerrit.getConfig().getGitDaemonUrl());
      r.append(projectName);
      r.append(" ");
      r.append(patchSet.getRefName());
      urls.add(new DownloadUrlLink(DownloadUrl.ANON_GIT, Util.M
          .anonymousDownload("Git"), r.toString()));
    }

    if (changeDetail.isAllowsAnonymous()) {
      StringBuilder r = new StringBuilder();
      r.append(GWT.getHostPageBaseURL());
      r.append("p/");
      r.append(projectName);
      r.append(" ");
      r.append(patchSet.getRefName());
      urls.add(new DownloadUrlLink(DownloadUrl.ANON_HTTP, Util.M
          .anonymousDownload("HTTP"), r.toString()));
    }

    if (Gerrit.getConfig().getSshdAddress() != null && Gerrit.isSignedIn()
        && Gerrit.getUserAccount().getUserName() != null
        && Gerrit.getUserAccount().getUserName().length() > 0) {
      String sshAddr = Gerrit.getConfig().getSshdAddress();
      final StringBuilder r = new StringBuilder();
      r.append("ssh://");
      r.append(Gerrit.getUserAccount().getUserName());
      r.append("@");
      if (sshAddr.startsWith("*:") || "".equals(sshAddr)) {
        r.append(Window.Location.getHostName());
      }
      if (sshAddr.startsWith("*")) {
        sshAddr = sshAddr.substring(1);
      }
      r.append(sshAddr);
      r.append("/");
      r.append(projectName);
      r.append(" ");
      r.append(patchSet.getRefName());
      urls.add(new DownloadUrlLink(DownloadUrl.SSH, "SSH", r.toString()));
    }

    if (Gerrit.isSignedIn() && Gerrit.getUserAccount().getUserName() != null
        && Gerrit.getUserAccount().getUserName().length() > 0) {
      String base = GWT.getHostPageBaseURL();
      int p = base.indexOf("://");
      int s = base.indexOf('/', p + 3);
      if (s < 0) {
        s = base.length();
      }
      String host = base.substring(p + 3, s);
      if (host.contains("@")) {
        host = host.substring(host.indexOf('@') + 1);
      }

      final StringBuilder r = new StringBuilder();
      r.append(base.substring(0, p + 3));
      r.append(Gerrit.getUserAccount().getUserName());
      r.append('@');
      r.append(host);
      r.append(base.substring(s));
      r.append("p/");
      r.append(projectName);
      r.append(" ");
      r.append(patchSet.getRefName());
      urls.add(new DownloadUrlLink(DownloadUrl.HTTP, "HTTP", r.toString()));
    }

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
      final String cmd = r.toString();
      commands.add(new DownloadCommandLink(DownloadCommand.REPO_DOWNLOAD,
          "repo download") {
        @Override
        void setCurrentUrl(DownloadUrlLink link) {
          urls.setVisible(false);
          copyLabel.setText(cmd);
        }
      });
    }

    if (!urls.isEmpty()) {
      commands.add(new DownloadCommandLink(DownloadCommand.CHECKOUT, "checkout") {
        @Override
        void setCurrentUrl(DownloadUrlLink link) {
          urls.setVisible(true);
          copyLabel.setText("git fetch " + link.urlData
              + " && git checkout FETCH_HEAD");
        }
      });
      commands.add(new DownloadCommandLink(DownloadCommand.PULL, "pull") {
        @Override
        void setCurrentUrl(DownloadUrlLink link) {
          urls.setVisible(true);
          copyLabel.setText("git pull " + link.urlData);
        }
      });
      commands.add(new DownloadCommandLink(DownloadCommand.CHERRY_PICK,
          "cherry-pick") {
        @Override
        void setCurrentUrl(DownloadUrlLink link) {
          urls.setVisible(true);
          copyLabel.setText("git fetch " + link.urlData
              + " && git cherry-pick FETCH_HEAD");
        }
      });
      commands.add(new DownloadCommandLink(DownloadCommand.FORMAT_PATCH,
          "patch") {
        @Override
        void setCurrentUrl(DownloadUrlLink link) {
          urls.setVisible(true);
          copyLabel.setText("git fetch " + link.urlData
              + " && git format-patch -1 --stdout FETCH_HEAD");
        }
      });
    }

    final FlowPanel fp = new FlowPanel();
    if (!commands.isEmpty()) {
      final AccountGeneralPreferences pref;
      if (Gerrit.isSignedIn()) {
        pref = Gerrit.getUserAccount().getGeneralPreferences();
      } else {
        pref = new AccountGeneralPreferences();
        pref.resetToDefaults();
      }
      commands.select(pref.getDownloadCommand());
      urls.select(pref.getDownloadUrl());

      FlowPanel p = new FlowPanel();
      p.setStyleName(Gerrit.RESOURCES.css().downloadLinkHeader());
      p.add(commands);
      final InlineLabel glue = new InlineLabel();
      glue.setStyleName(Gerrit.RESOURCES.css().downloadLinkHeaderGap());
      p.add(glue);
      p.add(urls);

      fp.add(p);
      fp.add(copyLabel);
    }
    infoTable.setWidget(R_DOWNLOAD, 1, fp);
  }

  private void displayUserIdentity(final int row, final UserIdentity who) {
    if (who == null) {
      infoTable.clearCell(row, 1);
      return;
    }

    final FlowPanel fp = new FlowPanel();
    fp.setStyleName(Gerrit.RESOURCES.css().patchSetUserIdentity());
    if (who.getName() != null) {
      final Account.Id aId = who.getAccount();
      if (aId != null) {
        fp.add(new AccountDashboardLink(who.getName(), aId));
      } else {
        final InlineLabel lbl = new InlineLabel(who.getName());
        lbl.setStyleName(Gerrit.RESOURCES.css().accountName());
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

    final Button diffAllSideBySide = new Button(Util.C.buttonDiffAllSideBySide());
    diffAllSideBySide.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        for (Patch p : detail.getPatches()) {
          SideBySide link = new PatchLink.SideBySide(p.getFileName(), p.getKey(), 0, null);
          Window.open(link.getElement().toString(), p.getFileName(), null);
        }
      }
    });
    actionsPanel.add(diffAllSideBySide);

    final Button diffAllUnified = new Button(Util.C.buttonDiffAllUnified());
    diffAllUnified.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        for (Patch p : detail.getPatches()) {
          Unified link = new PatchLink.Unified(p.getFileName(), p.getKey(), 0, null);
          Window.open(link.getElement().toString(), p.getFileName(), null);
        }
      }
    });
    actionsPanel.add(diffAllUnified);
  }

  private void populateReviewAction() {
    final Button b = new Button(Util.C.buttonReview());
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
    infoTable.getCellFormatter().addStyleName(row, 0,
        Gerrit.RESOURCES.css().header());
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
