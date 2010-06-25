// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.admin.SearchEvent;
import com.google.gerrit.client.admin.SearchHandler;
import com.google.gerrit.client.ui.Util;
import com.google.gerrit.common.data.ProjectData;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.OpenEvent;
import com.google.gwt.event.logical.shared.OpenHandler;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwtexpui.globalkey.client.NpTextBox;

import java.util.ArrayList;
import java.util.List;

/**
 * It creates UI for searching projects and/or their parent and executes search
 * operation.
 */
public class SearchProjects {
  final private HandlerManager handlerManager = new HandlerManager(this);

  private DisclosurePanel filterPanel;
  private NpTextBox filterProjectNameTxt;
  private NpTextBox filterParentProjectTxt;
  private SuggestBox parentNameTxt;
  private Button filterButton;
  private List<ProjectData> projectDataList;

  /**
   * Constructor - Creates and set UI elements.
   */
  public SearchProjects() {
    createFilterProjectUI();
  }

  private void createFilterProjectUI() {
    filterPanel = new DisclosurePanel(Util.C.headingSearchProject());
    filterPanel.addOpenHandler(new OpenHandler<DisclosurePanel>() {
      public void onOpen(OpenEvent<DisclosurePanel> event) {
        handlerManager.fireEvent(new ClickSearchEvent());
      }
    });

    filterPanel.addCloseHandler(new CloseHandler<DisclosurePanel>() {
      public void onClose(CloseEvent<DisclosurePanel> event) {
        handlerManager.fireEvent(new ClickSearchEvent());
      }
    });

    final Grid filterGrid = new Grid(3, 2);

    filterProjectNameTxt = new NpTextBox();
    filterProjectNameTxt.setVisibleLength(50);
    filterProjectNameTxt.setText("");
    filterProjectNameTxt.addKeyPressHandler(new KeyPressHandler() {
      public void onKeyPress(KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          doSearch();
        }
      }
    });

    filterGrid.setText(0, 0, Util.C.columnFilterProjectName() + ":");
    filterGrid.setWidget(0, 1, filterProjectNameTxt);

    filterParentProjectTxt = new NpTextBox();
    filterParentProjectTxt.setVisibleLength(50);
    filterParentProjectTxt.setText("");

    parentNameTxt =
        new SuggestBox(new ProjectNameSuggestOracle(), filterParentProjectTxt);

    filterParentProjectTxt.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          if (!parentNameTxt.isSuggestionListShowing()) {
            doSearch();
          }
        }
      }
    });

    filterGrid.setText(1, 0, Util.C.columnParentProject() + ":");
    filterGrid.setWidget(1, 1, parentNameTxt);

    filterButton = new Button(Util.C.buttonHighlightProject());
    filterButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doSearch();
      }
    });

    filterGrid.setWidget(2, 0, filterButton);

    filterPanel.setContent(filterGrid);
    filterPanel.setWidth("40%");
  }

  public void setProjectDataList(List<ProjectData> projectsDataList) {
    this.projectDataList = projectsDataList;
  }

  public DisclosurePanel getFilterPanel() {
    return filterPanel;
  }

  private void doSearch() {
    List<ProjectData> filterProjectsList = new ArrayList<ProjectData>();
    boolean searchAll = false;

    if ((!"".equals(filterParentProjectTxt.getText().trim()))
        || (!"".equals(filterProjectNameTxt.getText().trim()))) {
      for (final ProjectData k : projectDataList) {

        if (!"".equals(filterProjectNameTxt.getText())) {

          if (!"".equals(filterParentProjectTxt.getText())) {
            if ((k.getName().regionMatches(true, 0, filterProjectNameTxt
                .getText(), 0, filterProjectNameTxt.getText().length()))
                && k.getParentNameKey() != null
                && (k.getParentNameKey().get().regionMatches(true, 0,
                    filterParentProjectTxt.getText(), 0, filterParentProjectTxt
                        .getText().length()))) {
              filterProjectsList.add(k);
            }
          } else {
            if (k.getName().regionMatches(true, 0,
                filterProjectNameTxt.getText(), 0,
                filterProjectNameTxt.getText().length())) {
              filterProjectsList.add(k);
            }
          }
        } else {
          if (!"".equals(filterParentProjectTxt.getText())) {

            if (filterParentProjectTxt.getText().equals(
                Gerrit.getConfig().getWildProject().get())) {
              if (k.getParentNameKey() == null) {
                filterProjectsList.add(k);
              }
            } else if ((k.getParentNameKey() != null)
                && (k.getParentNameKey().get().regionMatches(true, 0,
                    filterParentProjectTxt.getText(), 0, filterParentProjectTxt
                        .getText().length()))) {
              filterProjectsList.add(k);
            }
          }
        }
      }
    } else {
      filterProjectsList = projectDataList;
      searchAll = true;
    }

    handlerManager.fireEvent(new SearchEvent(filterProjectsList, searchAll));
  }

  public void changeButtonCaption(boolean isProjectsView) {
    if (isProjectsView) {
      filterButton.setText(Util.C.buttonFilterProject());
    } else {
      filterButton.setText(Util.C.buttonHighlightProject());
    }
  }

  public void addSearchHandler(SearchHandler handler) {
    handlerManager.addHandler(SearchEvent.getType(), handler);
    handlerManager.addHandler(ClickSearchEvent.getType(), handler);
  }
}
