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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGeneralPreferences;
import com.google.gerrit.client.rpc.GerritCallback;
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
  private ListBox defaultContext;
  private Button save;

  PreferencePanel() {
    final FlowPanel body = new FlowPanel();

    final ClickHandler onClickSave = new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        save.setEnabled(true);
      }
    };

    showSiteHeader = new CheckBox(Util.C.showSiteHeader());
    showSiteHeader.addClickHandler(onClickSave);

    defaultContext = new ListBox();
    for (final short v : AccountGeneralPreferences.CONTEXT_CHOICES) {
      final String label;
      if (v == AccountGeneralPreferences.WHOLE_FILE_CONTEXT) {
        label = Util.C.contextWholeFile();
      } else {
        label = Util.M.lines(v);
      }
      defaultContext.addItem(label, String.valueOf(v));
    }
    defaultContext.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(final ChangeEvent event) {
        save.setEnabled(true);
      }
    });

    final int labelIdx, fieldIdx;
    if (LocaleInfo.getCurrentLocale().isRTL()) {
      labelIdx = 1;
      fieldIdx = 0;
    } else {
      labelIdx = 0;
      fieldIdx = 1;
    }
    final Grid formGrid = new Grid(2, 2);

    formGrid.setText(0, labelIdx, "");
    formGrid.setWidget(0, fieldIdx, showSiteHeader);

    formGrid.setText(1, labelIdx, Util.C.defaultContextFieldLabel());
    formGrid.setWidget(1, fieldIdx, defaultContext);

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
  public void onLoad() {
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
    defaultContext.setEnabled(on);
  }

  private void display(final AccountGeneralPreferences p) {
    showSiteHeader.setValue(p.isShowSiteHeader());
    displayDefaultContext(p.getDefaultContext());
  }

  private void displayDefaultContext(final short lines) {
    for (int i = 0; i < AccountGeneralPreferences.CONTEXT_CHOICES.length; i++) {
      if (AccountGeneralPreferences.CONTEXT_CHOICES[i] == lines) {
        defaultContext.setSelectedIndex(i);
        return;
      }
    }
    displayDefaultContext(AccountGeneralPreferences.DEFAULT_CONTEXT);
  }

  private short getDefaultContext() {
    final int idx = defaultContext.getSelectedIndex();
    if (0 <= idx) {
      return Short.parseShort(defaultContext.getValue(idx));
    }
    return AccountGeneralPreferences.DEFAULT_CONTEXT;
  }

  private void doSave() {
    final AccountGeneralPreferences p = new AccountGeneralPreferences();
    p.setShowSiteHeader(showSiteHeader.getValue());
    p.setDefaultContext(getDefaultContext());

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
