// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.RPCSuggestOracle;
import com.google.gerrit.common.data.GroupReference;
import com.google.gwt.editor.client.LeafValueEditor;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.HasCloseHandlers;
import com.google.gwt.event.logical.shared.HasSelectionHandlers;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focusable;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestBox.DefaultSuggestionDisplay;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class GroupReferenceBox extends Composite implements
    LeafValueEditor<GroupReference>, HasSelectionHandlers<GroupReference>,
    HasCloseHandlers<GroupReferenceBox>, Focusable {
  private final DefaultSuggestionDisplay suggestions;
  private final NpTextBox textBox;
  private final AccountGroupSuggestOracle oracle;
  private final SuggestBox suggestBox;

  private boolean submitOnSelection;

  public GroupReferenceBox() {
    suggestions = new DefaultSuggestionDisplay();
    textBox = new NpTextBox();
    oracle = new AccountGroupSuggestOracle();
    suggestBox = new SuggestBox( //
        new RPCSuggestOracle(oracle), //
        textBox, //
        suggestions);
    initWidget(suggestBox);

    suggestBox.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        submitOnSelection = false;

        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          if (suggestions.isSuggestionListShowing()) {
            submitOnSelection = true;
          } else {
            SelectionEvent.fire(GroupReferenceBox.this, getValue());
          }
        }
      }
    });
    suggestBox.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
          suggestBox.setText("");
          CloseEvent.fire(GroupReferenceBox.this, GroupReferenceBox.this);
        }
      }
    });
    suggestBox.addSelectionHandler(new SelectionHandler<Suggestion>() {
      @Override
      public void onSelection(SelectionEvent<Suggestion> event) {
        if (submitOnSelection) {
          submitOnSelection = false;
          SelectionEvent.fire(GroupReferenceBox.this, getValue());
        }
      }
    });
  }

  public void setVisibleLength(int len) {
    textBox.setVisibleLength(len);
  }

  @Override
  public HandlerRegistration addSelectionHandler(
      SelectionHandler<GroupReference> handler) {
    return addHandler(handler, SelectionEvent.getType());
  }

  @Override
  public HandlerRegistration addCloseHandler(
      CloseHandler<GroupReferenceBox> handler) {
    return addHandler(handler, CloseEvent.getType());
  }

  @Override
  public GroupReference getValue() {
    String name = suggestBox.getText();
    if (name != null && !name.isEmpty()) {
      return new GroupReference(oracle.getUUID(name), name);
    } else {
      return null;
    }
  }

  @Override
  public void setValue(GroupReference value) {
    suggestBox.setText(value != null ? value.getName() : "");
  }

  @Override
  public int getTabIndex() {
    return suggestBox.getTabIndex();
  }

  @Override
  public void setTabIndex(int index) {
    suggestBox.setTabIndex(index);
  }

  public void setFocus(boolean focused) {
    suggestBox.setFocus(focused);
  }

  @Override
  public void setAccessKey(char key) {
    suggestBox.setAccessKey(key);
  }
}
