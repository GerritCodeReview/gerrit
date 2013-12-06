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
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.CommentVisibilityStrategy;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwtjsonrpc.common.VoidResult;

import java.util.Date;

public class MyPreferencesScreen extends SettingsScreen {
  private CheckBox showSiteHeader;
  private CheckBox useFlashClipboard;
  private CheckBox copySelfOnEmails;
  private CheckBox reversePatchSetOrder;
  private CheckBox showUsernameInReviewCategory;
  private CheckBox relativeDateInChangeTable;
  private CheckBox sizeBarInChangeTable;
  private ListBox maximumPageSize;
  private ListBox dateFormat;
  private ListBox timeFormat;
  private ListBox commentVisibilityStrategy;
  private ListBox changeScreen;
  private ListBox diffView;
  private Button save;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    showSiteHeader = new CheckBox(Util.C.showSiteHeader());
    useFlashClipboard = new CheckBox(Util.C.useFlashClipboard());
    copySelfOnEmails = new CheckBox(Util.C.copySelfOnEmails());
    reversePatchSetOrder = new CheckBox(Util.C.reversePatchSetOrder());
    showUsernameInReviewCategory = new CheckBox(Util.C.showUsernameInReviewCategory());
    maximumPageSize = new ListBox();
    for (final short v : PAGESIZE_CHOICES) {
      maximumPageSize.addItem(Util.M.rowsPerPage(v), String.valueOf(v));
    }

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

    final Grid formGrid = new Grid(12, 2);

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

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, showUsernameInReviewCategory);
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
    }
    add(formGrid);

    save = new Button(Util.C.buttonSaveChanges());
    save.setEnabled(false);
    save.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doSave();
      }
    });
    add(save);

    final OnEditEnabler e = new OnEditEnabler(save);
    e.listenTo(showSiteHeader);
    e.listenTo(useFlashClipboard);
    e.listenTo(copySelfOnEmails);
    e.listenTo(reversePatchSetOrder);
    e.listenTo(showUsernameInReviewCategory);
    e.listenTo(maximumPageSize);
    e.listenTo(dateFormat);
    e.listenTo(timeFormat);
    e.listenTo(relativeDateInChangeTable);
    e.listenTo(sizeBarInChangeTable);
    e.listenTo(commentVisibilityStrategy);
    e.listenTo(changeScreen);
    e.listenTo(diffView);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.ACCOUNT_SVC.myAccount(new ScreenLoadCallback<Account>(this) {
      public void preDisplay(final Account result) {
        display(result.getGeneralPreferences());
      }
    });
  }

  private void enable(final boolean on) {
    showSiteHeader.setEnabled(on);
    useFlashClipboard.setEnabled(on);
    copySelfOnEmails.setEnabled(on);
    reversePatchSetOrder.setEnabled(on);
    showUsernameInReviewCategory.setEnabled(on);
    maximumPageSize.setEnabled(on);
    dateFormat.setEnabled(on);
    timeFormat.setEnabled(on);
    relativeDateInChangeTable.setEnabled(on);
    sizeBarInChangeTable.setEnabled(on);
    commentVisibilityStrategy.setEnabled(on);
    changeScreen.setEnabled(on);
    diffView.setEnabled(on);
  }

  private void display(final AccountGeneralPreferences p) {
    showSiteHeader.setValue(p.isShowSiteHeader());
    useFlashClipboard.setValue(p.isUseFlashClipboard());
    copySelfOnEmails.setValue(p.isCopySelfOnEmails());
    reversePatchSetOrder.setValue(p.isReversePatchSetOrder());
    showUsernameInReviewCategory.setValue(p.isShowUsernameInReviewCategory());
    setListBox(maximumPageSize, DEFAULT_PAGESIZE, p.getMaximumPageSize());
    setListBox(dateFormat, AccountGeneralPreferences.DateFormat.STD, //
        p.getDateFormat());
    setListBox(timeFormat, AccountGeneralPreferences.TimeFormat.HHMM_12, //
        p.getTimeFormat());
    relativeDateInChangeTable.setValue(p.isRelativeDateInChangeTable());
    sizeBarInChangeTable.setValue(p.isSizeBarInChangeTable());
    setListBox(commentVisibilityStrategy,
        AccountGeneralPreferences.CommentVisibilityStrategy.EXPAND_RECENT,
        p.getCommentVisibilityStrategy());
    setListBox(changeScreen,
        null,
        p.getChangeScreen());
    setListBox(diffView,
        AccountGeneralPreferences.DiffView.SIDE_BY_SIDE,
        p.getDiffView());
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
    p.setShowUsernameInReviewCategory(showUsernameInReviewCategory.getValue());
    p.setMaximumPageSize(getListBox(maximumPageSize, DEFAULT_PAGESIZE));
    p.setDateFormat(getListBox(dateFormat,
        AccountGeneralPreferences.DateFormat.STD,
        AccountGeneralPreferences.DateFormat.values()));
    p.setTimeFormat(getListBox(timeFormat,
        AccountGeneralPreferences.TimeFormat.HHMM_12,
        AccountGeneralPreferences.TimeFormat.values()));
    p.setRelativeDateInChangeTable(relativeDateInChangeTable.getValue());
    p.setSizeBarInChangeTable(sizeBarInChangeTable.getValue());
    p.setCommentVisibilityStrategy(getListBox(commentVisibilityStrategy,
        CommentVisibilityStrategy.EXPAND_RECENT,
        CommentVisibilityStrategy.values()));
    p.setDiffView(getListBox(diffView,
        AccountGeneralPreferences.DiffView.SIDE_BY_SIDE,
        AccountGeneralPreferences.DiffView.values()));
    p.setChangeScreen(getListBox(changeScreen,
        null,
        AccountGeneralPreferences.ChangeScreen.values()));

    enable(false);
    save.setEnabled(false);

    Util.ACCOUNT_SVC.changePreferences(p, new GerritCallback<VoidResult>() {
      @Override
      public void onSuccess(final VoidResult result) {
        Gerrit.getUserAccount().setGeneralPreferences(p);
        Gerrit.applyUserPreferences();
        Dispatcher.changeScreen2 = false;
        enable(true);
      }

      @Override
      public void onFailure(final Throwable caught) {
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
}
