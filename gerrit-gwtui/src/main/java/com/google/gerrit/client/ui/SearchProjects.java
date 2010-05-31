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

import com.google.gerrit.client.admin.Util;
import com.google.gerrit.common.data.ProjectRightsBased;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.OpenEvent;
import com.google.gwt.event.logical.shared.OpenHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwtexpui.globalkey.client.NpTextBox;

import java.util.ArrayList;
import java.util.List;

/** It creates UI for searching projects and/or their parent and executes search operation. **/
public class SearchProjects {

  private DisclosurePanel filterPanel;
  private NpTextBox filterProjectNameTxt;
  private NpTextBox filterParentProjectTxt;
  private Button searchButton;
  private List<ProjectRightsBased> projectsRightsBasedList;
  private SearchListener searchListener;

  /**
   * Constructor - Creates and set UI elements.
   */
  public SearchProjects() {
    createFilterProjectUI();
  }

  private void createFilterProjectUI() {
    filterPanel = new DisclosurePanel(Util.C.headingFilterProject());
    filterPanel.addOpenHandler(new OpenHandler<DisclosurePanel>() {
      public void onOpen(OpenEvent<DisclosurePanel> event) {
        if (searchListener != null) {
          searchListener.onClick();
        }
      }
    });

    filterPanel.addCloseHandler(new CloseHandler<DisclosurePanel>() {
      public void onClose(CloseEvent<DisclosurePanel> event) {
        if (searchListener != null) {
          searchListener.onClick();
        }
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
    filterParentProjectTxt.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          doSearch();
        }
      }
    });

    filterGrid.setText(1, 0, Util.C.columnParentProject() + ":");
    filterGrid.setWidget(1, 1, filterParentProjectTxt);

    searchButton = new Button(Util.C.buttonSearchProject());
    searchButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doSearch();
      }
    });

    filterGrid.setWidget(2, 0, searchButton);

    filterPanel.setContent(filterGrid);
    filterPanel.setWidth("40%");
  }

  public void setProjectsRightsBasedList(
      List<ProjectRightsBased> projectsRightsBasedList) {
    this.projectsRightsBasedList = projectsRightsBasedList;
  }

  public void setSearchListener(SearchListener searchListener) {
    this.searchListener = searchListener;
  }

  public DisclosurePanel getFilterPanel() {
    return filterPanel;
  }

  private void doSearch() {
    List<ProjectRightsBased> filterProjectsList = new ArrayList<ProjectRightsBased>();
    boolean searchAll = false;

    if ((!"".equals(filterParentProjectTxt.getText().trim()))
        || (!"".equals(filterProjectNameTxt.getText().trim()))) {
      for (final ProjectRightsBased k : projectsRightsBasedList) {

        if (!"".equals(filterProjectNameTxt.getText())) {

          if (!"".equals(filterParentProjectTxt.getText())) {
            if ((k.getProject().getName().regionMatches(true, 0, filterProjectNameTxt
                .getText(), 0, filterProjectNameTxt.getText().length()))
                && (k.getProject().getParent().get().regionMatches(true, 0,
                    filterParentProjectTxt.getText(), 0, filterParentProjectTxt
                    .getText().length()))) {
              filterProjectsList.add(k);
            }
          } else {
            if (k.getProject().getName().regionMatches(true, 0,
                filterProjectNameTxt.getText(), 0,
                filterProjectNameTxt.getText().length())) {
              filterProjectsList.add(k);
            }
          }
        } else {
          if (!"".equals(filterParentProjectTxt.getText())) {

            if ((k.getProject().getParent() != null)
                && (k.getProject().getParent().get().regionMatches(true, 0,
                    filterParentProjectTxt.getText(), 0, filterParentProjectTxt
                    .getText().length()))) {
              filterProjectsList.add(k);
            }
          }
        }
      }
    } else {
      filterProjectsList = projectsRightsBasedList;
      searchAll = true;
    }

    if (searchListener != null) {
      searchListener.onSearch(filterProjectsList, searchAll);
    }
  }

  /** Listener to search operation. */
  public static interface SearchListener {
    public void onSearch(List<ProjectRightsBased> filterProjectsList, boolean searchAll);
    public void onClick();
  }
}
