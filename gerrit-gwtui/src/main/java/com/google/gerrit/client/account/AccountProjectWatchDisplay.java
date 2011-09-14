// Copyright (C) 2012 The Android Open Source Project
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
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gerrit.common.data.AccountProjectWatchInfo;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestBox.DefaultSuggestionDisplay;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;

class AccountProjectWatchDisplay extends Composite {
  private AccountProjectWatchInfo info;
  private FlowPanel displayPanel;
  private ProjectWatchKeyEditor editPanel;

  AccountProjectWatchDisplay(final AccountProjectWatchInfo info) {
    this.info = info;
    final FlowPanel fp = new FlowPanel();
    displayPanel = new FlowPanel();

    final Image editIcon = new Image(Gerrit.RESOURCES.editIcon());
    editIcon.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        if(editPanel == null) {
          editPanel = new ProjectWatchKeyEditor(info.getWatch().getKey(), false, true) {
            public void onCancel() {
              display();
              setVisible(false);
            }
            public void onUpdate(AccountProjectWatchInfo info) {
              setWatch(info);
            }
          };
          fp.add(editPanel);
        }
        editPanel.setVisible(true);
      }
    });

    Grid grid = new Grid(1, 2);
    grid.setWidget(0, 0, displayPanel);
    grid.setWidget(0, 1, editIcon);

    fp.add(grid);
    initWidget(fp);
    display();
  }

  private void setWatch(AccountProjectWatchInfo info) {
    this.info = info;
    display();
    editPanel.setVisible(false);
  }

  private void display() {
    displayPanel.clear();
    AccountProjectWatch watch = info.getWatch();
    displayPanel.add(new ProjectLink(watch.getKey().getProjectName(), Status.NEW));
    if (watch.getFilter() != null) {
      Label filter = new Label(watch.getFilter());
      filter.setStyleName(Gerrit.RESOURCES.css().watchedProjectFilter());
      displayPanel.add(filter);
    }
  }
}
