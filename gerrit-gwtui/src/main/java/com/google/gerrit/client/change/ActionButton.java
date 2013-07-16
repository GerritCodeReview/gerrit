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

import com.google.gerrit.client.api.ActionContext;
import com.google.gerrit.client.api.ChangeGlue;
import com.google.gerrit.client.api.RevisionGlue;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.ActionInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

public class ActionButton extends Button implements ClickHandler {
  private final ChangeInfo change;
  private final RevisionInfo revision;
  private final ActionInfo action;
  private ActionContext ctx;

  ActionButton(ChangeInfo change, ActionInfo action) {
    this(change, null, action);
  }

  ActionButton(ChangeInfo change, RevisionInfo revision, ActionInfo action) {
    super(new SafeHtmlBuilder()
      .openDiv()
      .append(action.label())
      .closeDiv());
    setStyleName("");
    setTitle(action.title());
    setEnabled(action.enabled());
    addClickHandler(this);

    this.change = change;
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
    } else {
      ChangeGlue.onAction(change, action, this);
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
