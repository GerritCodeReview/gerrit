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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;

public class ChangeParentDialog extends AutoCenterDialogBox {

  private final NpTextBox newParentNameBox;
  private final SuggestBox newParentTxt;

  public ChangeParentDialog(final String childProject,
      final String parentProject, final AsyncCallback<String> callback) {
    super(/* auto hide */false, /* modal */true);
    setGlassEnabled(true);
    setText(Util.C.changeParentDialogTitle());

    final HTML htmlMessage = new HTML(Util.M.newParentProjectFor(childProject));
    htmlMessage.setWidth("400px");

    newParentNameBox = new NpTextBox();
    newParentNameBox.setVisibleLength(50);
    newParentNameBox.addStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());
    newParentTxt = new SuggestBox(new ProjectNameSuggestOracle(), newParentNameBox);
    newParentTxt.setText(parentProject);
    newParentTxt.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (!newParentTxt.isSuggestionListShowing() && event.getCharCode() == KeyCodes.KEY_ENTER) {
          hide();
          callback.onSuccess(newParentTxt.getText());
        }
      }
    });
    DOM.setStyleAttribute(newParentTxt.getElement(), "marginTop", "10px");
    DOM.setStyleAttribute(newParentTxt.getElement(), "marginBottom", "10px");

    final FlowPanel buttons = new FlowPanel();

    final Button okButton = new Button();
    okButton.setText(Util.C.changeParentDialogReparentButton());
    okButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();
        callback.onSuccess(newParentTxt.getText());
      }
    });
    okButton.setEnabled(false);
    new OnEditEnabler(okButton).listenTo(newParentNameBox);
    buttons.add(okButton);

    final Button cancelButton = new Button();
    DOM.setStyleAttribute(cancelButton.getElement(), "marginLeft", "300px");
    cancelButton.setText(Util.C.changeParentDialogCancelButton());
    cancelButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();
      }
    });
    buttons.add(cancelButton);

    final FlowPanel center = new FlowPanel();
    center.add(htmlMessage);
    center.add(newParentTxt);
    center.add(buttons);
    add(center);

    setWidget(center);
  }

  @Override
  public void center() {
    super.center();
    GlobalKey.dialog(this);
    newParentTxt.setFocus(true);
    newParentNameBox.selectAll();
  }
}
