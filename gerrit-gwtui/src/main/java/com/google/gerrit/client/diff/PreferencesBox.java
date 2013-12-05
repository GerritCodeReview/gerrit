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

import static com.google.gerrit.reviewdb.client.AccountDiffPreference.DEFAULT_CONTEXT;
import static com.google.gerrit.reviewdb.client.AccountDiffPreference.WHOLE_FILE_CONTEXT;
import static com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace.IGNORE_ALL_SPACE;
import static com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace.IGNORE_NONE;
import static com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace.IGNORE_SPACE_AT_EOL;
import static com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace.IGNORE_SPACE_CHANGE;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_ESCAPE;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountApi;
import com.google.gerrit.client.account.DiffPreferences;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.NpIntTextBox;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.ToggleButton;

import net.codemirror.lib.CodeMirror;

/** Displays current diff preferences. */
class PreferencesBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, PreferencesBox> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String dialog();
  }

  private final SideBySide2 view;
  private final CodeMirror cmA;
  private final CodeMirror cmB;
  private DiffPreferences prefs;
  private int oldContext;
  private long contextApplyTime;

  @UiField Style style;
  @UiField Anchor close;
  @UiField ListBox ignoreWhitespace;
  @UiField NpIntTextBox tabWidth;
  @UiField NpIntTextBox context;
  @UiField SimplePanel contextParent;
  @UiField CheckBox contextEntireFile;
  @UiField ToggleButton intralineDifference;
  @UiField ToggleButton syntaxHighlighting;
  @UiField ToggleButton whitespaceErrors;
  @UiField ToggleButton showTabs;
  @UiField ToggleButton topMenu;
  @UiField ToggleButton expandAllComments;
  @UiField Button apply;
  @UiField Button save;

  PreferencesBox(SideBySide2 view) {
    this.view = view;
    this.cmA = view.getCmA();
    this.cmB = view.getCmB();

    initWidget(uiBinder.createAndBindUi(this));
    initIgnoreWhitespace();
  }

  @Override
  public void onLoad() {
    super.onLoad();

    save.setVisible(Gerrit.isSignedIn());
    addDomHandler(new KeyDownHandler() {
      @Override
      public void onKeyDown(KeyDownEvent event) {
        if (event.getNativeKeyCode() == KEY_ESCAPE
            || event.getNativeKeyCode() == ',') {
          close();
        }
      }
    }, KeyDownEvent.getType());
    contextParent.addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        if (!context.isEnabled()) {
          restoreContext();
        }
      }
    }, ClickEvent.getType());
  }

  void set(DiffPreferences prefs) {
    this.prefs = prefs;

    setIgnoreWhitespace(prefs.ignoreWhitespace());
    tabWidth.setIntValue(prefs.tabSize());
    syntaxHighlighting.setValue(prefs.syntaxHighlighting());
    whitespaceErrors.setValue(prefs.showWhitespaceErrors());
    showTabs.setValue(prefs.showTabs());
    topMenu.setValue(!prefs.hideTopMenu());
    expandAllComments.setValue(prefs.expandAllComments());

    switch (view.getIntraLineStatus()) {
      case OFF:
      case OK:
        intralineDifference.setValue(prefs.intralineDifference());
        break;

      case TIMEOUT:
      case FAILURE:
        intralineDifference.setValue(false);
        intralineDifference.setEnabled(false);
        break;
    }

    if (prefs.context() == WHOLE_FILE_CONTEXT) {
      oldContext = DEFAULT_CONTEXT;
      context.setText("");
      context.setEnabled(false);
      contextEntireFile.setValue(true);
    } else {
      context.setIntValue(prefs.context());
      context.setEnabled(true);
      contextEntireFile.setValue(false);
    }
  }

  @UiHandler("ignoreWhitespace")
  void onIgnoreWhitespace(ChangeEvent e) {
    prefs.ignoreWhitespace(Whitespace.valueOf(
        ignoreWhitespace.getValue(ignoreWhitespace.getSelectedIndex())));
    view.reloadDiffInfo();
  }

  @UiHandler("intralineDifference")
  void onIntralineDifference(ValueChangeEvent<Boolean> e) {
    prefs.intralineDifference(e.getValue());
    view.setShowIntraline(prefs.intralineDifference());
  }

  @UiHandler("context")
  void onContext(ValueChangeEvent<String> e) {
    final String v = e.getValue();
    int c;
    if (v != null && v.length() > 0) {
      c = Math.min(Math.max(0, Integer.parseInt(v)), 32767);
      contextEntireFile.setValue(false);
      contextApplyTime = System.currentTimeMillis();
    } else if (v == null || v.isEmpty()) {
      c = WHOLE_FILE_CONTEXT;
      contextEntireFile.setValue(true);
      context.setEnabled(false);
    } else {
      return;
    }
    prefs.context(c);
    view.setContext(prefs.context());
  }

  @UiHandler("contextEntireFile")
  void onContextEntireFile(ValueChangeEvent<Boolean> e) {
    if ((System.currentTimeMillis() - contextApplyTime) < 200) {
      contextEntireFile.setValue(!e.getValue());
    } else if (e.getValue()) {
      oldContext = context.getIntValue();
      context.setText("");
      context.setEnabled(false);
      prefs.context(WHOLE_FILE_CONTEXT);
      view.setContext(prefs.context());
    } else {
      restoreContext();
    }
  }

  private void restoreContext() {
    prefs.context(oldContext > 0 ? oldContext : DEFAULT_CONTEXT);
    context.setIntValue(prefs.context());
    context.setEnabled(true);
    context.setFocus(true);
    context.setSelectionRange(0, context.getText().length());
    contextEntireFile.setValue(false);
    view.setContext(prefs.context());
  }

  @UiHandler("tabWidth")
  void onTabWidth(ValueChangeEvent<String> e) {
    String v = e.getValue();
    if (v != null && v.length() > 0) {
      prefs.tabSize(Math.max(1, Integer.parseInt(v)));
      view.operation(new Runnable() {
        @Override
        public void run() {
          cmA.setOption("tabSize", prefs.tabSize());
          cmB.setOption("tabSize", prefs.tabSize());
        }
      });
    }
  }

  @UiHandler("expandAllComments")
  void onExpandAllComments(ValueChangeEvent<Boolean> e) {
    prefs.expandAllComments(e.getValue());
    view.setExpandAllComments(prefs.expandAllComments());
  }

  @UiHandler("showTabs")
  void onShowTabs(ValueChangeEvent<Boolean> e) {
    prefs.showTabs(e.getValue());
    view.setShowTabs(prefs.showTabs());
  }

  @UiHandler("topMenu")
  void onTopMenu(ValueChangeEvent<Boolean> e) {
    prefs.hideTopMenu(!e.getValue());
    Gerrit.setHeaderVisible(view.diffTable.isHeaderVisible()
        && !prefs.hideTopMenu());
    view.resizeCodeMirror();
  }

  @UiHandler("syntaxHighlighting")
  void onSyntaxHighlighting(ValueChangeEvent<Boolean> e) {
    prefs.syntaxHighlighting(e.getValue());
    view.setSyntaxHighlighting(prefs.syntaxHighlighting());
  }

  @UiHandler("whitespaceErrors")
  void onWhitespaceErrors(ValueChangeEvent<Boolean> e) {
    prefs.showWhitespaceErrors(e.getValue());
    view.operation(new Runnable() {
      @Override
      public void run() {
        cmA.setOption("showTrailingSpace", prefs.showWhitespaceErrors());
        cmB.setOption("showTrailingSpace", prefs.showWhitespaceErrors());
      }
    });
  }

  @UiHandler("apply")
  void onApply(ClickEvent e) {
    close();
  }

  @UiHandler("save")
  void onSave(ClickEvent e) {
    AccountApi.putDiffPreferences(prefs, new GerritCallback<DiffPreferences>() {
      @Override
      public void onSuccess(DiffPreferences result) {
        AccountDiffPreference p = Gerrit.getAccountDiffPreference();
        if (p == null) {
          p = AccountDiffPreference.createDefault(Gerrit.getUserAccount().getId());
        }
        result.copyTo(p);
        Gerrit.setAccountDiffPreference(p);
      }
    });
    close();
  }

  @UiHandler("close")
  void onClose(ClickEvent e) {
    e.preventDefault();
    close();
  }

  void setFocus(boolean focus) {
    ignoreWhitespace.setFocus(focus);
  }

  private void close() {
    ((PopupPanel) getParent()).hide();
  }

  private void setIgnoreWhitespace(Whitespace v) {
    String name = v != null ? v.name() : IGNORE_NONE.name();
    for (int i = 0; i < ignoreWhitespace.getItemCount(); i++) {
      if (ignoreWhitespace.getValue(i).equals(name)) {
        ignoreWhitespace.setSelectedIndex(i);
        return;
      }
    }
    ignoreWhitespace.setSelectedIndex(0);
  }

  private void initIgnoreWhitespace() {
    ignoreWhitespace.addItem(
        PatchUtil.C.whitespaceIGNORE_NONE(),
        IGNORE_NONE.name());
    ignoreWhitespace.addItem(
        PatchUtil.C.whitespaceIGNORE_SPACE_AT_EOL(),
        IGNORE_SPACE_AT_EOL.name());
    ignoreWhitespace.addItem(
        PatchUtil.C.whitespaceIGNORE_SPACE_CHANGE(),
        IGNORE_SPACE_CHANGE.name());
    ignoreWhitespace.addItem(
        PatchUtil.C.whitespaceIGNORE_ALL_SPACE(),
        IGNORE_ALL_SPACE.name());
  }
}
