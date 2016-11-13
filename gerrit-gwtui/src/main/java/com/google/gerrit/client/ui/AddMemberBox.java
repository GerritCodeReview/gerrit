// Copyright (C) 2008 The Android Open Source Project
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
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SuggestOracle;

public class AddMemberBox extends Composite {
  private final FlowPanel addPanel;
  private final Button addMember;
  private final RemoteSuggestBox suggestBox;

  public AddMemberBox(
      final String buttonLabel, final String hint, final SuggestOracle suggestOracle) {
    addPanel = new FlowPanel();
    addMember = new Button(buttonLabel);

    suggestBox = new RemoteSuggestBox(suggestOracle);
    suggestBox.setStyleName(Gerrit.RESOURCES.css().addMemberTextBox());
    suggestBox.setVisibleLength(50);
    suggestBox.setHintText(hint);
    suggestBox.addSelectionHandler(
        new SelectionHandler<String>() {
          @Override
          public void onSelection(SelectionEvent<String> event) {
            addMember.fireEvent(new ClickEvent() {});
          }
        });

    addPanel.add(suggestBox);
    addPanel.add(addMember);

    initWidget(addPanel);
  }

  public void addClickHandler(ClickHandler handler) {
    addMember.addClickHandler(handler);
  }

  public String getText() {
    return suggestBox.getText();
  }

  public void setEnabled(boolean enabled) {
    addMember.setEnabled(enabled);
    suggestBox.setEnabled(enabled);
  }

  public void setText(String text) {
    suggestBox.setText(text);
  }
}
