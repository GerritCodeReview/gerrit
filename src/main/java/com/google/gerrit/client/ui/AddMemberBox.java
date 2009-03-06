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
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusListenerAdapter;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

public class AddMemberBox extends Composite {
  
  private final FlowPanel addPanel;
  private final Button addMember;
  private final TextBox nameTxtBox;
  private final SuggestBox nameTxt;
  
  public AddMemberBox() {
    addPanel = new FlowPanel();
    addMember = new Button(Util.C.buttonAddGroupMember());
    nameTxtBox = new TextBox();
    nameTxt = new SuggestBox(new AccountSuggestOracle(), nameTxtBox);
    
    nameTxtBox.setVisibleLength(50);
    nameTxtBox.setText(Util.C.defaultAccountName());
    nameTxtBox.addStyleName("gerrit-InputFieldTypeHint");
    nameTxtBox.addFocusListener(new FocusListenerAdapter() {
      @Override
      public void onFocus(Widget sender) {
        if (Util.C.defaultAccountName().equals(nameTxtBox.getText())) {
          nameTxtBox.setText("");
          nameTxtBox.removeStyleName("gerrit-InputFieldTypeHint");
        }
      }

      @Override
      public void onLostFocus(Widget sender) {
        if ("".equals(nameTxtBox.getText())) {
          nameTxtBox.setText(Util.C.defaultAccountName());
          nameTxtBox.addStyleName("gerrit-InputFieldTypeHint");
        }
      }
    });
    
    addPanel.setStyleName("gerrit-ProjectWatchPanel-AddPanel");
    addPanel.add(nameTxt);
    addPanel.add(addMember);
    
    initWidget(addPanel);
  }

  public void setAddButtonText(final String text) {
    addMember.setText(text);
  }

  public void addClickListener(ClickListener listener) {
    addMember.addClickListener(listener);
  }
  
  public String getText() {
    String s = nameTxtBox.getText();
    if (s == null || s.equals(Util.C.defaultAccountName())) {
      s = "";
    }
    return s;
  }
  
  public void setEnabled(boolean enabled) {
    addMember.setEnabled(enabled);
    nameTxtBox.setEnabled(enabled);
  }
  
  public void setText(String text) {
    nameTxtBox.setText(text);
  }

}
