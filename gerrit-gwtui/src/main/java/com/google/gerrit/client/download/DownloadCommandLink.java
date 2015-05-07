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

package com.google.gerrit.client.download;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.aria.client.Roles;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.VoidResult;

public abstract class DownloadCommandLink extends Anchor implements ClickHandler {
  public static class CopyableCommandLinkFactory {
    protected CopyableLabel copyLabel = null;
    protected Widget widget;

    public class CloneCommandLink extends DownloadCommandLink {
      public CloneCommandLink() {
        super(DownloadCommand.CHECKOUT, "clone");
      }

      @Override
      protected void setCurrentUrl(DownloadUrlLink link) {
        widget.setVisible(true);
        copyLabel.setText("git clone " + link.getUrlData());
      }
    }

    public class CloneWithCommitMsgHookCommandLink extends DownloadCommandLink {
      private final Project.NameKey project;

      public CloneWithCommitMsgHookCommandLink(Project.NameKey project) {
        super(DownloadCommand.CHECKOUT, "clone with commit-msg hook");
        this.project = project;
      }

      @Override
      protected void setCurrentUrl(DownloadUrlLink link) {
        widget.setVisible(true);

        String sshPort = null;
        String sshAddr = Gerrit.getConfig().getSshdAddress();
        int p = sshAddr.lastIndexOf(':');
        if (p != -1 && !sshAddr.endsWith(":")) {
          sshPort = sshAddr.substring(p + 1);
        }

        StringBuilder cmd = new StringBuilder();
        cmd.append("git clone ");
        cmd.append(link.getUrlData());
        cmd.append(" && scp -p ");
        if (sshPort != null) {
          cmd.append("-P ");
          cmd.append(sshPort);
          cmd.append(" ");
        }
        cmd.append(Gerrit.getUserAccount().getUserName());
        cmd.append("@");

        if (sshAddr.startsWith("*:") || p == -1) {
          cmd.append(Window.Location.getHostName());
        } else {
          cmd.append(sshAddr.substring(0, p));
        }

        cmd.append(":hooks/commit-msg ");

        p = project.get().lastIndexOf('/');
        if (p != -1) {
          cmd.append(project.get().substring(p + 1));
        } else {
          cmd.append(project.get());
        }

        cmd.append("/.git/hooks/");

        copyLabel.setText(cmd.toString());
      }
    }

    public CopyableCommandLinkFactory(CopyableLabel label, Widget widget) {
      copyLabel = label;
      this.widget = widget;
    }
  }

  final DownloadCommand cmdType;

  public DownloadCommandLink(DownloadCommand cmdType,
      String text) {
    super(text);
    this.cmdType = cmdType;
    setStyleName(Gerrit.RESOURCES.css().downloadLink());
    Roles.getTabRole().set(getElement());
    addClickHandler(this);
  }

  @Override
  public void onClick(ClickEvent event) {
    event.preventDefault();
    event.stopPropagation();

    select();

    if (Gerrit.isSignedIn()) {
      // If the user is signed-in, remember this choice for future panels.
      //
      AccountGeneralPreferences pref =
          Gerrit.getUserAccount().getGeneralPreferences();
      pref.setDownloadCommand(cmdType);
      com.google.gerrit.client.account.Util.ACCOUNT_SVC.changePreferences(pref,
          new AsyncCallback<VoidResult>() {
            @Override
            public void onFailure(Throwable caught) {
            }

            @Override
            public void onSuccess(VoidResult result) {
            }
          });
    }
  }

  public DownloadCommand getCmdType() {
    return cmdType;
  }

  void select() {
    DownloadCommandPanel parent = (DownloadCommandPanel) getParent();
    for (Widget w : parent) {
      if (w != this && w instanceof DownloadCommandLink) {
        w.removeStyleName(Gerrit.RESOURCES.css().downloadLink_Active());
      }
    }
    parent.setCurrentCommand(this);
    addStyleName(Gerrit.RESOURCES.css().downloadLink_Active());
  }

  protected abstract void setCurrentUrl(DownloadUrlLink link);
}
