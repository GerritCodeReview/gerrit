// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.ActionInfo;
import com.google.gerrit.client.changes.ChangeInfo.CommitInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;

import java.util.TreeSet;

class Actions extends Composite {
  private static final String[] CORE = {
    "abandon", "restore", "revert", "topic",
    "cherrypick", "submit", "rebase"};

  interface Binder extends UiBinder<FlowPanel, Actions> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  @UiField Button cherrypick;
  @UiField Button rebase;
  @UiField Button revert;
  @UiField Button submit;

  @UiField Button abandon;
  private AbandonAction abandonAction;

  @UiField Button restore;
  private RestoreAction restoreAction;

  private Change.Id changeId;
  private String revision;
  private String project;
  private String subject;
  private String message;

  Actions() {
    initWidget(uiBinder.createAndBindUi(this));
    getElement().setId("change_actions");
  }

  void display(ChangeInfo info, String revision, boolean canSubmit) {
    this.revision = revision;

    boolean hasUser = Gerrit.isSignedIn();
    RevisionInfo revInfo = info.revision(revision);
    CommitInfo commit = revInfo.commit();
    changeId = info.legacy_id();
    project = info.project();
    subject = commit.subject();
    message = commit.message();

    initChangeActions(info, hasUser);
    initRevisionActions(info, revInfo, canSubmit, hasUser);
  }

  private void initChangeActions(ChangeInfo info, boolean hasUser) {
    NativeMap<ActionInfo> actions = info.has_actions()
        ? info.actions()
        : NativeMap.<ActionInfo> create();
    actions.copyKeysIntoChildren("id");

    abandon.setVisible(hasUser && actions.containsKey("abandon"));
    restore.setVisible(hasUser && actions.containsKey("restore"));
    revert.setVisible(hasUser && actions.containsKey("revert"));

    if (hasUser) {
      for (String id : filterNonCore(actions)) {
        add(new ActionButton(info, actions.get(id)));
      }
    }
  }

  private void initRevisionActions(ChangeInfo info, RevisionInfo revInfo,
      boolean canSubmit, boolean hasUser) {
    boolean hasConflict = Gerrit.getConfig().testChangeMerge()
        && !info.mergeable();

    NativeMap<ActionInfo> actions = revInfo.has_actions()
        ? revInfo.actions()
        : NativeMap.<ActionInfo> create();
    actions.copyKeysIntoChildren("id");

    cherrypick.setVisible(hasUser && actions.containsKey("cherrypick"));
    rebase.setVisible(hasUser && actions.containsKey("rebase"));
    submit.setVisible(hasUser && !hasConflict
        && canSubmit
        && actions.containsKey("submit"));

    if (hasUser) {
      for (String id : filterNonCore(actions)) {
        add(new ActionButton(info, revInfo, actions.get(id)));
      }
    }
  }

  private void add(ActionButton b) {
    ((FlowPanel) getWidget()).add(b);
  }

  private static TreeSet<String> filterNonCore(NativeMap<ActionInfo> m) {
    TreeSet<String> ids = new TreeSet<String>(m.keySet());
    for (String id : CORE) {
      ids.remove(id);
    }
    return ids;
  }

  boolean isSubmitEnabled() {
    return submit.isVisible() && submit.isEnabled();
  }

  @UiHandler("abandon")
  void onAbandon(ClickEvent e) {
    if (abandonAction == null) {
      abandonAction = new AbandonAction(abandon, changeId);
    }
    abandonAction.show();
  }

  @UiHandler("restore")
  void onRestore(ClickEvent e) {
    if (restoreAction == null) {
      restoreAction = new RestoreAction(restore, changeId);
    }
    restoreAction.show();
  }

  @UiHandler("rebase")
  void onRebase(ClickEvent e) {
    RebaseAction.call(changeId, revision);
  }

  @UiHandler("submit")
  void onSubmit(ClickEvent e) {
    SubmitAction.call(changeId, revision);
  }

  @UiHandler("cherrypick")
  void onCherryPick(ClickEvent e) {
    CherryPickAction.call(cherrypick, changeId, revision, project, message);
  }

  @UiHandler("revert")
  void onRevert(ClickEvent e) {
    RevertAction.call(cherrypick, changeId, revision, project, subject);
  }
}
