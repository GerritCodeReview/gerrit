// Copyright (C) 2009 The Android Open Source Project
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
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;

public abstract class CommentedActionDialog<T> extends AutoCenterDialogBox implements CloseHandler<PopupPanel>{
  protected final FlowPanel panel;
  protected final NpTextArea message;
  protected final Button sendButton;
  protected final Button cancelButton;
  protected final FlowPanel buttonPanel;
  protected AsyncCallback<T> callback;

  protected boolean buttonClicked = false;

  public CommentedActionDialog(final String title, final String heading) {
    super(/* auto hide */false, /* modal */true);
    setGlassEnabled(true);
    setText(title);

    addStyleName(Gerrit.RESOURCES.css().commentedActionDialog());

    message = new NpTextArea();
    message.setCharacterWidth(60);
    message.setVisibleLines(10);
    DOM.setElementPropertyBoolean(message.getElement(), "spellcheck", true);

    sendButton = new Button(Util.C.commentedActionButtonSend());
    sendButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        sendButton.setEnabled(false);
        cancelButton.setEnabled(false);
        onSend();
      }
    });

    cancelButton = new Button(Util.C.commentedActionButtonCancel());
    DOM.setStyleAttribute(cancelButton.getElement(), "marginLeft", "300px");
    cancelButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        buttonClicked = true;
        if (callback != null) {
          callback.onFailure(null);
        }
        hide();
      }
    });

    final FlowPanel mwrap = new FlowPanel();
    mwrap.setStyleName(Gerrit.RESOURCES.css().commentedActionMessage());
    mwrap.add(message);

    buttonPanel = new FlowPanel();
    buttonPanel.add(sendButton);
    buttonPanel.add(cancelButton);

    panel = new FlowPanel();
    panel.add(new SmallHeading(heading));
    panel.add(mwrap);
    panel.add(buttonPanel);
    add(panel);

    callback = createCallback();

    addCloseHandler(this);
  }

  @Override
  public void center() {
    super.center();
    GlobalKey.dialog(this);
    message.setFocus(true);
  }

  @Override
  public void onClose(CloseEvent<PopupPanel> event) {
    if (!buttonClicked) {
      // the dialog was closed without one of the buttons being pressed
      // e.g. the user pressed ESC to close the dialog
      if (callback != null) {
        callback.onFailure(null);
      }
    }
  }

  public abstract void onSend();

  public String getMessageText() {
    return message.getText().trim();
  }

  public abstract AsyncCallback<T> createCallback();
}
