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
import com.google.gwt.user.client.ui.HTMLPanel;

import java.util.TreeSet;

class Actions extends Composite {
  private static final String[] CORE = {"cherrypick", "submit", "rebase"};

  interface Binder extends UiBinder<HTMLPanel, Actions> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  @UiField Button cherryPick;
  @UiField Button abandon;
  @UiField Button rebase;
  @UiField Button restore;
  @UiField Button revert;
  @UiField Button submit;

  private Change.Id changeId;
  private String revision;
  private String project;
  private String subject;
  private String message;

  Actions() {
    HTMLPanel panel = uiBinder.createAndBindUi(this);
    panel.getElement().setId("change_actions");
    initWidget(panel);
  }

  void display(ChangeInfo info, String revision, boolean canSubmit) {
    this.revision = revision;
    RevisionInfo revInfo = info.revision(revision);
    CommitInfo commit = revInfo.commit();
    changeId = info.legacy_id();
    project = info.project();
    subject = commit.subject();
    message = commit.message();

    NativeMap<ActionInfo> actions = revInfo.has_actions()
        ? revInfo.actions()
        : NativeMap.<ActionInfo> create();

    boolean hasUser = Gerrit.isSignedIn();
    boolean hasConflict = Gerrit.getConfig().testChangeMerge() && !info.mergeable();
    submit.setVisible(hasUser && !hasConflict && canSubmit && actions.containsKey("submit"));
    cherryPick.setVisible(hasUser && actions.containsKey("cherrypick"));
    rebase.setVisible(hasUser && info.status().isOpen() && actions.containsKey("rebase"));

    abandon.setVisible(hasUser && info.status().isOpen()); // can abandon?
    restore.setVisible(hasUser && info.status() == Change.Status.ABANDONED); // can restore?
    revert.setVisible(hasUser && info.status() == Change.Status.MERGED); // can revert?

    renderNonCoreActions(actions);
  }

  private void renderNonCoreActions(NativeMap<ActionInfo> actions) {
    actions.copyKeysIntoChildren("id");

    HTMLPanel panel = (HTMLPanel) getWidget();
    TreeSet<String> idSet = new TreeSet<String>(actions.keySet());
    for (String id : CORE) {
      idSet.remove(id);
    }
    for (String id : idSet) {
      panel.add(new ActionButton(changeId, revision, actions.get(id)));
    }
  }

  boolean isSubmitEnabled() {
    return submit.isVisible() && submit.isEnabled();
  }

  @UiHandler("abandon")
  void onAbandon(ClickEvent e) {
    AbandonAction.call(abandon, changeId);
  }

  @UiHandler("restore")
  void onRestore(ClickEvent e) {
    RestoreAction.call(restore, changeId);
  }

  @UiHandler("rebase")
  void onRebase(ClickEvent e) {
    RebaseAction.call(changeId, revision);
  }

  @UiHandler("submit")
  void onSubmit(ClickEvent e) {
    SubmitAction.call(changeId, revision);
  }

  @UiHandler("cherryPick")
  void onCherryPick(ClickEvent e) {
    CherryPickAction.call(cherryPick, changeId, revision, project, message);
  }

  @UiHandler("revert")
  void onRevert(ClickEvent e) {
    RevertAction.call(cherryPick, changeId, revision, project, subject);
  }
}
