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

package com.google.gerrit.client.editor;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.account.AccountApi;
import com.google.gerrit.client.account.EditPreferences;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.NpIntTextBox;
import com.google.gerrit.extensions.client.KeyMapType;
import com.google.gerrit.extensions.client.Theme;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.ToggleButton;

import net.codemirror.theme.ThemeLoader;

/** Displays current edit preferences. */
class EditPreferencesBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, EditPreferencesBox> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String dialog();
  }

  private final EditScreen view;
  private EditPreferences prefs;

  @UiField Style style;
  @UiField Anchor close;
  @UiField NpIntTextBox tabWidth;
  @UiField NpIntTextBox lineLength;
  @UiField NpIntTextBox cursorBlinkRate;
  @UiField ToggleButton topMenu;
  @UiField ToggleButton syntaxHighlighting;
  @UiField ToggleButton showTabs;
  @UiField ToggleButton whitespaceErrors;
  @UiField ToggleButton lineNumbers;
  @UiField ListBox theme;
  @UiField ListBox keyMap;
  @UiField Button apply;
  @UiField Button save;

  EditPreferencesBox(EditScreen view) {
    this.view = view;
    initWidget(uiBinder.createAndBindUi(this));
    initTheme();
    initKeyMapType();
  }

  void set(EditPreferences prefs) {
    this.prefs = prefs;

    tabWidth.setIntValue(prefs.tabSize());
    lineLength.setIntValue(prefs.lineLength());
    cursorBlinkRate.setIntValue(prefs.cursorBlinkRate());
    topMenu.setValue(!prefs.hideTopMenu());
    syntaxHighlighting.setValue(prefs.syntaxHighlighting());
    showTabs.setValue(prefs.showTabs());
    whitespaceErrors.setValue(prefs.showWhitespaceErrors());
    lineNumbers.setValue(prefs.hideLineNumbers());
    setTheme(prefs.theme());
    setKeyMapType(prefs.keyMapType());
  }

  @UiHandler("tabWidth")
  void onTabWidth(ValueChangeEvent<String> e) {
    String v = e.getValue();
    if (v != null && v.length() > 0) {
      prefs.tabSize(Math.max(1, Integer.parseInt(v)));
      view.getEditor().setOption("tabSize", v);
    }
  }

  @UiHandler("lineLength")
  void onLineLength(ValueChangeEvent<String> e) {
    String v = e.getValue();
    if (v != null && v.length() > 0) {
      prefs.lineLength(Math.max(1, Integer.parseInt(v)));
      view.setLineLength(prefs.lineLength());
    }
  }

  @UiHandler("cursorBlinkRate")
  void onCursoBlinkRate(ValueChangeEvent<String> e) {
    String v = e.getValue();
    if (v != null && v.length() > 0) {
      // A negative value hides the cursor entirely:
      // don't let user shot himself in the foot.
      prefs.cursorBlinkRate(Math.max(0, Integer.parseInt(v)));
      view.getEditor().setOption("cursorBlinkRate", prefs.cursorBlinkRate());
    }
  }

  @UiHandler("topMenu")
  void onTopMenu(ValueChangeEvent<Boolean> e) {
    prefs.hideTopMenu(!e.getValue());
    Gerrit.setHeaderVisible(!prefs.hideTopMenu());
    view.resizeCodeMirror();
  }

  @UiHandler("showTabs")
  void onShowTabs(ValueChangeEvent<Boolean> e) {
    prefs.showTabs(e.getValue());
    view.setShowTabs(prefs.showTabs());
  }

  @UiHandler("whitespaceErrors")
  void onshowTrailingSpace(ValueChangeEvent<Boolean> e) {
    prefs.showWhitespaceErrors(e.getValue());
    view.setShowWhitespaceErrors(prefs.showWhitespaceErrors());
  }

  @UiHandler("lineNumbers")
  void onLineNumbers(ValueChangeEvent<Boolean> e) {
    prefs.hideLineNumbers(e.getValue());
    view.setShowLineNumbers(prefs.hideLineNumbers());
  }

  @UiHandler("syntaxHighlighting")
  void onSyntaxHighlighting(ValueChangeEvent<Boolean> e) {
    prefs.syntaxHighlighting(e.getValue());
    view.setSyntaxHighlighting(prefs.syntaxHighlighting());
  }

  @UiHandler("theme")
  void onTheme(@SuppressWarnings("unused") ChangeEvent e) {
    final Theme newTheme = Theme.valueOf(theme.getValue(theme.getSelectedIndex()));
    prefs.theme(newTheme);
    ThemeLoader.loadTheme(newTheme, new GerritCallback<Void>() {
      @Override
      public void onSuccess(Void result) {
        view.getEditor().operation(new Runnable() {
          @Override
          public void run() {
            String t = newTheme.name().toLowerCase();
            view.getEditor().setOption("theme", t);
          }
        });
      }
    });
  }

  @UiHandler("keyMap")
  void onKeyMap(@SuppressWarnings("unused") ChangeEvent e) {
    KeyMapType keyMapType = KeyMapType.valueOf(
        keyMap.getValue(keyMap.getSelectedIndex()));
    prefs.keyMapType(keyMapType);
    view.getEditor().setOption("keyMap", keyMapType.name().toLowerCase());
  }

  @UiHandler("apply")
  void onApply(@SuppressWarnings("unused") ClickEvent e) {
    close();
  }

  @UiHandler("save")
  void onSave(@SuppressWarnings("unused") ClickEvent e) {
    AccountApi.putEditPreferences(prefs, new GerritCallback<VoidResult>() {
      @Override
      public void onSuccess(VoidResult n) {
        prefs.copyTo(Gerrit.getEditPreferences());
      }
    });
    close();
  }

  @UiHandler("close")
  void onClose(ClickEvent e) {
    e.preventDefault();
    close();
  }

  private void close() {
    ((PopupPanel) getParent()).hide();
  }

  private void setTheme(Theme v) {
    String name = v != null ? v.name() : Theme.DEFAULT.name();
    for (int i = 0; i < theme.getItemCount(); i++) {
      if (theme.getValue(i).equals(name)) {
        theme.setSelectedIndex(i);
        return;
      }
    }
    theme.setSelectedIndex(0);
  }

  private void initTheme() {
    theme.addItem(
        Theme.DEFAULT.name().toLowerCase(),
        Theme.DEFAULT.name());
    theme.addItem(
        Theme.ECLIPSE.name().toLowerCase(),
        Theme.ECLIPSE.name());
    theme.addItem(
        Theme.ELEGANT.name().toLowerCase(),
        Theme.ELEGANT.name());
    theme.addItem(
        Theme.NEAT.name().toLowerCase(),
        Theme.NEAT.name());
    theme.addItem(
        Theme.MIDNIGHT.name().toLowerCase(),
        Theme.MIDNIGHT.name());
    theme.addItem(
        Theme.NIGHT.name().toLowerCase(),
        Theme.NIGHT.name());
    theme.addItem(
        Theme.TWILIGHT.name().toLowerCase(),
        Theme.TWILIGHT.name());
  }

  private void setKeyMapType(KeyMapType v) {
    String name = v != null ? v.name() : KeyMapType.DEFAULT.name();
    for (int i = 0; i < keyMap.getItemCount(); i++) {
      if (keyMap.getValue(i).equals(name)) {
        keyMap.setSelectedIndex(i);
        return;
      }
    }
    keyMap.setSelectedIndex(0);
  }

  private void initKeyMapType() {
    keyMap.addItem(
        KeyMapType.DEFAULT.name().toLowerCase(),
        KeyMapType.DEFAULT.name());
    keyMap.addItem(
        KeyMapType.EMACS.name().toLowerCase(),
        KeyMapType.EMACS.name());
    keyMap.addItem(
        KeyMapType.VIM.name().toLowerCase(),
        KeyMapType.VIM.name());
  }
}
