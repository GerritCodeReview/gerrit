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

import static com.google.gerrit.reviewdb.AccountGeneralPreferences.DEFAULT_CONTEXT;
import static com.google.gerrit.reviewdb.AccountGeneralPreferences.WHOLE_FILE_CONTEXT;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.NpIntTextBox;
import com.google.gerrit.common.data.PatchScriptSettings;
import com.google.gerrit.common.data.PatchScriptSettings.Whitespace;
import com.google.gerrit.prettify.common.PrettySettings;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGeneralPreferences;
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

public class PatchScriptSettingsPanel extends Composite implements
    HasValueChangeHandlers<PatchScriptSettings> {
  private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

  interface MyUiBinder extends UiBinder<Widget, PatchScriptSettingsPanel> {
  }

  private PatchScriptSettings value;
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
  CheckBox showFullFile;

  @UiField
  CheckBox whitespaceErrors;

  @UiField
  CheckBox showTabs;

  @UiField
  CheckBox reviewed;

  @UiField
  Button update;

  public PatchScriptSettingsPanel() {
    initWidget(uiBinder.createAndBindUi(this));
    initIgnoreWhitespace(ignoreWhitespace);
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

    final PatchScriptSettings s = new PatchScriptSettings();
    if (Gerrit.isSignedIn()) {
      final Account u = Gerrit.getUserAccount();
      final AccountGeneralPreferences pref = u.getGeneralPreferences();
      s.setContext(pref.getDefaultContext());
    } else {
      s.setContext(DEFAULT_CONTEXT);
    }
    setValue(s);
  }

  @Override
  public HandlerRegistration addValueChangeHandler(
      ValueChangeHandler<PatchScriptSettings> handler) {
    return super.addHandler(handler, ValueChangeEvent.getType());
  }

  public void setEnabled(final boolean on) {
    for (Widget w : (HasWidgets) getWidget()) {
      if (w instanceof FocusWidget) {
        ((FocusWidget) w).setEnabled(on);
      }
    }
    toggleEnabledStatus(on);
  }

  public void setEnableSmallFileFeatures(final boolean on) {
    enableSmallFileFeatures = on;
    if (enableSmallFileFeatures) {
      final PrettySettings p = getValue().getPrettySettings();

      syntaxHighlighting.setValue(p.isSyntaxHighlighting());
      showFullFile.setValue(getValue().getContext() == WHOLE_FILE_CONTEXT);
    } else {
      syntaxHighlighting.setValue(false);
      showFullFile.setValue(false);
    }
    toggleEnabledStatus(update.isEnabled());
  }

  public void setEnableIntralineDifference(final boolean on) {
    enableIntralineDifference = on;
    if (enableIntralineDifference) {
      final PrettySettings p = getValue().getPrettySettings();
      intralineDifference.setValue(p.isIntralineDifference());
    } else {
      intralineDifference.setValue(false);
    }
    toggleEnabledStatus(update.isEnabled());
  }

  private void toggleEnabledStatus(final boolean on) {
    intralineDifference.setEnabled(on & enableIntralineDifference);
    syntaxHighlighting.setEnabled(on & enableSmallFileFeatures);
    showFullFile.setEnabled(on & enableSmallFileFeatures);

    final String title =
        enableSmallFileFeatures ? null : PatchUtil.C.disabledOnLargeFiles();
    syntaxHighlighting.setTitle(title);
    showFullFile.setTitle(title);
  }

  public CheckBox getReviewedCheckBox() {
    return reviewed;
  }

  public PatchScriptSettings getValue() {
    return value;
  }

  public void setValue(final PatchScriptSettings s) {
    final PrettySettings p = s.getPrettySettings();

    setIgnoreWhitespace(s.getWhitespace());
    if (enableSmallFileFeatures) {
      showFullFile.setValue(s.getContext() == WHOLE_FILE_CONTEXT);
      syntaxHighlighting.setValue(p.isSyntaxHighlighting());
    } else {
      showFullFile.setValue(false);
      syntaxHighlighting.setValue(false);
    }

    tabWidth.setIntValue(p.getTabSize());
    colWidth.setIntValue(p.getLineLength());
    intralineDifference.setValue(p.isIntralineDifference());
    whitespaceErrors.setValue(p.isShowWhiteSpaceErrors());
    showTabs.setValue(p.isShowTabs());

    value = s;
  }

  @UiHandler("update")
  void onUpdate(ClickEvent event) {
    update();
  }

  private void update() {
    PatchScriptSettings s = new PatchScriptSettings(getValue());
    PrettySettings p = s.getPrettySettings();

    s.setWhitespace(getIgnoreWhitespace());
    if (showFullFile.getValue()) {
      s.setContext(WHOLE_FILE_CONTEXT);
    } else if (Gerrit.isSignedIn()) {
      final Account u = Gerrit.getUserAccount();
      final AccountGeneralPreferences pref = u.getGeneralPreferences();
      if (pref.getDefaultContext() == WHOLE_FILE_CONTEXT) {
        s.setContext(DEFAULT_CONTEXT);
      } else {
        s.setContext(pref.getDefaultContext());
      }
    } else {
      s.setContext(DEFAULT_CONTEXT);
    }

    p.setTabSize(tabWidth.getIntValue());
    p.setLineLength(colWidth.getIntValue());
    p.setSyntaxHighlighting(syntaxHighlighting.getValue());
    p.setIntralineDifference(intralineDifference.getValue());
    p.setShowWhiteSpaceErrors(whitespaceErrors.getValue());
    p.setShowTabs(showTabs.getValue());

    value = s;
    fireEvent(new ValueChangeEvent<PatchScriptSettings>(s) {});
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

  private Whitespace getIgnoreWhitespace() {
    final int sel = ignoreWhitespace.getSelectedIndex();
    if (0 <= sel) {
      return Whitespace.valueOf(ignoreWhitespace.getValue(sel));
    }
    return value.getWhitespace();
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
}
