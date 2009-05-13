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
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwtjsonrpc.client.VoidResult;

class PreferencePanel extends Composite {
  private CheckBox showSiteHeader;
  private ListBox defaultContext;
  private short oldDefaultContext;
  private ProjectWatchPanel watchPanel;

  PreferencePanel() {
    final FlowPanel body = new FlowPanel();

    showSiteHeader = new CheckBox(Util.C.showSiteHeader());
    showSiteHeader.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        final boolean val = showSiteHeader.getValue();
        Util.ACCOUNT_SVC.changeShowSiteHeader(val,
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                Gerrit.getUserAccount().setShowSiteHeader(val);
                Gerrit.refreshMenuBar();
              }

              @Override
              public void onFailure(final Throwable caught) {
                showSiteHeader.setValue(!val);
                super.onFailure(caught);
              }
            });
      }
    });

    defaultContext = new ListBox();
    for (final short v : Account.CONTEXT_CHOICES) {
      final String label;
      if (v == Account.WHOLE_FILE_CONTEXT) {
        label = Util.C.contextWholeFile();
      } else {
        label = Util.M.lines(v);
      }
      defaultContext.addItem(label, String.valueOf(v));
    }
    defaultContext.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(final ChangeEvent event) {
        final int idx = defaultContext.getSelectedIndex();
        if (idx < 0) {
          return;
        }

        final short newLines = Short.parseShort(defaultContext.getValue(idx));
        if (newLines == oldDefaultContext) {
          return;
        }

        Util.ACCOUNT_SVC.changeDefaultContext(newLines,
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                oldDefaultContext = newLines;
                Gerrit.getUserAccount().setDefaultContext(newLines);
              }

              @Override
              public void onFailure(final Throwable caught) {
                setDefaultContext(oldDefaultContext);
                super.onFailure(caught);
              }
            });
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

    formGrid.setText(1, labelIdx, Util.C.defaultContext());
    formGrid.setWidget(1, fieldIdx, defaultContext);

    body.add(formGrid);

    {
      final FlowPanel fp = new FlowPanel();
      fp.setStyleName("gerrit-WatchedProjectPanel");
      fp.add(new SmallHeading(Util.C.watchedProjects()));

      watchPanel = new ProjectWatchPanel();
      fp.add(watchPanel);
      body.add(fp);
    }

    initWidget(body);
  }

  void display(final Account account) {
    showSiteHeader.setValue(account.isShowSiteHeader());
    setDefaultContext(account.getDefaultContext());
  }

  private void setDefaultContext(final short lines) {
    for (int i = 0; i < Account.CONTEXT_CHOICES.length; i++) {
      if (Account.CONTEXT_CHOICES[i] == lines) {
        oldDefaultContext = lines;
        defaultContext.setSelectedIndex(i);
        return;
      }
    }
    setDefaultContext(Account.DEFAULT_CONTEXT);
  }
}
