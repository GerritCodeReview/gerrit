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

package com.google.gerrit.client.actions;

import com.google.gerrit.client.api.ActionContext;
import com.google.gerrit.client.api.ChangeGlue;
import com.google.gerrit.client.api.EditGlue;
import com.google.gerrit.client.api.ProjectGlue;
import com.google.gerrit.client.api.RevisionGlue;
import com.google.gerrit.client.info.ActionInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.EditInfo;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.projects.BranchInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

public class ActionButton extends Button implements ClickHandler {
  private final Project.NameKey project;
  private final BranchInfo branch;
  private final ChangeInfo change;
  private final EditInfo edit;
  private final RevisionInfo revision;
  private final ActionInfo action;
  private ActionContext ctx;

  public ActionButton(Project.NameKey project, ActionInfo action) {
    this(project, null, null, null, null, action);
  }

  public ActionButton(Project.NameKey project, BranchInfo branch, ActionInfo action) {
    this(project, branch, null, null, null, action);
  }

  public ActionButton(ChangeInfo change, ActionInfo action) {
    this(null, null, change, null, null, action);
  }

  public ActionButton(ChangeInfo change, RevisionInfo revision, ActionInfo action) {
    this(null, null, change, null, revision, action);
  }

  private ActionButton(
      Project.NameKey project,
      BranchInfo branch,
      ChangeInfo change,
      EditInfo edit,
      RevisionInfo revision,
      ActionInfo action) {
    super(new SafeHtmlBuilder().openDiv().append(action.label()).closeDiv());
    setStyleName("");
    setTitle(action.title());
    setEnabled(action.enabled());
    addClickHandler(this);

    this.project = project;
    this.branch = branch;
    this.change = change;
    this.edit = edit;
    this.revision = revision;
    this.action = action;
  }

  @Override
  public void onClick(ClickEvent event) {
    if (ctx != null && ctx.has_popup()) {
      ctx.hide();
      ctx = null;
      return;
    }

    if (revision != null) {
      RevisionGlue.onAction(change, revision, action, this);
    } else if (edit != null) {
      EditGlue.onAction(change, edit, action, this);
    } else if (change != null) {
      ChangeGlue.onAction(change, action, this);
    } else if (branch != null) {
      ProjectGlue.onAction(project, branch, action, this);
    } else if (project != null) {
      ProjectGlue.onAction(project, action, this);
    }
  }

  @Override
  public void onUnload() {
    if (ctx != null) {
      if (ctx.has_popup()) {
        ctx.hide();
      }
      ctx = null;
    }
    super.onUnload();
  }

  public void link(ActionContext ctx) {
    this.ctx = ctx;
  }

  public void unlink() {
    ctx = null;
  }
}
