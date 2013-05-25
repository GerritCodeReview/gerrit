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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;
import com.google.gwtjsonrpc.common.AsyncCallback;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.ModeInjector;

public abstract class CodeMirrorActionDialog<T> extends AutoCenterDialogBox
    implements CloseHandler<PopupPanel> {

  private static final int HEADER_FOOTER = 60 + 15 * 2 + 38;
  private static final int SIDE_MARGIN = 60;
  private CodeMirror message;
  protected Button sendButton;
  protected Button cancelButton;
  protected AsyncCallback<T> callback;
  private String heading;

  protected boolean sent = false;

  public CodeMirrorActionDialog(final String title, final String heading,
      AsyncCallback<T> callback) {
    super(/* auto hide */false, /* modal */true);
    this.callback = callback;
    this.heading = heading;
    setGlassEnabled(true);
    setText(title);

    CallbackGroup group = new CallbackGroup();
    CodeMirror.initLibrary(group.add(new GerritCallback<Void>() {
      @Override
      public void onSuccess(Void result) {
        display();
      }
    }));
  }

  private void display() {
    final FlowPanel mwrap = new FlowPanel();
    mwrap.setStyleName(Gerrit.RESOURCES.css().commentedActionMessage());
    message = initCodeMirror(mwrap);
    sendButton = new Button(Util.C.commentedActionButtonSend());
    sendButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        enableButtons(false);
        onSend();
      }
    });

    cancelButton = new Button(Util.C.commentedActionButtonCancel());
    DOM.setStyleAttribute(cancelButton.getElement(), "marginLeft", "300px");
    cancelButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        hide();
      }
    });

    FlowPanel buttonPanel = new FlowPanel();
    buttonPanel.add(sendButton);
    buttonPanel.add(cancelButton);

    FlowPanel panel = new FlowPanel();
    panel.add(new SmallHeading(heading));
    panel.add(mwrap);
    panel.add(buttonPanel);
    add(panel);

    addCloseHandler(this);
  }

  private CodeMirror initCodeMirror(final FlowPanel mwrap) {
    Configuration cfg = Configuration.create()
        .set("readOnly", false)
        .set("lineNumbers", true)
        .set("tabSize", 2);
      final CodeMirror cm = CodeMirror.create(mwrap.getElement(), cfg);
      cm.setWidth(Window.getClientWidth() - SIDE_MARGIN);
      cm.setHeight(Window.getClientHeight() - HEADER_FOOTER);
      return cm;
  }

  protected void setContentType(final String contentType) {
    new ModeInjector()
    .add(getContentType(contentType))
    .inject(new GerritCallback<Void>() {
      @Override
      public void onSuccess(Void result) {
        message.setMode(getContentType(contentType));
      }
    });
  }

  private String getContentType(String contentType) {
    return ModeInjector.getContentType(contentType);
  }

  public void enableButtons(boolean enable) {
    sendButton.setEnabled(enable);
    cancelButton.setEnabled(enable);
  }

  @Override
  public void onClose(CloseEvent<PopupPanel> event) {
    if (!sent) {
      // the dialog was closed without the send button being pressed
      // e.g. the user pressed Cancel or ESC to close the dialog
      if (callback != null) {
        callback.onFailure(null);
      }
    }
    sent = false;
  }

  public abstract void onSend();

  protected String getContent() {
    return message.getValue();
  }

  protected void setContent(String content) {
    message.setValue(content);
  }

  public AsyncCallback<T> createCallback() {
    return new GerritCallback<T>(){
      @Override
      public void onSuccess(T result) {
        sent = true;
        if (callback != null) {
          callback.onSuccess(result);
        }
        hide();
      }

      @Override
      public void onFailure(Throwable caught) {
        enableButtons(true);
        super.onFailure(caught);
      }
    };
  }
}
