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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.reviewdb.PatchSet;
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

public class RestoreChangeDialog extends AutoCenterDialogBox implements CloseHandler<PopupPanel>{
  private final FlowPanel panel;
  private final NpTextArea message;
  private final Button sendButton;
  private final Button cancelButton;
  private final PatchSet.Id psid;
  private final AsyncCallback<ChangeDetail> callback;

  private boolean buttonClicked = false;

  public RestoreChangeDialog(final PatchSet.Id psi,
      final AsyncCallback<ChangeDetail> callback) {
    super(/* auto hide */false, /* modal */true);
    setGlassEnabled(true);

    psid = psi;
    this.callback = callback;
    addStyleName(Gerrit.RESOURCES.css().abandonChangeDialog());
    setText(Util.C.restoreChangeTitle());

    panel = new FlowPanel();
    add(panel);

    panel.add(new SmallHeading(Util.C.headingRestoreMessage()));

    final FlowPanel mwrap = new FlowPanel();
    mwrap.setStyleName(Gerrit.RESOURCES.css().abandonMessage());
    panel.add(mwrap);

    message = new NpTextArea();
    message.setCharacterWidth(60);
    message.setVisibleLines(10);
    DOM.setElementPropertyBoolean(message.getElement(), "spellcheck", true);
    mwrap.add(message);

    final FlowPanel buttonPanel = new FlowPanel();
    panel.add(buttonPanel);

    sendButton = new Button(Util.C.buttonRestoreChangeSend());
    sendButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        sendButton.setEnabled(false);
        cancelButton.setEnabled(false);
        Util.MANAGE_SVC.restoreChange(psid, message.getText().trim(),
            new GerritCallback<ChangeDetail>() {
              @Override
              public void onSuccess(ChangeDetail result) {
                buttonClicked = true;
                if (callback != null) {
                  callback.onSuccess(result);
                }
                hide();
              }

              @Override
              public void onFailure(Throwable caught) {
                sendButton.setEnabled(true);
                cancelButton.setEnabled(true);
                super.onFailure(caught);
              }
            });
      }
    });
    buttonPanel.add(sendButton);

    cancelButton = new Button(Util.C.buttonRestoreChangeCancel());
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
    buttonPanel.add(cancelButton);

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
}
