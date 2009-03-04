// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;
import com.google.gwtjsonrpc.client.VoidResult;

/**
 * TODO: javadoc
 */
public class AbandonChangeDialog extends AutoCenterDialogBox {

  private final VerticalPanel panel;
  private final TextArea message;
  private final Button sendButton;
  private final Button cancelButton;
  private final PatchSet.Id psid;
  private final AsyncCallback<?> appCallback;

  public AbandonChangeDialog(final PatchSet.Id psi,
      final AsyncCallback<?> callback) {
    super(false, true);
    psid = psi;
    appCallback = callback;
    addStyleName("gerrit-AbandonChangeDialog");

    panel = new VerticalPanel();
    add(panel);
    
    panel.add(new SmallHeading(Util.C.headingAbandonMessage()));

    final VerticalPanel mwrap = new VerticalPanel();
    mwrap.setStyleName("gerrit-AbandonMessage");
    panel.add(mwrap);

    message = new TextArea();
    message.setCharacterWidth(60);
    message.setVisibleLines(10);
    DOM.setElementPropertyBoolean(message.getElement(), "spellcheck", true);
    mwrap.add(message);

    final FlowPanel buttonPanel = new FlowPanel();
    buttonPanel.setStyleName("gerrit-CommentEditor-Buttons");
    panel.add(buttonPanel);

    sendButton = new Button(Util.C.buttonAbandonChangeSend());
    sendButton.addClickListener(new ClickListener() {
      public void onClick(Widget sender) {
        sendButton.setEnabled(false);
        PatchUtil.DETAIL_SVC.abandonChange(message.getText().trim(), psid,
            new GerritCallback<VoidResult>() {
              public void onSuccess(VoidResult result) {
                if (appCallback != null) {
                  appCallback.onSuccess(null);
                }
                hide();
              }

              @Override
              public void onFailure(Throwable caught) {
                sendButton.setEnabled(true);
                super.onFailure(caught);
              }
            });
      }
    });
    buttonPanel.add(sendButton);

    cancelButton = new Button(Util.C.buttonAbandonChangeCancel());
    cancelButton.addClickListener(new ClickListener() {
      public void onClick(Widget sender) {
        if (appCallback != null) {
          appCallback.onFailure(null);
        }
        hide();
      }
    });
    buttonPanel.add(cancelButton);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    message.setFocus(true);
  }
}
