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

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.ComplexDisclosurePanel;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.AccountGeneralPreferences.DownloadScheme;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.UserIdentity;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.OpenHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwtexpui.clippy.client.CopyableLabel;

import java.util.Set;

abstract class CommonComplexDisclosurePanel extends ComplexDisclosurePanel implements OpenHandler<DisclosurePanel> {
  protected Grid infoTable;

  public CommonComplexDisclosurePanel(String text, boolean isOpen) {
    super(text, isOpen);
  }

  protected void displayDownload(final Project.NameKey projectKey,
      final boolean isAllowsAnonymous, final String refname,
      final int entityId, final int setId,
      final int downloadRow) {
    final String projectName = projectKey.get();
    final CopyableLabel copyLabel = new CopyableLabel("");
    final DownloadCommandPanel commands = new DownloadCommandPanel();
    final DownloadUrlPanel urls = new DownloadUrlPanel(commands);
    final Set<DownloadScheme> allowedSchemes = Gerrit.getConfig().getDownloadSchemes();

    copyLabel.setStyleName(Gerrit.RESOURCES.css().downloadLinkCopyLabel());

    if (isAllowsAnonymous
        && Gerrit.getConfig().getGitDaemonUrl() != null
        && (allowedSchemes.contains(DownloadScheme.ANON_GIT) ||
            allowedSchemes.contains(DownloadScheme.DEFAULT_DOWNLOADS))) {
      StringBuilder r = new StringBuilder();
      r.append(Gerrit.getConfig().getGitDaemonUrl());
      r.append(projectName);
      r.append(" ");
      r.append(refname);
      urls.add(new DownloadUrlLink(DownloadScheme.ANON_GIT, Util.M
          .anonymousDownload("Git"), r.toString()));
    }

    if (isAllowsAnonymous
        && (allowedSchemes.contains(DownloadScheme.ANON_HTTP) ||
            allowedSchemes.contains(DownloadScheme.DEFAULT_DOWNLOADS))) {
      StringBuilder r = new StringBuilder();
      r.append(GWT.getHostPageBaseURL());
      r.append("p/");
      r.append(projectName);
      r.append(" ");
      r.append(refname);
      urls.add(new DownloadUrlLink(DownloadScheme.ANON_HTTP, Util.M
          .anonymousDownload("HTTP"), r.toString()));
    }

    if (Gerrit.getConfig().getSshdAddress() != null && Gerrit.isSignedIn()
        && Gerrit.getUserAccount().getUserName() != null
        && Gerrit.getUserAccount().getUserName().length() > 0
        && (allowedSchemes.contains(DownloadScheme.SSH) ||
            allowedSchemes.contains(DownloadScheme.DEFAULT_DOWNLOADS))) {
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
      r.append(refname);
      urls.add(new DownloadUrlLink(DownloadScheme.SSH, "SSH", r.toString()));
    }

    if (Gerrit.isSignedIn() && Gerrit.getUserAccount().getUserName() != null
        && Gerrit.getUserAccount().getUserName().length() > 0
        && (allowedSchemes.contains(DownloadScheme.HTTP) ||
            allowedSchemes.contains(DownloadScheme.DEFAULT_DOWNLOADS))) {
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
      r.append(refname);
      urls.add(new DownloadUrlLink(DownloadScheme.HTTP, "HTTP", r.toString()));
    }

    if (allowedSchemes.contains(DownloadScheme.REPO_DOWNLOAD)) {
      // This site prefers usage of the 'repo' tool, so suggest
      // that for easy fetch.
      //
      final StringBuilder r = new StringBuilder();
      r.append("repo download ");
      r.append(projectName);
      r.append(" ");
      r.append(entityId);
      r.append("/");
      r.append(setId);
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
    infoTable.setWidget(downloadRow, 1, fp);
  }

  protected void displayUserIdentity(final int row, final UserIdentity who) {
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

  protected void initRow(final int row, final String name) {
    infoTable.setText(row, 0, name);
    infoTable.getCellFormatter().addStyleName(row, 0,
        Gerrit.RESOURCES.css().header());
  }
}
