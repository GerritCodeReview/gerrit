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
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtjsonrpc.common.JavaSqlTimestampHelper;

import java.util.Date;

class SearchPanel extends Composite {
  private final HintTextBox searchBox;
  private HandlerRegistration regFocus;

  SearchPanel() {
    final FlowPanel body = new FlowPanel();
    initWidget(body);
    setStyleName(Gerrit.RESOURCES.css().searchPanel());

    searchBox = new HintTextBox();
    final MySuggestionDisplay suggestionDisplay = new MySuggestionDisplay();
    searchBox.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          if (!suggestionDisplay.isSuggestionSelected) {
            doSearch();
          }
        }
      }
    });

    final SuggestBox suggestBox =
        new SuggestBox(new SearchSuggestOracle(), searchBox, suggestionDisplay);
    searchBox.setStyleName("gwt-TextBox");
    searchBox.setVisibleLength(70);
    searchBox.setHintText(Gerrit.C.searchHint());

    final Button searchButton = new Button(Gerrit.C.searchButton());
    searchButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doSearch();
      }
    });

    body.add(suggestBox);
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
          GlobalKey.addApplication(this, new KeyCommand(0, '/', Gerrit.C
              .keySearch()) {
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
    String query = searchBox.getText().trim();
    if ("".equals(query)) {
      return;
    }

    searchBox.setFocus(false);

    if (query.matches("^[1-9][0-9]*$")) {
      Gerrit.display(PageLinks.toChange(Change.Id.parse(query)));
    } else {
      query = replaceTimestamps(query);
      searchBox.setText(query);
      Gerrit.display(PageLinks.toChangeQuery(query), QueryScreen.forQuery(query));
    }
  }

  private static String replaceTimestamps(String query) {
    // Add explicit timezone for any before/after queries and update the search
    // box, since REST API inputs/outputs are UTC unless otherwise specified.
    StringBuilder sb = new StringBuilder(query.length() + 30);
    boolean quote = false;
    for (int i = 0; i < query.length(); i++) {
      switch (query.charAt(i)) {
        case 'b':
          i += replaceTimestamp(query, i, quote, sb, "before:");
          break;
        case 'a':
          i += replaceTimestamp(query, i, quote, sb, "after:");
          break;
        case '"':
          quote = !quote;
          // Fallthrough.
        default:
          sb.append(query.charAt(i));
          break;
      }
    }
    return sb.toString();
  }

  private static final DateTimeFormat TZ_FORMAT = DateTimeFormat.getFormat("Z");

  private static int replaceTimestamp(String query, int start, boolean quote,
      StringBuilder out, String pred) {
    int l = pred.length();
    if (quote || !query.substring(start, start + l).equals(pred)) {
      out.append(query.charAt(start));
      return 0;
    }
    int skip = l;

    StringBuilder sb = new StringBuilder(30);
    quote = false;
    boolean needQuote = false;
    LOOP: for (int i = start + l; i < query.length(); i++) {
      char c = query.charAt(i);
      switch (c) {
        case '"':
          if (quote) {
            // Grammar doesn't allow escaped/nested quotes.
            break LOOP;
          }
          quote = true;
          break;
        case ' ':
          if (quote) {
            sb.append(c);
            needQuote = true;
          } else {
            break LOOP;
          }
          break;
        default:
          sb.append(c);
          break;
      }
      skip++;
    }
    String ts = sb.toString();
    if (!JavaSqlTimestampHelper.hasTime(ts)) {
      sb.append(" 00:00:00");
      ts = sb.toString();
      needQuote = true;
    }
    if (!JavaSqlTimestampHelper.hasTimeZone(ts)) {
      sb.append(' ').append(TZ_FORMAT.format(new Date()));
      needQuote = true;
    }
    out.append(pred);
    if (needQuote) {
      out.append('"').append(sb).append('"');
    } else {
      out.append(sb);
    }
    return skip;
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
