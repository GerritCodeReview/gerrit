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

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo.ActionInfo;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

class ActionButton extends Button implements ClickHandler {
  private final Change.Id changeId;
  private final String revision;
  private final ActionInfo action;

  ActionButton(Change.Id changeId, ActionInfo action) {
    this(changeId, null, action);
  }

  ActionButton(Change.Id changeId, String revision, ActionInfo action) {
    super(new SafeHtmlBuilder()
      .openDiv()
      .append(action.label())
      .closeDiv());
    setStyleName("");
    setTitle(action.title());
    setEnabled(action.enabled());
    addClickHandler(this);

    this.changeId = changeId;
    this.revision = revision;
    this.action = action;
  }

  @Override
  public void onClick(ClickEvent event) {
    setEnabled(false);

    AsyncCallback<NativeString> cb = new AsyncCallback<NativeString>() {
      @Override
      public void onFailure(Throwable caught) {
        setEnabled(true);
        new ErrorDialog(caught).center();
      }

      @Override
      public void onSuccess(NativeString msg) {
        setEnabled(true);
        if (msg != null && !msg.asString().isEmpty()) {
          // TODO Support better UI on UiAction results.
          Window.alert(msg.asString());
        }
        Gerrit.display(PageLinks.toChange2(changeId));
      }
    };

    RestApi api = revision != null
        ? ChangeApi.revision(changeId.get(), revision)
        : ChangeApi.change(changeId.get());
    api.view(action.id());

    if ("PUT".equalsIgnoreCase(action.method())) {
      api.put(JavaScriptObject.createObject(), cb);
    } else if ("DELETE".equalsIgnoreCase(action.method())) {
      api.delete(cb);
    } else {
      api.post(JavaScriptObject.createObject(), cb);
    }
  }
}
