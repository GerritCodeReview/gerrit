// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.HasCloseHandlers;
import com.google.gwt.event.logical.shared.HasSelectionHandlers;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focusable;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestBox.DefaultSuggestionDisplay;
import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwt.user.client.ui.TextBoxBase;

public class RemoteSuggestBox extends Composite
    implements Focusable,
        HasText,
        HasSelectionHandlers<String>,
        HasCloseHandlers<RemoteSuggestBox> {
  private final RemoteSuggestOracle remoteSuggestOracle;
  private final DefaultSuggestionDisplay display;
  private final HintTextBox textBox;
  private final SuggestBox suggestBox;
  private boolean submitOnSelection;

  public RemoteSuggestBox(SuggestOracle oracle) {
    remoteSuggestOracle = new RemoteSuggestOracle(oracle);
    remoteSuggestOracle.setServeSuggestions(true);
    display = new DefaultSuggestionDisplay();

    textBox = new HintTextBox();
    textBox.addKeyDownHandler(
        new KeyDownHandler() {
          @Override
          public void onKeyDown(KeyDownEvent e) {
            submitOnSelection = false;
            if (e.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
              CloseEvent.fire(RemoteSuggestBox.this, RemoteSuggestBox.this);
            } else if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
              if (display.isSuggestionListShowing()) {
                if (textBox.getValue().equals(remoteSuggestOracle.getLast())) {
                  submitOnSelection = true;
                } else {
                  display.hideSuggestions();
                }
              } else {
                SelectionEvent.fire(RemoteSuggestBox.this, getText());
              }
            }
          }
        });

    suggestBox = new SuggestBox(remoteSuggestOracle, textBox, display);
    suggestBox.addSelectionHandler(
        new SelectionHandler<Suggestion>() {
          @Override
          public void onSelection(SelectionEvent<Suggestion> event) {
            if (submitOnSelection) {
              SelectionEvent.fire(RemoteSuggestBox.this, getText());
            }
            remoteSuggestOracle.cancelOutstandingRequest();
            display.hideSuggestions();
          }
        });
    initWidget(suggestBox);
  }

  public void setHintText(String hint) {
    textBox.setHintText(hint);
  }

  public void setVisibleLength(int len) {
    textBox.setVisibleLength(len);
  }

  public void setEnabled(boolean enabled) {
    suggestBox.setEnabled(enabled);
  }

  public TextBoxBase getTextBox() {
    return textBox;
  }

  @Override
  public String getText() {
    return suggestBox.getText();
  }

  @Override
  public void setText(String value) {
    suggestBox.setText(value);
  }

  @Override
  public void setFocus(boolean focus) {
    suggestBox.setFocus(focus);
  }

  @Override
  public int getTabIndex() {
    return suggestBox.getTabIndex();
  }

  @Override
  public void setAccessKey(char key) {
    suggestBox.setAccessKey(key);
  }

  @Override
  public void setTabIndex(int index) {
    suggestBox.setTabIndex(index);
  }

  @Override
  public HandlerRegistration addSelectionHandler(SelectionHandler<String> h) {
    return addHandler(h, SelectionEvent.getType());
  }

  @Override
  public HandlerRegistration addCloseHandler(CloseHandler<RemoteSuggestBox> h) {
    return addHandler(h, CloseEvent.getType());
  }

  public void selectAll() {
    suggestBox.getValueBox().selectAll();
  }

  public void enableDefaultSuggestions() {
    textBox.addFocusHandler(
        new FocusHandler() {
          @Override
          public void onFocus(FocusEvent focusEvent) {
            if (textBox.getText().equals("")) {
              suggestBox.showSuggestionList();
            }
          }
        });
  }

  public void setServeSuggestionsOnOracle(boolean serveSuggestions) {
    remoteSuggestOracle.setServeSuggestions(serveSuggestions);
  }
}
