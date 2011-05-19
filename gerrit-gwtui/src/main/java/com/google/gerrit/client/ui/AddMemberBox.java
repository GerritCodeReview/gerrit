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

import com.google.gerrit.client.admin.Util;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestBox.DefaultSuggestionDisplay;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;

public class AddMemberBox extends Composite {
  private final FlowPanel addPanel;
  private final Button addMember;
  private final HintTextBox nameTxtBox;
  private final SuggestBox nameTxt;
  private boolean submitOnSelection;

  public AddMemberBox() {
    addPanel = new FlowPanel();
    addMember = new Button(Util.C.buttonAddGroupMember());
    nameTxtBox = new HintTextBox();
    nameTxt = new SuggestBox(new RPCSuggestOracle(
        new AccountSuggestOracle()), nameTxtBox);

    nameTxtBox.setVisibleLength(50);
    nameTxtBox.setHintText(Util.C.defaultAccountName());
    nameTxtBox.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        submitOnSelection = false;

        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          if (((DefaultSuggestionDisplay) nameTxt.getSuggestionDisplay())
              .isSuggestionListShowing()) {
            submitOnSelection = true;
          } else {
            doAdd();
          }
        }
      }
    });
    nameTxt.addSelectionHandler(new SelectionHandler<Suggestion>() {
      @Override
      public void onSelection(SelectionEvent<Suggestion> event) {
        if (submitOnSelection) {
          submitOnSelection = false;
          doAdd();
        }
      }
    });

    addPanel.add(nameTxt);
    addPanel.add(addMember);

    initWidget(addPanel);
  }

  public void setAddButtonText(final String text) {
    addMember.setText(text);
  }

  public void addClickHandler(final ClickHandler handler) {
    addMember.addClickHandler(handler);
  }

  public String getText() {
    String s = nameTxtBox.getText();
    return s == null ? "" : s;
  }

  public void setEnabled(boolean enabled) {
    addMember.setEnabled(enabled);
    nameTxtBox.setEnabled(enabled);
  }

  public void setText(String text) {
    nameTxtBox.setText(text);
  }

  private void doAdd() {
    addMember.fireEvent(new ClickEvent() {});
  }
}
