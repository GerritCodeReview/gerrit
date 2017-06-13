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

import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.DEFAULT_PAGESIZE;
import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.PAGESIZE_CHOICES;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.GerritUiExtensionPoint;
import com.google.gerrit.client.StringListPanel;
import com.google.gerrit.client.api.ExtensionPanel;
import com.google.gerrit.client.config.ConfigServerApi;
import com.google.gerrit.client.info.GeneralPreferences;
import com.google.gerrit.client.info.TopMenuItem;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.ReviewCategoryStrategy;
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
import com.google.gwtexpui.user.client.UserAgent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MyPreferencesScreen extends SettingsScreen {
  private CheckBox showSiteHeader;
  private CheckBox useFlashClipboard;
  private CheckBox highlightAssigneeInChangeTable;
  private CheckBox relativeDateInChangeTable;
  private CheckBox sizeBarInChangeTable;
  private CheckBox legacycidInChangeTable;
  private CheckBox muteCommonPathPrefixes;
  private CheckBox signedOffBy;
  private CheckBox publishCommentsOnPush;
  private ListBox maximumPageSize;
  private ListBox dateFormat;
  private ListBox timeFormat;
  private ListBox reviewCategoryStrategy;
  private ListBox diffView;
  private ListBox emailStrategy;
  private ListBox emailFormat;
  private ListBox defaultBaseForMerges;
  private StringListPanel myMenus;
  private Button save;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    showSiteHeader = new CheckBox(Util.C.showSiteHeader());
    useFlashClipboard = new CheckBox(Util.C.useFlashClipboard());
    maximumPageSize = new ListBox();
    for (int v : PAGESIZE_CHOICES) {
      maximumPageSize.addItem(Util.M.rowsPerPage(v), String.valueOf(v));
    }

    reviewCategoryStrategy = new ListBox();
    reviewCategoryStrategy.addItem(
        Util.C.messageShowInReviewCategoryNone(),
        GeneralPreferencesInfo.ReviewCategoryStrategy.NONE.name());
    reviewCategoryStrategy.addItem(
        Util.C.messageShowInReviewCategoryName(),
        GeneralPreferencesInfo.ReviewCategoryStrategy.NAME.name());
    reviewCategoryStrategy.addItem(
        Util.C.messageShowInReviewCategoryEmail(),
        GeneralPreferencesInfo.ReviewCategoryStrategy.EMAIL.name());
    reviewCategoryStrategy.addItem(
        Util.C.messageShowInReviewCategoryUsername(),
        GeneralPreferencesInfo.ReviewCategoryStrategy.USERNAME.name());
    reviewCategoryStrategy.addItem(
        Util.C.messageShowInReviewCategoryAbbrev(),
        GeneralPreferencesInfo.ReviewCategoryStrategy.ABBREV.name());

    emailStrategy = new ListBox();
    emailStrategy.addItem(
        Util.C.messageCCMeOnMyComments(),
        GeneralPreferencesInfo.EmailStrategy.CC_ON_OWN_COMMENTS.name());
    emailStrategy.addItem(
        Util.C.messageEnabled(), GeneralPreferencesInfo.EmailStrategy.ENABLED.name());
    emailStrategy.addItem(
        Util.C.messageDisabled(), GeneralPreferencesInfo.EmailStrategy.DISABLED.name());

    emailFormat = new ListBox();
    emailFormat.addItem(
        Util.C.messagePlaintextOnly(), GeneralPreferencesInfo.EmailFormat.PLAINTEXT.name());
    emailFormat.addItem(
        Util.C.messageHtmlPlaintext(), GeneralPreferencesInfo.EmailFormat.HTML_PLAINTEXT.name());

    defaultBaseForMerges = new ListBox();
    defaultBaseForMerges.addItem(
        Util.C.autoMerge(), GeneralPreferencesInfo.DefaultBase.AUTO_MERGE.name());
    defaultBaseForMerges.addItem(
        Util.C.firstParent(), GeneralPreferencesInfo.DefaultBase.FIRST_PARENT.name());

    diffView = new ListBox();
    diffView.addItem(
        com.google.gerrit.client.changes.Util.C.sideBySide(),
        GeneralPreferencesInfo.DiffView.SIDE_BY_SIDE.name());
    diffView.addItem(
        com.google.gerrit.client.changes.Util.C.unifiedDiff(),
        GeneralPreferencesInfo.DiffView.UNIFIED_DIFF.name());

    Date now = new Date();
    dateFormat = new ListBox();
    for (GeneralPreferencesInfo.DateFormat fmt : GeneralPreferencesInfo.DateFormat.values()) {
      StringBuilder r = new StringBuilder();
      r.append(DateTimeFormat.getFormat(fmt.getShortFormat()).format(now));
      r.append(" ; ");
      r.append(DateTimeFormat.getFormat(fmt.getLongFormat()).format(now));
      dateFormat.addItem(r.toString(), fmt.name());
    }

    timeFormat = new ListBox();
    for (GeneralPreferencesInfo.TimeFormat fmt : GeneralPreferencesInfo.TimeFormat.values()) {
      StringBuilder r = new StringBuilder();
      r.append(DateTimeFormat.getFormat(fmt.getFormat()).format(now));
      timeFormat.addItem(r.toString(), fmt.name());
    }

    FlowPanel dateTimePanel = new FlowPanel();

    final int labelIdx;
    final int fieldIdx;
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
    highlightAssigneeInChangeTable = new CheckBox(Util.C.highlightAssigneeInChangeTable());
    relativeDateInChangeTable = new CheckBox(Util.C.showRelativeDateInChangeTable());
    sizeBarInChangeTable = new CheckBox(Util.C.showSizeBarInChangeTable());
    legacycidInChangeTable = new CheckBox(Util.C.showLegacycidInChangeTable());
    muteCommonPathPrefixes = new CheckBox(Util.C.muteCommonPathPrefixes());
    signedOffBy = new CheckBox(Util.C.signedOffBy());
    publishCommentsOnPush = new CheckBox(Util.C.publishCommentsOnPush());

    boolean flashClippy = !UserAgent.hasJavaScriptClipboard() && UserAgent.Flash.isInstalled();
    final Grid formGrid = new Grid(15 + (flashClippy ? 1 : 0), 2);

    int row = 0;

    formGrid.setText(row, labelIdx, Util.C.reviewCategoryLabel());
    formGrid.setWidget(row, fieldIdx, reviewCategoryStrategy);
    row++;

    formGrid.setText(row, labelIdx, Util.C.maximumPageSizeFieldLabel());
    formGrid.setWidget(row, fieldIdx, maximumPageSize);
    row++;

    formGrid.setText(row, labelIdx, Util.C.dateFormatLabel());
    formGrid.setWidget(row, fieldIdx, dateTimePanel);
    row++;

    formGrid.setText(row, labelIdx, Util.C.emailFieldLabel());
    formGrid.setWidget(row, fieldIdx, emailStrategy);
    row++;

    formGrid.setText(row, labelIdx, Util.C.emailFormatFieldLabel());
    formGrid.setWidget(row, fieldIdx, emailFormat);
    row++;

    formGrid.setText(row, labelIdx, Util.C.defaultBaseForMerges());
    formGrid.setWidget(row, fieldIdx, defaultBaseForMerges);
    row++;

    formGrid.setText(row, labelIdx, Util.C.diffViewLabel());
    formGrid.setWidget(row, fieldIdx, diffView);
    row++;

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, showSiteHeader);
    row++;

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, highlightAssigneeInChangeTable);
    row++;

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, relativeDateInChangeTable);
    row++;

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, sizeBarInChangeTable);
    row++;

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, legacycidInChangeTable);
    row++;

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, muteCommonPathPrefixes);
    row++;

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, signedOffBy);
    row++;

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, publishCommentsOnPush);
    row++;

    if (flashClippy) {
      formGrid.setText(row, labelIdx, "");
      formGrid.setWidget(row, fieldIdx, useFlashClipboard);
    }

    add(formGrid);

    save = new Button(Util.C.buttonSaveChanges());
    save.setEnabled(false);
    save.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            doSave();
          }
        });

    myMenus = new MyMenuPanel(save);
    add(myMenus);

    add(save);

    final OnEditEnabler e = new OnEditEnabler(save);
    e.listenTo(showSiteHeader);
    e.listenTo(useFlashClipboard);
    e.listenTo(maximumPageSize);
    e.listenTo(dateFormat);
    e.listenTo(timeFormat);
    e.listenTo(highlightAssigneeInChangeTable);
    e.listenTo(relativeDateInChangeTable);
    e.listenTo(sizeBarInChangeTable);
    e.listenTo(legacycidInChangeTable);
    e.listenTo(muteCommonPathPrefixes);
    e.listenTo(signedOffBy);
    e.listenTo(publishCommentsOnPush);
    e.listenTo(diffView);
    e.listenTo(reviewCategoryStrategy);
    e.listenTo(emailStrategy);
    e.listenTo(emailFormat);
    e.listenTo(defaultBaseForMerges);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    ExtensionPanel extensionPanel =
        createExtensionPoint(GerritUiExtensionPoint.PREFERENCES_SCREEN_BOTTOM);
    extensionPanel.addStyleName(Gerrit.RESOURCES.css().extensionPanel());
    add(extensionPanel);

    AccountApi.self()
        .view("preferences")
        .get(
            new ScreenLoadCallback<GeneralPreferences>(this) {
              @Override
              public void preDisplay(GeneralPreferences prefs) {
                display(prefs);
              }
            });
  }

  private void enable(boolean on) {
    showSiteHeader.setEnabled(on);
    useFlashClipboard.setEnabled(on);
    maximumPageSize.setEnabled(on);
    dateFormat.setEnabled(on);
    timeFormat.setEnabled(on);
    highlightAssigneeInChangeTable.setEnabled(on);
    relativeDateInChangeTable.setEnabled(on);
    sizeBarInChangeTable.setEnabled(on);
    legacycidInChangeTable.setEnabled(on);
    muteCommonPathPrefixes.setEnabled(on);
    signedOffBy.setEnabled(on);
    publishCommentsOnPush.setEnabled(on);
    reviewCategoryStrategy.setEnabled(on);
    diffView.setEnabled(on);
    emailStrategy.setEnabled(on);
    emailFormat.setEnabled(on);
    defaultBaseForMerges.setEnabled(on);
  }

  private void display(GeneralPreferences p) {
    showSiteHeader.setValue(p.showSiteHeader());
    useFlashClipboard.setValue(p.useFlashClipboard());
    setListBox(maximumPageSize, DEFAULT_PAGESIZE, p.changesPerPage());
    setListBox(
        dateFormat,
        GeneralPreferencesInfo.DateFormat.STD, //
        p.dateFormat());
    setListBox(
        timeFormat,
        GeneralPreferencesInfo.TimeFormat.HHMM_12, //
        p.timeFormat());
    highlightAssigneeInChangeTable.setValue(p.highlightAssigneeInChangeTable());
    relativeDateInChangeTable.setValue(p.relativeDateInChangeTable());
    sizeBarInChangeTable.setValue(p.sizeBarInChangeTable());
    legacycidInChangeTable.setValue(p.legacycidInChangeTable());
    muteCommonPathPrefixes.setValue(p.muteCommonPathPrefixes());
    signedOffBy.setValue(p.signedOffBy());
    publishCommentsOnPush.setValue(p.publishCommentsOnPush());
    setListBox(
        reviewCategoryStrategy,
        GeneralPreferencesInfo.ReviewCategoryStrategy.NONE,
        p.reviewCategoryStrategy());
    setListBox(diffView, GeneralPreferencesInfo.DiffView.SIDE_BY_SIDE, p.diffView());
    setListBox(emailStrategy, GeneralPreferencesInfo.EmailStrategy.ENABLED, p.emailStrategy());
    setListBox(emailFormat, GeneralPreferencesInfo.EmailFormat.HTML_PLAINTEXT, p.emailFormat());
    setListBox(
        defaultBaseForMerges,
        GeneralPreferencesInfo.DefaultBase.FIRST_PARENT,
        p.defaultBaseForMerges());
    display(p.my());
  }

  private void display(JsArray<TopMenuItem> items) {
    List<List<String>> values = new ArrayList<>();
    for (TopMenuItem item : Natives.asList(items)) {
      values.add(Arrays.asList(item.getName(), item.getUrl()));
    }
    myMenus.display(values);
  }

  private void setListBox(ListBox f, int defaultValue, int currentValue) {
    setListBox(f, String.valueOf(defaultValue), String.valueOf(currentValue));
  }

  private <T extends Enum<?>> void setListBox(final ListBox f, T defaultValue, T currentValue) {
    setListBox(
        f,
        defaultValue != null ? defaultValue.name() : "",
        currentValue != null ? currentValue.name() : "");
  }

  private void setListBox(ListBox f, String defaultValue, String currentValue) {
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

  private int getListBox(ListBox f, int defaultValue) {
    final int idx = f.getSelectedIndex();
    if (0 <= idx) {
      return Short.parseShort(f.getValue(idx));
    }
    return defaultValue;
  }

  private <T extends Enum<?>> T getListBox(ListBox f, T defaultValue, T[] all) {
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
    GeneralPreferences p = GeneralPreferences.create();
    p.showSiteHeader(showSiteHeader.getValue());
    p.useFlashClipboard(useFlashClipboard.getValue());
    p.changesPerPage(getListBox(maximumPageSize, DEFAULT_PAGESIZE));
    p.dateFormat(
        getListBox(
            dateFormat,
            GeneralPreferencesInfo.DateFormat.STD,
            GeneralPreferencesInfo.DateFormat.values()));
    p.timeFormat(
        getListBox(
            timeFormat,
            GeneralPreferencesInfo.TimeFormat.HHMM_12,
            GeneralPreferencesInfo.TimeFormat.values()));
    p.highlightAssigneeInChangeTable(highlightAssigneeInChangeTable.getValue());
    p.relativeDateInChangeTable(relativeDateInChangeTable.getValue());
    p.sizeBarInChangeTable(sizeBarInChangeTable.getValue());
    p.legacycidInChangeTable(legacycidInChangeTable.getValue());
    p.muteCommonPathPrefixes(muteCommonPathPrefixes.getValue());
    p.signedOffBy(signedOffBy.getValue());
    p.publishCommentsOnPush(publishCommentsOnPush.getValue());
    p.reviewCategoryStrategy(
        getListBox(
            reviewCategoryStrategy, ReviewCategoryStrategy.NONE, ReviewCategoryStrategy.values()));
    p.diffView(
        getListBox(
            diffView,
            GeneralPreferencesInfo.DiffView.SIDE_BY_SIDE,
            GeneralPreferencesInfo.DiffView.values()));

    p.emailStrategy(
        getListBox(
            emailStrategy,
            GeneralPreferencesInfo.EmailStrategy.ENABLED,
            GeneralPreferencesInfo.EmailStrategy.values()));

    p.emailFormat(
        getListBox(
            emailFormat,
            GeneralPreferencesInfo.EmailFormat.HTML_PLAINTEXT,
            GeneralPreferencesInfo.EmailFormat.values()));

    p.defaultBaseForMerges(
        getListBox(
            defaultBaseForMerges,
            GeneralPreferencesInfo.DefaultBase.FIRST_PARENT,
            GeneralPreferencesInfo.DefaultBase.values()));

    List<TopMenuItem> items = new ArrayList<>();
    for (List<String> v : myMenus.getValues()) {
      items.add(TopMenuItem.create(v.get(0), v.get(1)));
    }
    p.setMyMenus(items);

    enable(false);
    save.setEnabled(false);

    AccountApi.self()
        .view("preferences")
        .put(
            p,
            new GerritCallback<GeneralPreferences>() {
              @Override
              public void onSuccess(GeneralPreferences prefs) {
                Gerrit.setUserPreferences(prefs);
                enable(true);
                display(prefs);
              }

              @Override
              public void onFailure(Throwable caught) {
                enable(true);
                save.setEnabled(true);
                super.onFailure(caught);
              }
            });
  }

  private class MyMenuPanel extends StringListPanel {
    MyMenuPanel(Button save) {
      super(Util.C.myMenu(), Arrays.asList(Util.C.myMenuName(), Util.C.myMenuUrl()), save, false);

      setInfo(Util.C.myMenuInfo());

      Button resetButton = new Button(Util.C.myMenuReset());
      resetButton.addClickHandler(
          new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
              ConfigServerApi.defaultPreferences(
                  new GerritCallback<GeneralPreferences>() {
                    @Override
                    public void onSuccess(GeneralPreferences p) {
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
