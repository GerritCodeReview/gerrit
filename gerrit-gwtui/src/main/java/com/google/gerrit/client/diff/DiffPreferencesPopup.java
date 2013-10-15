// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.diff;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountApi;
import com.google.gerrit.client.account.DiffPreferencesInput;
import com.google.gerrit.client.account.Util;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.ui.ListenableAccountDiffPreference;
import com.google.gerrit.client.ui.NpIntTextBox;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.OptionElement;
import com.google.gwt.dom.client.SelectElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.user.client.PluginSafePopupPanel;

public class DiffPreferencesPopup extends PluginSafePopupPanel implements
KeyPressHandler, KeyDownHandler {
  private final FocusPanel focus;

  private final ListenableAccountDiffPreference listenablePrefs;
  private boolean enableIntralineDifference = true;
  private boolean enableSmallFileFeatures = true;

  Label lbIgnoreWhitespace;
  ListBox ignoreWhitespace;
  Label lbTabWidth;
  NpIntTextBox tabWidth;
  Label lbIntralineDifference;
  CheckBox intralineDifference;
  Label lbContext;
  ListBox context;
  Label lbWhitespaceErrors;
  CheckBox whitespaceErrors;
  Label lbShowTabs;
  CheckBox showTabs;
  Label lbExpandAllComments;
  CheckBox expandAllComments;
  Button update;
  Button save;

  /**
   * Counts +1 for every setEnabled(true) and -1 for every setEnabled(false)
   *
   * The purpose is to prevent enabling widgets too early. It might happen that
   * setEnabled(false) is called from this class and from an event handler
   * of ValueChangeEvent in another class. The first setEnabled(true) would then
   * enable widgets too early i.e. before the second setEnabled(true) is called.
   *
   * With this counter the setEnabled(true) will enable widgets only when
   * setEnabledCounter == 0. Until it is less than zero setEnabled(true) will
   * not enable the widgets.
   */
  private int setEnabledCounter;

  public DiffPreferencesPopup(ListenableAccountDiffPreference prefs) {
    super(true/* autohide */, true/* modal */);
    listenablePrefs = prefs;
    Resources.I.prefs().ensureInjected();
    setStyleName(Resources.I.prefs().prefsPopup());

    final Anchor closer = new Anchor(DiffConstants.I.close());
    closer.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        hide();
      }
    });

    final Grid header = new Grid(1, 3);
    header.setStyleName(Resources.I.prefs().prefsHeader());
    header.setText(0, 0, DiffConstants.I.diffPreferences());
    header.setWidget(0, 2, closer);

    final CellFormatter fmt = header.getCellFormatter();
    fmt.addStyleName(0, 1, Resources.I.prefs().prefsHeaderGlue());
    fmt.setHorizontalAlignment(0, 2, HasHorizontalAlignment.ALIGN_RIGHT);

    final FlowPanel body = new FlowPanel();
    body.add(header);
    DOM.appendChild(body.getElement(), DOM.createElement("hr"));
    final Grid content = new Grid(9, 2);
    content.setWidget(0, 0, lbIgnoreWhitespace = new Label(DiffConstants.I.ignoreWhitespace()));
    content.setWidget(0, 1, ignoreWhitespace = new ListBox());
    content.setWidget(1, 0, lbTabWidth = new Label(DiffConstants.I.tabWidth()));
    content.setWidget(1, 1, tabWidth = new NpIntTextBox());
    content.setWidget(2, 0, lbIntralineDifference = new Label(DiffConstants.I.intralineDifference()));
    content.setWidget(2, 1, intralineDifference = new CheckBox());
    content.setWidget(3, 0, lbContext = new Label(DiffConstants.I.context()));
    content.setWidget(3, 1, context = new ListBox());
    content.setWidget(4, 0, lbWhitespaceErrors = new Label(DiffConstants.I.whitespaceErrors()));
    content.setWidget(4, 1, whitespaceErrors = new CheckBox());
    content.setWidget(5, 0, lbShowTabs = new Label(DiffConstants.I.showTabs()));
    content.setWidget(5, 1, showTabs = new CheckBox());
    content.setWidget(6, 0, lbExpandAllComments = new Label(DiffConstants.I.expandAllComments()));
    content.setWidget(6, 1, expandAllComments = new CheckBox());
    content.setWidget(7, 0, update = new Button("Update"));
    content.setWidget(7, 1, save = new Button("Save"));
    body.add(content);
    update.addClickHandler(new ClickHandler() {
      public void onClick(ClickEvent e) {
          update();
      }
    });
    save.addClickHandler(new ClickHandler() {
      public void onClick(ClickEvent e) {
        save();
      }
    });


    focus = new FocusPanel(body);
    focus.getElement().getStyle().setProperty("outline", "0px");
    focus.getElement().setAttribute("hideFocus", "true");
    focus.addKeyPressHandler(this);
    focus.addKeyDownHandler(this);
    add(focus);

    initIgnoreWhitespace(ignoreWhitespace);
    initContext(context);
    if (!Gerrit.isSignedIn()) {
      save.setVisible(false);
    }

    tabWidth.addKeyPressHandler(this);
    setVisible(false);

    display();
  }

  public void setEnabled(final boolean on) {
    if (on) {
      setEnabledCounter++;
    } else {
      setEnabledCounter--;
    }
    if (on && setEnabledCounter == 0 || !on) {
      for (Widget w : (HasWidgets) getWidget()) {
        if (w instanceof FocusWidget) {
          ((FocusWidget) w).setEnabled(on);
        }
      }
      toggleEnabledStatus(on);
    };
  }

  public void setEnableSmallFileFeatures(final boolean on) {
    enableSmallFileFeatures = on;

    NodeList<OptionElement> options =
        context.getElement().<SelectElement>cast().getOptions();
    // WHOLE_FILE_CONTEXT is the last option in the list.
    int lastIndex = options.getLength() - 1;
    OptionElement currOption = options.getItem(lastIndex);
    if (enableSmallFileFeatures) {
      currOption.setDisabled(false);
    } else {
      currOption.setDisabled(true);
      if (context.getSelectedIndex() == lastIndex) {
        // Select the next longest context from WHOLE_FILE_CONTEXT
        context.setSelectedIndex(lastIndex - 1);
      }
    }
    toggleEnabledStatus(save.isEnabled());
  }

  public void setEnableIntralineDifference(final boolean on) {
    enableIntralineDifference = on;
    if (enableIntralineDifference) {
      intralineDifference.setValue(getValue().isIntralineDifference());
    } else {
      intralineDifference.setValue(false);
    }
    toggleEnabledStatus(save.isEnabled());
  }

  private void toggleEnabledStatus(final boolean on) {
    intralineDifference.setEnabled(on && enableIntralineDifference);
  }

  public AccountDiffPreference getValue() {
    return listenablePrefs.get();
  }

  public void setValue(final AccountDiffPreference dp) {
    listenablePrefs.set(dp);
    display();
  }

  protected void display() {
    final AccountDiffPreference dp = getValue();
    setIgnoreWhitespace(dp.getIgnoreWhitespace());
    setContext(dp.getContext());

    tabWidth.setIntValue(dp.getTabSize());
    intralineDifference.setValue(dp.isIntralineDifference());
    whitespaceErrors.setValue(dp.isShowWhitespaceErrors());
    showTabs.setValue(dp.isShowTabs());
    expandAllComments.setValue(dp.isExpandAllComments());
  }



  void onSave(ClickEvent event) {
    save();
  }

  private void update() {
    AccountDiffPreference dp = new AccountDiffPreference(getValue());
    dp.setIgnoreWhitespace(getIgnoreWhitespace());
    dp.setContext(getContext());
    dp.setTabSize(tabWidth.getIntValue());
    dp.setIntralineDifference(intralineDifference.getValue());
    dp.setShowWhitespaceErrors(whitespaceErrors.getValue());
    dp.setShowTabs(showTabs.getValue());
    dp.setExpandAllComments(expandAllComments.getValue());

    listenablePrefs.set(dp);
  }

  private void save() {
    update();
    if (Gerrit.isSignedIn()) {
      persistDiffPreferences();
    }
  }

  private void persistDiffPreferences() {
    setEnabled(false);
    final AccountDiffPreference p = listenablePrefs.get();
    DiffPreferencesInput in = DiffPreferencesInput.create(p);
    AccountApi.self().view("preferences.diff")
        .put(in, new AsyncCallback<JavaScriptObject>() {
          @Override
          public void onSuccess(JavaScriptObject result) {
            Gerrit.setAccountDiffPreference(p);
            setEnabled(true);
          }

          @Override
          public void onFailure(Throwable caught) {
            setEnabled(true);
          }
        });
  }

  private void initIgnoreWhitespace(ListBox ws) {
    ws.addItem(PatchUtil.C.whitespaceIGNORE_NONE(), //
        Whitespace.IGNORE_NONE.name());
    ws.addItem(PatchUtil.C.whitespaceIGNORE_SPACE_AT_EOL(), //
        Whitespace.IGNORE_SPACE_AT_EOL.name());
    ws.addItem(PatchUtil.C.whitespaceIGNORE_SPACE_CHANGE(), //
        Whitespace.IGNORE_SPACE_CHANGE.name());
    ws.addItem(PatchUtil.C.whitespaceIGNORE_ALL_SPACE(), //
        Whitespace.IGNORE_ALL_SPACE.name());
  }

  private void initContext(ListBox context) {
    for (final short v : AccountDiffPreference.CONTEXT_CHOICES) {
      final String label;
      if (v == AccountDiffPreference.WHOLE_FILE_CONTEXT) {
        label = Util.C.contextWholeFile();
      } else {
        label = Util.M.lines(v);
      }
      context.addItem(label, String.valueOf(v));
    }
  }

  private Whitespace getIgnoreWhitespace() {
    final int sel = ignoreWhitespace.getSelectedIndex();
    if (0 <= sel) {
      return Whitespace.valueOf(ignoreWhitespace.getValue(sel));
    }
    return getValue().getIgnoreWhitespace();
  }

  private void setIgnoreWhitespace(Whitespace s) {
    for (int i = 0; i < ignoreWhitespace.getItemCount(); i++) {
      if (ignoreWhitespace.getValue(i).equals(s.name())) {
        ignoreWhitespace.setSelectedIndex(i);
        return;
      }
    }
    ignoreWhitespace.setSelectedIndex(0);
  }

  private short getContext() {
    final int sel = context.getSelectedIndex();
    if (0 <= sel) {
      return Short.parseShort(context.getValue(sel));
    }
    return getValue().getContext();
  }

  private void setContext(int ctx) {
    String v = String.valueOf(ctx);
    for (int i = 0; i < context.getItemCount(); i++) {
      if (context.getValue(i).equals(v)) {
        context.setSelectedIndex(i);
        return;
      }
    }
    context.setSelectedIndex(0);
  }

  @Override
  public void setVisible(final boolean show) {
    super.setVisible(show);
    if (show) {
      focus.setFocus(true);
    }
  }

  @Override
  public void onKeyDown(KeyDownEvent event) {
    if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
      hide();
    }
  }

  @Override
  public void onKeyPress(KeyPressEvent event) {
    if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
      save();
    } else if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ESCAPE) {
      hide();
    } else if (event.getNativeEvent().getKeyCode() != 115) {
      // Block the key from triggering other events.
      event.stopPropagation();
    }
  }
}
