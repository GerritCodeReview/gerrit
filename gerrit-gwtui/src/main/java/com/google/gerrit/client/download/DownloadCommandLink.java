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
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.aria.client.Roles;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.VoidResult;

public abstract class DownloadCommandLink extends Anchor implements ClickHandler {
  public static class CopyableCommandLinkFactory {
    protected CopyableLabel copyLabel = null;
    protected Widget widget;

    public class CheckoutCommandLink extends DownloadCommandLink {
      public CheckoutCommandLink () {
        super(DownloadCommand.CHECKOUT, "checkout");
      }

      @Override
      protected void setCurrentUrl(DownloadUrlLink link) {
        widget.setVisible(true);
        copyLabel.setText("git fetch " + link.getUrlData()
            + " && git checkout FETCH_HEAD");
      }
    }

    public class PullCommandLink extends DownloadCommandLink {
      public PullCommandLink() {
        super(DownloadCommand.PULL, "pull");
      }

      @Override
      protected void setCurrentUrl(DownloadUrlLink link) {
        widget.setVisible(true);
        copyLabel.setText("git pull " + link.getUrlData());
      }
    }

    public class CherryPickCommandLink extends DownloadCommandLink {
      public CherryPickCommandLink() {
        super(DownloadCommand.CHERRY_PICK, "cherry-pick");
      }

      @Override
      protected void setCurrentUrl(DownloadUrlLink link) {
        widget.setVisible(true);
        copyLabel.setText("git fetch " + link.getUrlData()
            + " && git cherry-pick FETCH_HEAD");
      }
    }

    public class FormatPatchCommandLink extends DownloadCommandLink {
      private final PatchSet.Id id;

      public FormatPatchCommandLink(PatchSet.Id id) {
        super(DownloadCommand.FORMAT_PATCH, "patch");
        this.id = id;
      }

      @Override
      protected void setCurrentUrl(DownloadUrlLink link) {
        String cmd;
        if (link.urlType == DownloadScheme.SSH) {
          cmd = "git fetch " + link.getUrlData()
              + " && git checkout FETCH_HEAD";
        } else {
          String url = ChangeApi.revision(id).view("patch").url();
          cmd = "curl " + url + " | base64 --decode";
        }
        widget.setVisible(true);
        copyLabel.setText(cmd);
      }
    }

    public class RepoCommandLink extends DownloadCommandLink {
      String projectName;
      String ref;
      public RepoCommandLink(String project, String ref) {
        super(DownloadCommand.REPO_DOWNLOAD, "checkout");
        this.projectName = project;
        this.ref = ref;
      }

      @Override
      protected void setCurrentUrl(DownloadUrlLink link) {
        widget.setVisible(false);
        final StringBuilder r = new StringBuilder();
        r.append("repo download ");
        r.append(projectName);
        r.append(" ");
        r.append(ref);
        copyLabel.setText(r.toString());
      }
    }

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
