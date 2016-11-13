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

package com.google.gerrit.client;

import com.google.gerrit.client.changes.QueryScreen;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;

class SearchPanel extends Composite {
  private final HintTextBox searchBox;
  private final ListBox dropdown;
  private HandlerRegistration regFocus;

  SearchPanel() {
    final FlowPanel body = new FlowPanel();
    initWidget(body);
    setStyleName(Gerrit.RESOURCES.css().searchPanel());

    searchBox = new HintTextBox();
    final MySuggestionDisplay suggestionDisplay = new MySuggestionDisplay();
    searchBox.addKeyPressHandler(
        new KeyPressHandler() {
          @Override
          public void onKeyPress(final KeyPressEvent event) {
            if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
              if (!suggestionDisplay.isSuggestionSelected) {
                doSearch();
              }
            }
          }
        });

    if (Gerrit.hasDocSearch()) {
      dropdown = new ListBox();
      dropdown.setStyleName("searchDropdown");
      dropdown.addItem(Gerrit.C.searchDropdownChanges());
      dropdown.addItem(Gerrit.C.searchDropdownDoc());
      dropdown.setVisibleItemCount(1);
      dropdown.setSelectedIndex(0);
    } else {
      // Doc search is NOT available.
      dropdown = null;
    }

    final SuggestBox suggestBox =
        new SuggestBox(new SearchSuggestOracle(), searchBox, suggestionDisplay);
    searchBox.setStyleName("searchTextBox");
    searchBox.setVisibleLength(70);
    searchBox.setHintText(Gerrit.C.searchHint());

    final Button searchButton = new Button(Gerrit.C.searchButton());
    searchButton.setStyleName("searchButton");
    searchButton.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            doSearch();
          }
        });

    body.add(suggestBox);
    if (dropdown != null) {
      body.add(dropdown);
    }
    body.add(searchButton);
  }

  void setText(final String query) {
    searchBox.setText(query);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    if (regFocus == null) {
      regFocus =
          GlobalKey.addApplication(
              this,
              new KeyCommand(0, '/', Gerrit.C.keySearch()) {
                @Override
                public void onKeyPress(final KeyPressEvent event) {
                  event.preventDefault();
                  searchBox.setFocus(true);
                  searchBox.selectAll();
                }
              });
    }
  }

  @Override
  protected void onUnload() {
    if (regFocus != null) {
      regFocus.removeHandler();
      regFocus = null;
    }
  }

  private void doSearch() {
    final String query = searchBox.getText().trim();
    if ("".equals(query)) {
      return;
    }

    searchBox.setFocus(false);

    if (dropdown != null && dropdown.getSelectedValue().equals(Gerrit.C.searchDropdownDoc())) {
      // doc
      Gerrit.display(PageLinks.toDocumentationQuery(query));
    } else {
      // changes
      if (query.matches("^[1-9][0-9]*$")) {
        Gerrit.display(PageLinks.toChange(Change.Id.parse(query)));
      } else {
        Gerrit.display(PageLinks.toChangeQuery(query), QueryScreen.forQuery(query));
      }
    }
  }

  private static class MySuggestionDisplay extends SuggestBox.DefaultSuggestionDisplay {
    private boolean isSuggestionSelected;

    private MySuggestionDisplay() {
      super();

      getPopupPanel().addStyleName(Gerrit.RESOURCES.css().suggestBoxPopup());
    }

    @Override
    protected Suggestion getCurrentSelection() {
      Suggestion currentSelection = super.getCurrentSelection();
      isSuggestionSelected = currentSelection != null;
      return currentSelection;
    }
  }
}
