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

import static com.google.gerrit.reviewdb.AccountGeneralPreferences.DEFAULT_PAGESIZE;
import static com.google.gerrit.reviewdb.AccountGeneralPreferences.PAGESIZE_CHOICES;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGeneralPreferences;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwtjsonrpc.client.VoidResult;

class PreferencePanel extends Composite {
  private CheckBox showSiteHeader;
  private CheckBox useFlashClipboard;
  private ListBox maximumPageSize;
  private Button save;

  PreferencePanel() {
    final FlowPanel body = new FlowPanel();

    final ClickHandler onClickSave = new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        save.setEnabled(true);
      }
    };
    final ChangeHandler onChangeSave = new ChangeHandler() {
      @Override
      public void onChange(final ChangeEvent event) {
        save.setEnabled(true);
      }
    };

    showSiteHeader = new CheckBox(Util.C.showSiteHeader());
    showSiteHeader.addClickHandler(onClickSave);

    useFlashClipboard = new CheckBox(Util.C.useFlashClipboard());
    useFlashClipboard.addClickHandler(onClickSave);

    maximumPageSize = new ListBox();
    for (final short v : PAGESIZE_CHOICES) {
      maximumPageSize.addItem(Util.M.rowsPerPage(v), String.valueOf(v));
    }
    maximumPageSize.addChangeHandler(onChangeSave);

    final int labelIdx, fieldIdx;
    if (LocaleInfo.getCurrentLocale().isRTL()) {
      labelIdx = 1;
      fieldIdx = 0;
    } else {
      labelIdx = 0;
      fieldIdx = 1;
    }
    final Grid formGrid = new Grid(4, 2);

    int row = 0;
    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, showSiteHeader);
    row++;

    formGrid.setText(row, labelIdx, "");
    formGrid.setWidget(row, fieldIdx, useFlashClipboard);
    row++;

    formGrid.setText(row, labelIdx, Util.C.maximumPageSizeFieldLabel());
    formGrid.setWidget(row, fieldIdx, maximumPageSize);
    row++;

    body.add(formGrid);

    save = new Button(Util.C.buttonSaveChanges());
    save.setEnabled(false);
    save.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doSave();
      }
    });
    body.add(save);

    initWidget(body);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.ACCOUNT_SVC.myAccount(new GerritCallback<Account>() {
      public void onSuccess(final Account result) {
        display(result.getGeneralPreferences());
        enable(true);
      }
    });
  }

  private void enable(final boolean on) {
    showSiteHeader.setEnabled(on);
    useFlashClipboard.setEnabled(on);
    maximumPageSize.setEnabled(on);
  }

  private void display(final AccountGeneralPreferences p) {
    showSiteHeader.setValue(p.isShowSiteHeader());
    useFlashClipboard.setValue(p.isUseFlashClipboard());
    setListBox(maximumPageSize, DEFAULT_PAGESIZE, p.getMaximumPageSize());
  }

  private void setListBox(final ListBox f, final short defaultValue,
      final short currentValue) {
    final int n = f.getItemCount();
    for (int i = 0; i < n; i++) {
      if (Short.parseShort(f.getValue(i)) == currentValue) {
        f.setSelectedIndex(i);
        return;
      }
    }
    if (currentValue != defaultValue) {
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

  private void doSave() {
    final AccountGeneralPreferences p = new AccountGeneralPreferences();
    p.setShowSiteHeader(showSiteHeader.getValue());
    p.setUseFlashClipboard(useFlashClipboard.getValue());
    p.setMaximumPageSize(getListBox(maximumPageSize, DEFAULT_PAGESIZE));

    enable(false);
    save.setEnabled(false);

    Util.ACCOUNT_SVC.changePreferences(p, new GerritCallback<VoidResult>() {
      @Override
      public void onSuccess(final VoidResult result) {
        Gerrit.getUserAccount().setGeneralPreferences(p);
        Gerrit.applyUserPreferences();
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
}
