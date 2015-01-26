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
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.NpIntTextBox;
import com.google.gerrit.extensions.client.Theme;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.ToggleButton;

import net.codemirror.mode.ModeInfo;
import net.codemirror.mode.ModeInjector;
import net.codemirror.theme.ThemeLoader;

import java.util.Objects;

/** Displays current diff preferences. */
class PreferencesBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, PreferencesBox> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String dialog();
  }

  private final SideBySide view;
  private DiffPreferences prefs;
  private int contextLastValue;
  private Timer updateContextTimer;

  @UiField Style style;
  @UiField Anchor close;
  @UiField ListBox ignoreWhitespace;
  @UiField NpIntTextBox tabWidth;
  @UiField NpIntTextBox lineLength;
  @UiField NpIntTextBox context;
  @UiField CheckBox contextEntireFile;
  @UiField ToggleButton intralineDifference;
  @UiField ToggleButton syntaxHighlighting;
  @UiField ToggleButton whitespaceErrors;
  @UiField ToggleButton showTabs;
  @UiField ToggleButton lineNumbers;
  @UiField ToggleButton leftSide;
  @UiField ToggleButton emptyPane;
  @UiField ToggleButton topMenu;
  @UiField ToggleButton autoHideDiffTableHeader;
  @UiField ToggleButton manualReview;
  @UiField ToggleButton expandAllComments;
  @UiField ToggleButton renderEntireFile;
  @UiField ListBox theme;
  @UiField ListBox mode;
  @UiField Button apply;
  @UiField Button save;

  PreferencesBox(SideBySide view) {
    this.view = view;

    initWidget(uiBinder.createAndBindUi(this));
    initIgnoreWhitespace();
    initTheme();
    initMode();
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
    updateContextTimer = new Timer() {
      @Override
      public void run() {
        if (prefs.context() == WHOLE_FILE_CONTEXT) {
          contextEntireFile.setValue(true);
        }
        if (view.canEnableRenderEntireFile(prefs)) {
          renderEntireFile.setEnabled(true);
        } else {
          if (prefs.renderEntireFile()) {
            prefs.renderEntireFile(false);
            renderEntireFile.setValue(false);
            view.updateRenderEntireFile();
          }
          renderEntireFile.setEnabled(false);
        }
        view.setContext(prefs.context());
      }
    };
  }

  void set(DiffPreferences prefs) {
    this.prefs = prefs;

    setIgnoreWhitespace(prefs.ignoreWhitespace());
    tabWidth.setIntValue(prefs.tabSize());
    if (Patch.COMMIT_MSG.equals(view.getPath())) {
      lineLength.setEnabled(false);
      lineLength.setIntValue(72);
    } else {
      lineLength.setEnabled(true);
      lineLength.setIntValue(prefs.lineLength());
    }
    syntaxHighlighting.setValue(prefs.syntaxHighlighting());
    whitespaceErrors.setValue(prefs.showWhitespaceErrors());
    showTabs.setValue(prefs.showTabs());
    lineNumbers.setValue(prefs.showLineNumbers());
    leftSide.setValue(view.diffTable.isVisibleA());
    emptyPane.setValue(!prefs.hideEmptyPane());
    leftSide.setEnabled(!(prefs.hideEmptyPane()
        && view.diffTable.getChangeType() == ChangeType.ADDED));
    topMenu.setValue(!prefs.hideTopMenu());
    autoHideDiffTableHeader.setValue(!prefs.autoHideDiffTableHeader());
    manualReview.setValue(prefs.manualReview());
    expandAllComments.setValue(prefs.expandAllComments());
    renderEntireFile.setValue(prefs.renderEntireFile());
    renderEntireFile.setEnabled(view.canEnableRenderEntireFile(prefs));
    setTheme(prefs.theme());

    mode.setEnabled(prefs.syntaxHighlighting());
    if (prefs.syntaxHighlighting()) {
      setMode(view.getCmFromSide(DisplaySide.B).getStringOption("mode"));
    }

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
      contextLastValue = DEFAULT_CONTEXT;
      context.setText("");
      contextEntireFile.setValue(true);
    } else {
      context.setIntValue(prefs.context());
      contextEntireFile.setValue(false);
    }
  }

  @UiHandler("ignoreWhitespace")
  void onIgnoreWhitespace(@SuppressWarnings("unused") ChangeEvent e) {
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
  void onContextKey(KeyPressEvent e) {
    if (contextEntireFile.getValue()) {
      char c = e.getCharCode();
      if ('0' <= c && c <= '9') {
        contextEntireFile.setValue(false);
      }
    }
  }

  @UiHandler("context")
  void onContext(ValueChangeEvent<String> e) {
    String v = e.getValue();
    int c;
    if (v != null && v.length() > 0) {
      c = Math.min(Math.max(0, Integer.parseInt(v)), 32767);
      contextEntireFile.setValue(false);
    } else if (v == null || v.isEmpty()) {
      c = WHOLE_FILE_CONTEXT;
    } else {
      return;
    }
    prefs.context(c);
    updateContextTimer.schedule(200);
  }

  @UiHandler("contextEntireFile")
  void onContextEntireFile(ValueChangeEvent<Boolean> e) {
    // If a click arrives too fast after onContext applied an update
    // the user committed the context line update by clicking on the
    // whole file checkmark. Drop this event, but transfer focus.
    if (e.getValue()) {
      contextLastValue = context.getIntValue();
      context.setText("");
      prefs.context(WHOLE_FILE_CONTEXT);
    } else {
      prefs.context(contextLastValue > 0 ? contextLastValue : DEFAULT_CONTEXT);
      context.setIntValue(prefs.context());
      context.setFocus(true);
      context.setSelectionRange(0, context.getText().length());
    }
    updateContextTimer.schedule(200);
  }

  @UiHandler("tabWidth")
  void onTabWidth(ValueChangeEvent<String> e) {
    String v = e.getValue();
    if (v != null && v.length() > 0) {
      prefs.tabSize(Math.max(1, Integer.parseInt(v)));
      view.operation(new Runnable() {
        @Override
        public void run() {
          int v = prefs.tabSize();
          view.getCmFromSide(DisplaySide.A).setOption("tabSize", v);
          view.getCmFromSide(DisplaySide.B).setOption("tabSize", v);
        }
      });
    }
  }

  @UiHandler("lineLength")
  void onLineLength(ValueChangeEvent<String> e) {
    String v = e.getValue();
    if (v != null && v.length() > 0) {
      prefs.lineLength(Math.max(1, Integer.parseInt(v)));
      view.operation(new Runnable() {
        @Override
        public void run() {
          view.setLineLength(prefs.lineLength());
        }
      });
    }
  }
  @UiHandler("expandAllComments")
  void onExpandAllComments(ValueChangeEvent<Boolean> e) {
    prefs.expandAllComments(e.getValue());
    view.getCommentManager().setExpandAllComments(prefs.expandAllComments());
  }

  @UiHandler("showTabs")
  void onShowTabs(ValueChangeEvent<Boolean> e) {
    prefs.showTabs(e.getValue());
    view.setShowTabs(prefs.showTabs());
  }

  @UiHandler("lineNumbers")
  void onLineNumbers(ValueChangeEvent<Boolean> e) {
    prefs.showLineNumbers(e.getValue());
    view.setShowLineNumbers(prefs.showLineNumbers());
  }

  @UiHandler("leftSide")
  void onLeftSide(ValueChangeEvent<Boolean> e) {
    view.diffTable.setVisibleA(e.getValue());
  }

  @UiHandler("emptyPane")
  void onHideEmptyPane(ValueChangeEvent<Boolean> e) {
    prefs.hideEmptyPane(!e.getValue());
    view.diffTable.setHideEmptyPane(prefs.hideEmptyPane());
    if (prefs.hideEmptyPane()) {
      if (view.diffTable.getChangeType() == ChangeType.ADDED) {
        leftSide.setValue(false);
        leftSide.setEnabled(false);
      }
    } else {
      leftSide.setValue(view.diffTable.isVisibleA());
      leftSide.setEnabled(true);
    }
  }

  @UiHandler("topMenu")
  void onTopMenu(ValueChangeEvent<Boolean> e) {
    prefs.hideTopMenu(!e.getValue());
    Gerrit.setHeaderVisible(!prefs.hideTopMenu());
    view.resizeCodeMirror();
  }

  @UiHandler("autoHideDiffTableHeader")
  void onAutoHideDiffTableHeader(ValueChangeEvent<Boolean> e) {
    prefs.autoHideDiffTableHeader(!e.getValue());
    view.setAutoHideDiffHeader(!e.getValue());
  }

  @UiHandler("manualReview")
  void onManualReview(ValueChangeEvent<Boolean> e) {
    prefs.manualReview(e.getValue());
  }

  @UiHandler("syntaxHighlighting")
  void onSyntaxHighlighting(ValueChangeEvent<Boolean> e) {
    prefs.syntaxHighlighting(e.getValue());
    mode.setEnabled(prefs.syntaxHighlighting());
    if (prefs.syntaxHighlighting()) {
      setMode(view.getContentType());
    }
    view.setSyntaxHighlighting(prefs.syntaxHighlighting());
  }

  @UiHandler("mode")
  void onMode(@SuppressWarnings("unused") ChangeEvent e) {
    final String mode = getSelectedMode();
    prefs.syntaxHighlighting(true);
    syntaxHighlighting.setValue(true, false);
    new ModeInjector().add(mode).inject(new GerritCallback<Void>() {
      @Override
      public void onSuccess(Void result) {
        if (prefs.syntaxHighlighting()
            && Objects.equals(mode, getSelectedMode())
            && view.isAttached()) {
          view.operation(new Runnable() {
            @Override
            public void run() {
              view.getCmFromSide(DisplaySide.A).setOption("mode", mode);
              view.getCmFromSide(DisplaySide.B).setOption("mode", mode);
            }
          });
        }
      }
    });
  }

  private String getSelectedMode() {
    String m = mode.getValue(mode.getSelectedIndex());
    return m != null && !m.isEmpty() ? m : null;
  }

  @UiHandler("whitespaceErrors")
  void onWhitespaceErrors(ValueChangeEvent<Boolean> e) {
    prefs.showWhitespaceErrors(e.getValue());
    view.operation(new Runnable() {
      @Override
      public void run() {
        boolean s = prefs.showWhitespaceErrors();
        view.getCmFromSide(DisplaySide.A).setOption("showTrailingSpace", s);
        view.getCmFromSide(DisplaySide.B).setOption("showTrailingSpace", s);
      }
    });
  }

  @UiHandler("renderEntireFile")
  void onRenderEntireFile(ValueChangeEvent<Boolean> e) {
    prefs.renderEntireFile(e.getValue());
    view.updateRenderEntireFile();
  }

  @UiHandler("theme")
  void onTheme(@SuppressWarnings("unused") ChangeEvent e) {
    final Theme newTheme = getSelectedTheme();
    prefs.theme(newTheme);
    ThemeLoader.loadTheme(newTheme, new GerritCallback<Void>() {
      @Override
      public void onSuccess(Void result) {
        view.operation(new Runnable() {
          @Override
          public void run() {
            if (getSelectedTheme() == newTheme && isAttached()) {
              String t = newTheme.name().toLowerCase();
              view.getCmFromSide(DisplaySide.A).setOption("theme", t);
              view.getCmFromSide(DisplaySide.B).setOption("theme", t);
              view.setThemeStyles(newTheme.isDark());
            }
          }
        });
      }
    });
  }

  private Theme getSelectedTheme() {
    return Theme.valueOf(theme.getValue(theme.getSelectedIndex()));
  }

  @UiHandler("apply")
  void onApply(@SuppressWarnings("unused") ClickEvent e) {
    close();
  }

  @UiHandler("save")
  void onSave(@SuppressWarnings("unused") ClickEvent e) {
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

  private void initMode() {
    mode.addItem("", "");
    for (ModeInfo m : Natives.asList(ModeInfo.all())) {
      mode.addItem(m.name(), m.mime());
    }
  }

  private void setMode(String modeType) {
    if (modeType != null && !modeType.isEmpty()) {
      ModeInfo m = ModeInfo.findModeByMIME(modeType);
      if (m != null) {
        for (int i = 0; i < mode.getItemCount(); i++) {
          if (mode.getValue(i).equals(m.mime())) {
            mode.setSelectedIndex(i);
            return;
          }
        }
      }
    }
    mode.setSelectedIndex(0);
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
    for (Theme t : Theme.values()) {
      theme.addItem(t.name().toLowerCase(), t.name());
    }
  }
}
