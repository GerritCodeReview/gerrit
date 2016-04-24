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
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailTypes;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.ReviewCategoryStrategy;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
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
  private CheckBox relativeDateInChangeTable;
  private CheckBox sizeBarInChangeTable;
  private CheckBox legacycidInChangeTable;
  private CheckBox muteCommonPathPrefixes;
  private CheckBox signedOffBy;
  // Email types
  private CheckBox newChange;
  private CheckBox newPatchset;
  private CheckBox changeComment;
  private CheckBox addReviewer;
  private CheckBox deleteVote;
  private CheckBox changeMerged;
  private CheckBox mergeFailed;
  private CheckBox changeAbandoned;
  private CheckBox changeRestored;
  private CheckBox changeReverted;
  private CheckBox ccOnOwnComments;
  private CheckBox trivialRebase;

  private ListBox maximumPageSize;
  private ListBox dateFormat;
  private ListBox timeFormat;
  private ListBox reviewCategoryStrategy;
  private ListBox diffView;
  private StringListPanel myMenus;
  private Button save;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    showSiteHeader = new CheckBox(Util.C.showSiteHeader());
    useFlashClipboard = new CheckBox(Util.C.useFlashClipboard());
    maximumPageSize = new ListBox();
    for (final int v : PAGESIZE_CHOICES) {
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

    diffView = new ListBox();
    diffView.addItem(
        com.google.gerrit.client.changes.Util.C.sideBySide(),
        GeneralPreferencesInfo.DiffView.SIDE_BY_SIDE.name());
    diffView.addItem(
        com.google.gerrit.client.changes.Util.C.unifiedDiff(),
        GeneralPreferencesInfo.DiffView.UNIFIED_DIFF.name());

    Date now = new Date();
    dateFormat = new ListBox();
    for (GeneralPreferencesInfo.DateFormat fmt
        : GeneralPreferencesInfo.DateFormat.values()) {
      StringBuilder r = new StringBuilder();
      r.append(DateTimeFormat.getFormat(fmt.getShortFormat()).format(now));
      r.append(" ; ");
      r.append(DateTimeFormat.getFormat(fmt.getLongFormat()).format(now));
      dateFormat.addItem(r.toString(), fmt.name());
    }

    timeFormat = new ListBox();
    for (GeneralPreferencesInfo.TimeFormat fmt
        : GeneralPreferencesInfo.TimeFormat.values()) {
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

    relativeDateInChangeTable = new CheckBox(Util.C.showRelativeDateInChangeTable());
    sizeBarInChangeTable = new CheckBox(Util.C.showSizeBarInChangeTable());
    legacycidInChangeTable = new CheckBox(Util.C.showLegacycidInChangeTable());
    muteCommonPathPrefixes = new CheckBox(Util.C.muteCommonPathPrefixes());
    signedOffBy = new CheckBox(Util.C.signedOffBy());
    newChange = new CheckBox(Util.C.emailNewChange());
    newPatchset = new CheckBox(Util.C.emailNewPatchset());
    changeComment = new CheckBox(Util.C.emailChangeComment());
    addReviewer = new CheckBox(Util.C.emailAddReviewer());
    deleteVote = new CheckBox(Util.C.emailDeleteVote());
    changeMerged = new CheckBox(Util.C.emailChangeMerged());
    mergeFailed = new CheckBox(Util.C.emailMergeFailed());
    changeAbandoned = new CheckBox(Util.C.emailChangeAbandoned());
    changeRestored = new CheckBox(Util.C.emailChangeRestored());
    changeReverted = new CheckBox(Util.C.emailChangeReverted());
    ccOnOwnComments = new CheckBox(Util.C.emailCcOnOwnComments());
    trivialRebase = new CheckBox(Util.C.emailTrivialRebase());

    boolean flashClippy = !UserAgent.hasJavaScriptClipboard() && UserAgent.Flash.isInstalled();
    final Grid formGrid = new Grid(22 + (flashClippy ? 1 : 0), 2);

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

    // Email Settings
    formGrid.setText(row, labelIdx, Util.C.emailFieldLabel());
    formGrid.setWidget(row, fieldIdx, newChange);
    row++;
    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, newPatchset);
    row++;
    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, changeComment);
    row++;
    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, addReviewer);
    row++;
    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, deleteVote);
    row++;
    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, changeMerged);
    row++;
    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, mergeFailed);
    row++;
    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, changeAbandoned);
    row++;
    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, changeRestored);
    row++;
    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, changeReverted);
    row++;
    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, ccOnOwnComments);
    row++;
    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, trivialRebase);
    row++;

    formGrid.setText(row, labelIdx, Util.C.diffViewLabel());
    formGrid.setWidget(row, fieldIdx, diffView);
    row++;

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, showSiteHeader);
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

    if (flashClippy) {
      formGrid.setText(row, labelIdx, "");
      formGrid.setWidget(row, fieldIdx, useFlashClipboard);
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

    myMenus = new MyMenuPanel(save);
    add(myMenus);

    add(save);

    final OnEditEnabler e = new OnEditEnabler(save);
    e.listenTo(showSiteHeader);
    e.listenTo(useFlashClipboard);
    e.listenTo(maximumPageSize);
    e.listenTo(dateFormat);
    e.listenTo(timeFormat);
    e.listenTo(relativeDateInChangeTable);
    e.listenTo(sizeBarInChangeTable);
    e.listenTo(legacycidInChangeTable);
    e.listenTo(muteCommonPathPrefixes);
    e.listenTo(signedOffBy);
    e.listenTo(diffView);
    e.listenTo(reviewCategoryStrategy);

    e.listenTo(newChange);
    e.listenTo(newPatchset);
    e.listenTo(changeComment);
    e.listenTo(addReviewer);
    e.listenTo(deleteVote);
    e.listenTo(changeMerged);
    e.listenTo(mergeFailed);
    e.listenTo(changeAbandoned);
    e.listenTo(changeRestored);
    e.listenTo(changeReverted);
    e.listenTo(ccOnOwnComments);
    e.listenTo(trivialRebase);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    ExtensionPanel extensionPanel =
        createExtensionPoint(GerritUiExtensionPoint.PREFERENCES_SCREEN_BOTTOM);
    extensionPanel.addStyleName(Gerrit.RESOURCES.css().extensionPanel());
    add(extensionPanel);

    AccountApi.self().view("preferences")
        .get(new ScreenLoadCallback<GeneralPreferences>(this) {
      @Override
      public void preDisplay(GeneralPreferences prefs) {
        display(prefs);
      }
    });
  }

  private void enable(final boolean on) {
    showSiteHeader.setEnabled(on);
    useFlashClipboard.setEnabled(on);
    maximumPageSize.setEnabled(on);
    dateFormat.setEnabled(on);
    timeFormat.setEnabled(on);
    relativeDateInChangeTable.setEnabled(on);
    sizeBarInChangeTable.setEnabled(on);
    legacycidInChangeTable.setEnabled(on);
    muteCommonPathPrefixes.setEnabled(on);
    signedOffBy.setEnabled(on);
    reviewCategoryStrategy.setEnabled(on);
    diffView.setEnabled(on);
    newChange.setEnabled(on);
    newPatchset.setEnabled(on);
    changeComment.setEnabled(on);
    addReviewer.setEnabled(on);
    deleteVote.setEnabled(on);
    changeMerged.setEnabled(on);
    mergeFailed.setEnabled(on);
    changeAbandoned.setEnabled(on);
    changeRestored.setEnabled(on);
    changeReverted.setEnabled(on);
    ccOnOwnComments.setEnabled(on);
    trivialRebase.setEnabled(on);
  }

  private void display(GeneralPreferences p) {
    showSiteHeader.setValue(p.showSiteHeader());
    useFlashClipboard.setValue(p.useFlashClipboard());
    setListBox(maximumPageSize, DEFAULT_PAGESIZE, p.changesPerPage());
    setListBox(dateFormat, GeneralPreferencesInfo.DateFormat.STD, //
        p.dateFormat());
    setListBox(timeFormat, GeneralPreferencesInfo.TimeFormat.HHMM_12, //
        p.timeFormat());
    relativeDateInChangeTable.setValue(p.relativeDateInChangeTable());
    sizeBarInChangeTable.setValue(p.sizeBarInChangeTable());
    legacycidInChangeTable.setValue(p.legacycidInChangeTable());
    muteCommonPathPrefixes.setValue(p.muteCommonPathPrefixes());
    signedOffBy.setValue(p.signedOffBy());
    setListBox(reviewCategoryStrategy,
        GeneralPreferencesInfo.ReviewCategoryStrategy.NONE,
        p.reviewCategoryStrategy());
    setListBox(diffView,
        GeneralPreferencesInfo.DiffView.SIDE_BY_SIDE,
        p.diffView());
    newChange.setValue(false);
    newPatchset.setValue(false);
    changeComment.setValue(false);
    addReviewer.setValue(false);
    deleteVote.setValue(false);
    changeMerged.setValue(false);
    mergeFailed.setValue(false);
    changeAbandoned.setValue(false);
    changeRestored.setValue(false);
    changeReverted.setValue(false);
    ccOnOwnComments.setValue(false);
    trivialRebase.setValue(false);
    JsArrayString types = p.emailTypes();
    for (int i = 0; i < types.length(); i++) {
      String type = types.get(i);
      if (type == EmailTypes.NEW_CHANGE.name()) {
        newChange.setValue(true);
      } else if (type == EmailTypes.NEW_PATCHSET.name()) {
        newPatchset.setValue(true);
      } else if (type == EmailTypes.CHANGE_COMMENT.name()) {
        changeComment.setValue(true);
      } else if (type == EmailTypes.ADD_REVIEWER.name()) {
        addReviewer.setValue(true);
      } else if (type == EmailTypes.DELETE_VOTE.name()) {
        deleteVote.setValue(true);
      } else if (type == EmailTypes.CHANGE_MERGED.name()) {
        changeMerged.setValue(true);
      } else if (type == EmailTypes.MERGE_FAILED.name()) {
        mergeFailed.setValue(true);
      } else if (type == EmailTypes.CHANGE_ABANDONED.name()) {
        changeAbandoned.setValue(true);
      } else if (type == EmailTypes.CHANGE_RESTORED.name()) {
        changeRestored.setValue(true);
      } else if (type == EmailTypes.CHANGE_REVERTED.name()) {
        changeReverted.setValue(true);
      } else if (type == EmailTypes.CC_ON_OWN_COMMENTS.name()) {
        ccOnOwnComments.setValue(true);
      } else if (type == EmailTypes.TRIVIAL_REBASE.name()) {
        trivialRebase.setValue(true);
      }
    }

    display(p.my());
  }

  private void display(JsArray<TopMenuItem> items) {
    List<List<String>> values = new ArrayList<>();
    for (TopMenuItem item : Natives.asList(items)) {
      values.add(Arrays.asList(item.getName(), item.getUrl()));
    }
    myMenus.display(values);
  }

  private void setListBox(final ListBox f, final int defaultValue,
      final int currentValue) {
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

  private int getListBox(final ListBox f, final int defaultValue) {
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
    GeneralPreferences p = GeneralPreferences.create();
    p.showSiteHeader(showSiteHeader.getValue());
    p.useFlashClipboard(useFlashClipboard.getValue());
    p.changesPerPage(getListBox(maximumPageSize, DEFAULT_PAGESIZE));
    p.dateFormat(getListBox(dateFormat,
        GeneralPreferencesInfo.DateFormat.STD,
        GeneralPreferencesInfo.DateFormat.values()));
    p.timeFormat(getListBox(timeFormat,
        GeneralPreferencesInfo.TimeFormat.HHMM_12,
        GeneralPreferencesInfo.TimeFormat.values()));
    p.relativeDateInChangeTable(relativeDateInChangeTable.getValue());
    p.sizeBarInChangeTable(sizeBarInChangeTable.getValue());
    p.legacycidInChangeTable(legacycidInChangeTable.getValue());
    p.muteCommonPathPrefixes(muteCommonPathPrefixes.getValue());
    p.signedOffBy(signedOffBy.getValue());
    p.reviewCategoryStrategy(getListBox(reviewCategoryStrategy,
        ReviewCategoryStrategy.NONE,
        ReviewCategoryStrategy.values()));
    p.diffView(getListBox(diffView,
        GeneralPreferencesInfo.DiffView.SIDE_BY_SIDE,
        GeneralPreferencesInfo.DiffView.values()));

    ArrayList<EmailTypes> types = new ArrayList<>();
    if (newChange.getValue()) {
      types.add(EmailTypes.NEW_CHANGE);
    }
    if (newPatchset.getValue()) {
      types.add(EmailTypes.NEW_PATCHSET);
    }
    if (changeComment.getValue()) {
      types.add(EmailTypes.CHANGE_COMMENT);
    }
    if (addReviewer.getValue()) {
      types.add(EmailTypes.ADD_REVIEWER);
    }
    if (deleteVote.getValue()) {
      types.add(EmailTypes.DELETE_VOTE);
    }
    if (changeMerged.getValue()) {
      types.add(EmailTypes.CHANGE_MERGED);
    }
    if (mergeFailed.getValue()) {
      types.add(EmailTypes.MERGE_FAILED);
    }
    if (changeAbandoned.getValue()) {
      types.add(EmailTypes.CHANGE_ABANDONED);
    }
    if (changeRestored.getValue()) {
      types.add(EmailTypes.CHANGE_RESTORED);
    }
    if (changeReverted.getValue()) {
      types.add(EmailTypes.CHANGE_REVERTED);
    }
    if (ccOnOwnComments.getValue()) {
      types.add(EmailTypes.CC_ON_OWN_COMMENTS);
    }
    if (trivialRebase.getValue()) {
      types.add(EmailTypes.TRIVIAL_REBASE);
    }
    JsArrayString jsTypes = JsArrayString.createArray().cast();
    for (EmailTypes type : types) {
      jsTypes.push(type.name());
    }
    p.emailTypes(jsTypes);

    List<TopMenuItem> items = new ArrayList<>();
    for (List<String> v : myMenus.getValues()) {
      items.add(TopMenuItem.create(v.get(0), v.get(1)));
    }
    p.setMyMenus(items);

    enable(false);
    save.setEnabled(false);

    AccountApi.self().view("preferences")
        .put(p, new GerritCallback<GeneralPreferences>() {
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
      super(Util.C.myMenu(), Arrays.asList(Util.C.myMenuName(),
          Util.C.myMenuUrl()), save, false);

      setInfo(Util.C.myMenuInfo());

      Button resetButton = new Button(Util.C.myMenuReset());
      resetButton.addClickHandler(new ClickHandler() {
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
