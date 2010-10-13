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
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gerrit.client.ui.RPCSuggestOracle;
import com.google.gerrit.common.data.AccountProjectWatchInfo;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;

import java.util.List;

public class MyWatchedProjectsScreen extends SettingsScreen {
  private Button addNew;
  private HintTextBox nameBox;
  private SuggestBox nameTxt;
  private HintTextBox filterTxt;
  private MyWatchesTable watchesTab;
  private Button delSel;
  private boolean submitOnSelection;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    createWidgets();

    final Grid grid = new Grid(2, 2);
    grid.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    grid.setText(0, 0, Util.C.watchedProjectName());
    grid.setWidget(0, 1, nameTxt);

    grid.setText(1, 0, Util.C.watchedProjectFilter());
    grid.setWidget(1, 1, filterTxt);

    final CellFormatter fmt = grid.getCellFormatter();
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().header());
    fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().header());
    fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().bottomheader());

    final FlowPanel fp = new FlowPanel();
    fp.setStyleName(Gerrit.RESOURCES.css().addWatchPanel());
    fp.add(grid);
    fp.add(addNew);
    add(fp);

    add(watchesTab);
    add(delSel);
  }

  protected void createWidgets() {
    nameBox = new HintTextBox();
    nameTxt = new SuggestBox(new ProjectNameSuggestOracle(), nameBox);
    nameBox.setVisibleLength(50);
    nameBox.setHintText(Util.C.defaultProjectName());
    nameBox.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        submitOnSelection = false;

        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          if (nameTxt.isSuggestionListShowing()) {
            submitOnSelection = true;
          } else {
            doAddNew();
          }
        }
      }
    });
    nameTxt.addSelectionHandler(new SelectionHandler<Suggestion>() {
      @Override
      public void onSelection(SelectionEvent<Suggestion> event) {
        if (submitOnSelection) {
          submitOnSelection = false;
          doAddNew();
        }
      }
    });

    filterTxt = new HintTextBox();
    filterTxt.setVisibleLength(50);
    filterTxt.setHintText(Util.C.defaultFilter());
    filterTxt.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          doAddNew();
        }
      }
    });

    addNew = new Button(Util.C.buttonWatchProject());
    addNew.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doAddNew();
      }
    });

    watchesTab = new MyWatchesTable();

    delSel = new Button(Util.C.buttonDeleteSshKey());
    delSel.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        watchesTab.deleteChecked();
      }
    });
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    populateWatches();
  }

  void doAddNew() {
    final String projectName = nameTxt.getText();
    if ("".equals(projectName)) {
      return;
    }

    String filter = filterTxt.getText();
    if (filter == null || filter.isEmpty()
        || filter.equals(Util.C.defaultFilter())) {
      filter = null;
    }

    addNew.setEnabled(false);
    nameBox.setEnabled(false);
    filterTxt.setEnabled(false);

    Util.ACCOUNT_SVC.addProjectWatch(projectName, filter,
        new GerritCallback<AccountProjectWatchInfo>() {
          public void onSuccess(final AccountProjectWatchInfo result) {
            addNew.setEnabled(true);
            nameBox.setEnabled(true);
            filterTxt.setEnabled(true);

            nameTxt.setText("");
            watchesTab.insertWatch(result);
          }

          @Override
          public void onFailure(final Throwable caught) {
            addNew.setEnabled(true);
            nameBox.setEnabled(true);
            filterTxt.setEnabled(true);

            super.onFailure(caught);
          }
        });
  }

  protected void populateWatches() {
    Util.ACCOUNT_SVC.myProjectWatch(
        new ScreenLoadCallback<List<AccountProjectWatchInfo>>(this) {
      @Override
      public void preDisplay(final List<AccountProjectWatchInfo> result) {
        watchesTab.display(result);
      }
    });
  }
}
