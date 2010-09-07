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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.Util;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.ListenableAccountDiffPreference;
import com.google.gerrit.client.ui.NpIntTextBox;
import com.google.gerrit.reviewdb.AccountDiffPreference;
import com.google.gerrit.reviewdb.AccountDiffPreference.Whitespace;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtjsonrpc.client.VoidResult;

public class PatchScriptSettingsPanel extends Composite implements
    HasValueChangeHandlers<AccountDiffPreference> {
  private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

  interface MyUiBinder extends UiBinder<Widget, PatchScriptSettingsPanel> {
  }

  private ListenableAccountDiffPreference listenablePrefs;
  private boolean enableIntralineDifference = true;
  private boolean enableSmallFileFeatures = true;

  @UiField
  ListBox ignoreWhitespace;

  @UiField
  NpIntTextBox tabWidth;

  @UiField
  NpIntTextBox colWidth;

  @UiField
  CheckBox syntaxHighlighting;

  @UiField
  CheckBox intralineDifference;

  @UiField
  ListBox context;

  @UiField
  CheckBox whitespaceErrors;

  @UiField
  CheckBox showTabs;

  @UiField
  CheckBox reviewed;

  @UiField
  Button update;

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

  public PatchScriptSettingsPanel(ListenableAccountDiffPreference prefs) {
    listenablePrefs = prefs;
    initWidget(uiBinder.createAndBindUi(this));
    initIgnoreWhitespace(ignoreWhitespace);
    initContext(context);
    if (!Gerrit.isSignedIn()) {
      reviewed.setVisible(false);
    }

    KeyPressHandler onEnter = new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          update();
        }
      }
    };
    tabWidth.addKeyPressHandler(onEnter);
    colWidth.addKeyPressHandler(onEnter);

    display();
  }

  @Override
  public HandlerRegistration addValueChangeHandler(
      ValueChangeHandler<AccountDiffPreference> handler) {
    return super.addHandler(handler, ValueChangeEvent.getType());
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
    if (enableSmallFileFeatures) {
      syntaxHighlighting.setValue(getValue().isSyntaxHighlighting());
    } else {
      syntaxHighlighting.setValue(false);
    }
    toggleEnabledStatus(update.isEnabled());
  }

  public void setEnableIntralineDifference(final boolean on) {
    enableIntralineDifference = on;
    if (enableIntralineDifference) {
      intralineDifference.setValue(getValue().isIntralineDifference());
    } else {
      intralineDifference.setValue(false);
    }
    toggleEnabledStatus(update.isEnabled());
  }

  private void toggleEnabledStatus(final boolean on) {
    intralineDifference.setEnabled(on & enableIntralineDifference);
    syntaxHighlighting.setEnabled(on & enableSmallFileFeatures);

    final String title =
        enableSmallFileFeatures ? null : PatchUtil.C.disabledOnLargeFiles();
    syntaxHighlighting.setTitle(title);
  }

  public CheckBox getReviewedCheckBox() {
    return reviewed;
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
    if (enableSmallFileFeatures) {
      syntaxHighlighting.setValue(dp.isSyntaxHighlighting());
    } else {
      syntaxHighlighting.setValue(false);
    }
    setContext(dp.getContext());

    tabWidth.setIntValue(dp.getTabSize());
    colWidth.setIntValue(dp.getLineLength());
    intralineDifference.setValue(dp.isIntralineDifference());
    whitespaceErrors.setValue(dp.isShowWhitespaceErrors());
    showTabs.setValue(dp.isShowTabs());
  }

  @UiHandler("update")
  void onUpdate(ClickEvent event) {
    update();
  }

  private void update() {
    AccountDiffPreference dp = new AccountDiffPreference(getValue());
    dp.setIgnoreWhitespace(getIgnoreWhitespace());
    dp.setContext(getContext());
    dp.setTabSize(tabWidth.getIntValue());
    dp.setLineLength(colWidth.getIntValue());
    dp.setSyntaxHighlighting(syntaxHighlighting.getValue());
    dp.setIntralineDifference(intralineDifference.getValue());
    dp.setShowWhitespaceErrors(whitespaceErrors.getValue());
    dp.setShowTabs(showTabs.getValue());

    listenablePrefs.set(dp);

    if (Gerrit.isSignedIn()) {
      persistDiffPreferences();
    }
  }

  private void persistDiffPreferences() {
    setEnabled(false);
    Util.ACCOUNT_SVC.changeDiffPreferences(getValue(),
        new GerritCallback<VoidResult>() {
      @Override
      public void onSuccess(VoidResult result) {
        Gerrit.setAccountDiffPreference(getValue());
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
    return (short) getValue().getContext();
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
}
