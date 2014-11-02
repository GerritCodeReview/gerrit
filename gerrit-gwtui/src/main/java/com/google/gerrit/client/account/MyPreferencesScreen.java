// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.account;

import static com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DEFAULT_PAGESIZE;
import static com.google.gerrit.reviewdb.client.AccountGeneralPreferences.PAGESIZE_CHOICES;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.StringListPanel;
import com.google.gerrit.client.config.ConfigServerApi;
import com.google.gerrit.client.extensions.TopMenuItem;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.CommentVisibilityStrategy;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.ReviewCategoryStrategy;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.ListBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MyPreferencesScreen extends SettingsScreen {
  private CheckBox showSiteHeader;
  private CheckBox useFlashClipboard;
  private CheckBox copySelfOnEmails;
  private CheckBox reversePatchSetOrder;
  private CheckBox relativeDateInChangeTable;
  private CheckBox sizeBarInChangeTable;
  private CheckBox legacycidInChangeTable;
  private ListBox maximumPageSize;
  private ListBox dateFormat;
  private ListBox timeFormat;
  private ListBox reviewCategoryStrategy;
  private ListBox commentVisibilityStrategy;
  private ListBox changeScreen;
  private ListBox diffView;
  private ListBox fontSize;
  private CheckBox wrapLines;
  private StringListPanel myMenus;
  private Button save;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    showSiteHeader = new CheckBox(Util.C.showSiteHeader());
    useFlashClipboard = new CheckBox(Util.C.useFlashClipboard());
    copySelfOnEmails = new CheckBox(Util.C.copySelfOnEmails());
    reversePatchSetOrder = new CheckBox(Util.C.reversePatchSetOrder());
    maximumPageSize = new ListBox();
    for (final short v : PAGESIZE_CHOICES) {
      maximumPageSize.addItem(Util.M.rowsPerPage(v), String.valueOf(v));
    }

    reviewCategoryStrategy = new ListBox();
    reviewCategoryStrategy.addItem(
        Util.C.messageShowInReviewCategoryNone(),
        AccountGeneralPreferences.ReviewCategoryStrategy.NONE.name());
    reviewCategoryStrategy.addItem(
        Util.C.messageShowInReviewCategoryName(),
        AccountGeneralPreferences.ReviewCategoryStrategy.NAME.name());
    reviewCategoryStrategy.addItem(
        Util.C.messageShowInReviewCategoryEmail(),
        AccountGeneralPreferences.ReviewCategoryStrategy.EMAIL.name());
    reviewCategoryStrategy.addItem(
        Util.C.messageShowInReviewCategoryUsername(),
        AccountGeneralPreferences.ReviewCategoryStrategy.USERNAME.name());
    reviewCategoryStrategy.addItem(
        Util.C.messageShowInReviewCategoryAbbrev(),
        AccountGeneralPreferences.ReviewCategoryStrategy.ABBREV.name());

    commentVisibilityStrategy = new ListBox();
    commentVisibilityStrategy.addItem(
        com.google.gerrit.client.changes.Util.C.messageCollapseAll(),
        AccountGeneralPreferences.CommentVisibilityStrategy.COLLAPSE_ALL.name());
    commentVisibilityStrategy.addItem(
        com.google.gerrit.client.changes.Util.C.messageExpandMostRecent(),
        AccountGeneralPreferences.CommentVisibilityStrategy.EXPAND_MOST_RECENT.name());
    commentVisibilityStrategy.addItem(
        com.google.gerrit.client.changes.Util.C.messageExpandRecent(),
        AccountGeneralPreferences.CommentVisibilityStrategy.EXPAND_RECENT.name());
    commentVisibilityStrategy.addItem(
        com.google.gerrit.client.changes.Util.C.messageExpandAll(),
        AccountGeneralPreferences.CommentVisibilityStrategy.EXPAND_ALL.name());

    changeScreen = new ListBox();
    changeScreen.addItem(
        Util.M.changeScreenServerDefault(
            getLabel(Gerrit.getConfig().getChangeScreen())),
        "");
    changeScreen.addItem(
        Util.C.changeScreenOldUi(),
        AccountGeneralPreferences.ChangeScreen.OLD_UI.name());
    changeScreen.addItem(
        Util.C.changeScreenNewUi(),
        AccountGeneralPreferences.ChangeScreen.CHANGE_SCREEN2.name());

    diffView = new ListBox();
    diffView.addItem(
        com.google.gerrit.client.changes.Util.C.sideBySide(),
        AccountGeneralPreferences.DiffView.SIDE_BY_SIDE.name());
    diffView.addItem(
        com.google.gerrit.client.changes.Util.C.unifiedDiff(),
        AccountGeneralPreferences.DiffView.UNIFIED_DIFF.name());

    fontSize = new ListBox();
    fontSize.addItem(
        Util.C.fontSizeSmall(),
        AccountGeneralPreferences.FontSize.SMALL.name());
    fontSize.addItem(
        Util.C.fontSizeMedium(),
        AccountGeneralPreferences.FontSize.MEDIUM.name());
    fontSize.addItem(
        Util.C.fontSizeLarge(),
        AccountGeneralPreferences.FontSize.LARGE.name());

    wrapLines = new CheckBox(Util.C.wrapLines());

    Date now = new Date();
    dateFormat = new ListBox();
    for (AccountGeneralPreferences.DateFormat fmt : AccountGeneralPreferences.DateFormat
        .values()) {
      StringBuilder r = new StringBuilder();
      r.append(DateTimeFormat.getFormat(fmt.getShortFormat()).format(now));
      r.append(" ; ");
      r.append(DateTimeFormat.getFormat(fmt.getLongFormat()).format(now));
      dateFormat.addItem(r.toString(), fmt.name());
    }

    timeFormat = new ListBox();
    for (AccountGeneralPreferences.TimeFormat fmt : AccountGeneralPreferences.TimeFormat
        .values()) {
      StringBuilder r = new StringBuilder();
      r.append(DateTimeFormat.getFormat(fmt.getFormat()).format(now));
      timeFormat.addItem(r.toString(), fmt.name());
    }

    FlowPanel dateTimePanel = new FlowPanel();

    final int labelIdx, fieldIdx;
    if (LocaleInfo.getCurrentLocale().isRTL()) {
      labelIdx = 1;
      fieldIdx = 0;
      dateTimePanel.add(timeFormat);
      dateTimePanel.add(dateFormat);
    } else {
      labelIdx = 0;
      fieldIdx = 1;
      dateTimePanel.add(dateFormat);
      dateTimePanel.add(timeFormat);
    }

    relativeDateInChangeTable = new CheckBox(Util.C.showRelativeDateInChangeTable());
    sizeBarInChangeTable = new CheckBox(Util.C.showSizeBarInChangeTable());
    legacycidInChangeTable = new CheckBox(Util.C.showLegacycidInChangeTable());

    final Grid formGrid = new Grid(15, 2);

    int row = 0;
    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, showSiteHeader);
    row++;

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, useFlashClipboard);
    row++;

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, copySelfOnEmails);
    row++;

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, reversePatchSetOrder);
    row++;

    formGrid.setText(row, labelIdx, Util.C.reviewCategoryLabel());
    formGrid.setWidget(row, fieldIdx, reviewCategoryStrategy);
    row++;

    formGrid.setText(row, labelIdx, Util.C.maximumPageSizeFieldLabel());
    formGrid.setWidget(row, fieldIdx, maximumPageSize);
    row++;

    formGrid.setText(row, labelIdx, Util.C.dateFormatLabel());
    formGrid.setWidget(row, fieldIdx, dateTimePanel);
    row++;

    if (Gerrit.getConfig().getNewFeatures()) {
      formGrid.setText(row, labelIdx, "");
      formGrid.setWidget(row, fieldIdx, relativeDateInChangeTable);
      row++;

      formGrid.setText(row, labelIdx, "");
      formGrid.setWidget(row, fieldIdx, sizeBarInChangeTable);
      row++;

      formGrid.setText(row, labelIdx, "");
      formGrid.setWidget(row, fieldIdx, legacycidInChangeTable);
      row++;
    }

    formGrid.setText(row, labelIdx, Util.C.commentVisibilityLabel());
    formGrid.setWidget(row, fieldIdx, commentVisibilityStrategy);
    row++;

    if (Gerrit.getConfig().getNewFeatures()) {
      formGrid.setText(row, labelIdx, Util.C.changeScreenLabel());
      formGrid.setWidget(row, fieldIdx, changeScreen);
      row++;

      formGrid.setText(row, labelIdx, Util.C.diffViewLabel());
      formGrid.setWidget(row, fieldIdx, diffView);
      row++;
    }

    formGrid.setText(row, labelIdx, Util.C.fontSizeLabel());
    formGrid.setWidget(row, fieldIdx, fontSize);
    row++;

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, wrapLines);
    row++;

    add(formGrid);

    save = new Button(Util.C.buttonSaveChanges());
    save.setEnabled(false);
    save.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doSave();
      }
    });

    myMenus = new MyMenuPanel(save);
    add(myMenus);

    add(save);

    final OnEditEnabler e = new OnEditEnabler(save);
    e.listenTo(showSiteHeader);
    e.listenTo(useFlashClipboard);
    e.listenTo(copySelfOnEmails);
    e.listenTo(reversePatchSetOrder);
    e.listenTo(maximumPageSize);
    e.listenTo(dateFormat);
    e.listenTo(timeFormat);
    e.listenTo(relativeDateInChangeTable);
    e.listenTo(sizeBarInChangeTable);
    e.listenTo(legacycidInChangeTable);
    e.listenTo(reviewCategoryStrategy);
    e.listenTo(commentVisibilityStrategy);
    e.listenTo(changeScreen);
    e.listenTo(diffView);
    e.listenTo(fontSize);
    e.listenTo(wrapLines);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    AccountApi.self().view("preferences")
        .get(new ScreenLoadCallback<Preferences>(this) {
      @Override
      public void preDisplay(Preferences prefs) {
        display(prefs);
      }
    });
  }

  private void enable(final boolean on) {
    showSiteHeader.setEnabled(on);
    useFlashClipboard.setEnabled(on);
    copySelfOnEmails.setEnabled(on);
    reversePatchSetOrder.setEnabled(on);
    maximumPageSize.setEnabled(on);
    dateFormat.setEnabled(on);
    timeFormat.setEnabled(on);
    relativeDateInChangeTable.setEnabled(on);
    sizeBarInChangeTable.setEnabled(on);
    legacycidInChangeTable.setEnabled(on);
    reviewCategoryStrategy.setEnabled(on);
    commentVisibilityStrategy.setEnabled(on);
    changeScreen.setEnabled(on);
    diffView.setEnabled(on);
    fontSize.setEnabled(on);
    wrapLines.setEnabled(on);
  }

  private void display(Preferences p) {
    showSiteHeader.setValue(p.showSiteHeader());
    useFlashClipboard.setValue(p.useFlashClipboard());
    copySelfOnEmails.setValue(p.copySelfOnEmail());
    reversePatchSetOrder.setValue(p.reversePatchSetOrder());
    setListBox(maximumPageSize, DEFAULT_PAGESIZE, p.changesPerPage());
    setListBox(dateFormat, AccountGeneralPreferences.DateFormat.STD, //
        p.dateFormat());
    setListBox(timeFormat, AccountGeneralPreferences.TimeFormat.HHMM_12, //
        p.timeFormat());
    relativeDateInChangeTable.setValue(p.relativeDateInChangeTable());
    sizeBarInChangeTable.setValue(p.sizeBarInChangeTable());
    legacycidInChangeTable.setValue(p.legacycidInChangeTable());
    setListBox(reviewCategoryStrategy,
        AccountGeneralPreferences.ReviewCategoryStrategy.NONE,
        p.reviewCategoryStrategy());
    setListBox(commentVisibilityStrategy,
        AccountGeneralPreferences.CommentVisibilityStrategy.EXPAND_RECENT,
        p.commentVisibilityStrategy());
    setListBox(changeScreen,
        null,
        p.changeScreen());
    setListBox(diffView,
        AccountGeneralPreferences.DiffView.SIDE_BY_SIDE,
        p.diffView());
    setListBox(fontSize,
        AccountGeneralPreferences.FontSize.SMALL,
        p.fontSize());
    wrapLines.setValue(p.wrapLines());
    display(p.my());
  }

  private void display(JsArray<TopMenuItem> items) {
    List<List<String>> values = new ArrayList<>();
    for (TopMenuItem item : Natives.asList(items)) {
      values.add(Arrays.asList(item.getName(), item.getUrl()));
    }
    myMenus.display(values);
  }

  private void setListBox(final ListBox f, final short defaultValue,
      final short currentValue) {
    setListBox(f, String.valueOf(defaultValue), String.valueOf(currentValue));
  }

  private <T extends Enum<?>> void setListBox(final ListBox f,
      final T defaultValue, final T currentValue) {
    setListBox(f,
        defaultValue != null ? defaultValue.name() : "",
        currentValue != null ? currentValue.name() : "");
  }

  private void setListBox(final ListBox f, final String defaultValue,
      final String currentValue) {
    final int n = f.getItemCount();
    for (int i = 0; i < n; i++) {
      if (f.getValue(i).equals(currentValue)) {
        f.setSelectedIndex(i);
        return;
      }
    }
    if (!currentValue.equals(defaultValue)) {
      setListBox(f, defaultValue, defaultValue);
    }
  }

  private short getListBox(final ListBox f, final short defaultValue) {
    final int idx = f.getSelectedIndex();
    if (0 <= idx) {
      return Short.parseShort(f.getValue(idx));
    }
    return defaultValue;
  }

  private <T extends Enum<?>> T getListBox(final ListBox f,
      final T defaultValue, T[] all) {
    final int idx = f.getSelectedIndex();
    if (0 <= idx) {
      String v = f.getValue(idx);
      if ("".equals(v)) {
        return defaultValue;
      }
      for (T t : all) {
        if (t.name().equals(v)) {
          return t;
        }
      }
    }
    return defaultValue;
  }

  private void doSave() {
    final AccountGeneralPreferences p = new AccountGeneralPreferences();
    p.setShowSiteHeader(showSiteHeader.getValue());
    p.setUseFlashClipboard(useFlashClipboard.getValue());
    p.setCopySelfOnEmails(copySelfOnEmails.getValue());
    p.setReversePatchSetOrder(reversePatchSetOrder.getValue());
    p.setMaximumPageSize(getListBox(maximumPageSize, DEFAULT_PAGESIZE));
    p.setDateFormat(getListBox(dateFormat,
        AccountGeneralPreferences.DateFormat.STD,
        AccountGeneralPreferences.DateFormat.values()));
    p.setTimeFormat(getListBox(timeFormat,
        AccountGeneralPreferences.TimeFormat.HHMM_12,
        AccountGeneralPreferences.TimeFormat.values()));
    p.setRelativeDateInChangeTable(relativeDateInChangeTable.getValue());
    p.setSizeBarInChangeTable(sizeBarInChangeTable.getValue());
    p.setLegacycidInChangeTable(legacycidInChangeTable.getValue());
    p.setReviewCategoryStrategy(getListBox(reviewCategoryStrategy,
        ReviewCategoryStrategy.NONE,
        ReviewCategoryStrategy.values()));
    p.setCommentVisibilityStrategy(getListBox(commentVisibilityStrategy,
        CommentVisibilityStrategy.EXPAND_RECENT,
        CommentVisibilityStrategy.values()));
    p.setDiffView(getListBox(diffView,
        AccountGeneralPreferences.DiffView.SIDE_BY_SIDE,
        AccountGeneralPreferences.DiffView.values()));
    p.setChangeScreen(getListBox(changeScreen,
        null,
        AccountGeneralPreferences.ChangeScreen.values()));
    p.setFontSize(getListBox(fontSize,
        AccountGeneralPreferences.FontSize.SMALL,
        AccountGeneralPreferences.FontSize.values()));
    p.setWrapLines(wrapLines.getValue());

    enable(false);
    save.setEnabled(false);

    List<TopMenuItem> items = new ArrayList<>();
    for (List<String> v : myMenus.getValues()) {
      items.add(TopMenuItem.create(v.get(0), v.get(1)));
    }

    AccountApi.self().view("preferences")
        .post(Preferences.create(p, items), new GerritCallback<Preferences>() {
          @Override
          public void onSuccess(Preferences prefs) {
            Gerrit.getUserAccount().setGeneralPreferences(p);
            Gerrit.applyUserPreferences();
            Dispatcher.changeScreen2 = false;
            enable(true);
            display(prefs);
            Gerrit.refreshMenuBar();
          }

          @Override
          public void onFailure(Throwable caught) {
            enable(true);
            save.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private static String getLabel(AccountGeneralPreferences.ChangeScreen ui) {
    if (ui == null) {
      return "";
    }
    switch (ui) {
      case OLD_UI:
        return Util.C.changeScreenOldUi();
      case CHANGE_SCREEN2:
        return Util.C.changeScreenNewUi();
      default:
        return ui.name();
    }
  }

  private class MyMenuPanel extends StringListPanel {
    MyMenuPanel(Button save) {
      super(Util.C.myMenu(), Arrays.asList(Util.C.myMenuName(),
          Util.C.myMenuUrl()), save, false);

      setInfo(Util.C.myMenuInfo());

      Button resetButton = new Button(Util.C.myMenuReset());
      resetButton.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          ConfigServerApi.defaultPreferences(new GerritCallback<Preferences>() {
            @Override
            public void onSuccess(Preferences p) {
              MyPreferencesScreen.this.display(p.my());
              widget.setEnabled(true);
            }
          });
        }
      });
      buttonPanel.add(resetButton);
    }
  }
}
