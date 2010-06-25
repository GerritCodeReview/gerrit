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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.ClickSearchEvent;
import com.google.gerrit.client.ui.ParentProjectsSetting;
import com.google.gerrit.client.ui.ProjectsSelectionTable;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.client.ui.SearchProjects;
import com.google.gerrit.client.ui.SelectedItemsTable;
import com.google.gerrit.client.ui.ProjectsTreeContents;
import com.google.gerrit.client.ui.ProjectsTreeContents.TreeNode;
import com.google.gerrit.client.ui.ProjectsTreeContents.TreeNodeWidget;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.ProjectData;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Tree;
import com.google.gwt.user.client.ui.TreeItem;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gerrit.client.ui.Util;
import com.google.gerrit.client.admin.ProjectScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class ProjectListScreen extends Screen {
  private ProjectsSelectionTable projects;
  private SearchProjects searchProjects;
  private SelectedItemsTable selectedProjectsTable;
  private SimplePanel onSuccessPanel;
  private Label onSuccessLabel;
  private boolean treeViewFlag;
  private Button viewButton;
  private Grid gridView;
  private VerticalPanel vp = new VerticalPanel();

  private final List<TreeNode> highlightedNodes = new ArrayList<TreeNode>();

  private TreeMap<Integer, TreeNode> treeNodesMap;
  private TreeMap<String, Integer> projectNodesMap;
  private ProjectsTreeContents treeContents;
  private VerticalPanel treePanel;
  private Tree projectsTree;
  private TreeItem rootWildProject;
  private CheckBox cbTreeSelectAll;
  private ParentProjectsSetting setParentProjects;
  private Button setParentButton;

  @Override
  protected void onLoad() {
    super.onLoad();
    loadProjects();
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.projectListTitle());

    /* Set page style */
    addStyleName(Gerrit.RESOURCES.css().filterProjectList());

    /* Add onSuccessPanel */
    onSuccessLabel = new Label(Util.C.labelOnSuccessSetParent() + ".");
    onSuccessPanel = new SimplePanel();
    onSuccessPanel.setStyleName(Gerrit.RESOURCES.css().onSuccessPanel());
    onSuccessPanel.add(onSuccessLabel);
    onSuccessPanel.setVisible(false);

    add(onSuccessPanel);

    /* Add filter panel and its contents */
    searchProjects = new SearchProjects();

    searchProjects.addSearchHandler(new SearchHandler() {
      @Override
      public void onSearch(SearchEvent searchEvent) {
        final List<ProjectData> filterProjectsList = searchEvent.getFilteredProjects();
        final boolean searchAll = searchEvent.isSearchAll();

        hideOnSuccessPanel();
        reloadProjectContent(true);

        if (!highlightedNodes.isEmpty()) {
          for (TreeNode highlightedNode : highlightedNodes) {
            final Widget widget = highlightedNode.getTreeItem().getWidget();
            if (widget instanceof TreeNodeWidget) {
              ((TreeNodeWidget) widget).removeNodeHighlight();
            }
          }
          highlightedNodes.clear();
        }

        final List<String> selectedProjectsList =
            selectedProjectsTable.getInsertedRows();

        if (searchAll) {
          projectsTree.removeItems();
          buildProjectsTree(filterProjectsList);
        } else {

          for (final ProjectData k : filterProjectsList) {
            if (treeNodesMap != null) {
              final TreeNode treeNode = treeNodesMap.get(k.getId());
              if (treeNode != null) {
                final Widget widget = treeNode.getTreeItem().getWidget();
                if (widget instanceof TreeNodeWidget) {
                  ((TreeNodeWidget) widget).highlightNode();
                  highlightedNodes.add(treeNode);
                  if (treeNode.getParentName() != null) {
                    final TreeNode parentNode =
                        treeNodesMap.get(treeNode.getParentId());
                    expandTreeNode(parentNode);
                  }
                }
              }
            }
          }
          projectsTree.getItem(0).setState(true);
        }

        projects.display(filterProjectsList);
        projects.finishDisplay();

        for (ProjectData filteredProjects : filterProjectsList) {
          if (selectedProjectsList.contains(filteredProjects.getName())) {
            setProjectCheck(filteredProjects.getName(), true);
          }
        }
      }

      @Override
      public void onClickSearch(ClickSearchEvent searchEvent) {
        hideOnSuccessPanel();
      }
    });

    add(searchProjects.getFilterPanel());

    /* Add view panel (change between tree view and flat view) and its contents */
    SimplePanel viewPanel = new SimplePanel();
    viewPanel.setWidth("75%");
    viewPanel.addStyleName(Gerrit.RESOURCES.css().viewPanel());

    viewButton = new Button();
    viewButton.setText(Util.C.buttonFlatView());
    viewButton.setStyleName(Gerrit.RESOURCES.css().buttonLink());

    treeViewFlag = true;

    viewButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        switchProjectsView();
      }
    });

    viewPanel.add(viewButton);
    add(viewPanel);

    /* Add tree panel and its contents */
    treePanel = new VerticalPanel();
    treePanel.setWidth("100%");
    treePanel.addStyleName(Gerrit.RESOURCES.css().treePanel());

    projectsTree = new Tree();

    final SimplePanel treeSelectAllPanel = new SimplePanel();

    cbTreeSelectAll = new CheckBox(Util.C.checkBoxSelectAll());
    cbTreeSelectAll.addStyleName(Gerrit.RESOURCES.css().selectAllTreeLabel());
    cbTreeSelectAll.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
      @Override
      public void onValueChange(ValueChangeEvent<Boolean> event) {
        Boolean checked = event.getValue();
        changeAllTreeNodesSelection(checked, true);
        if (projects != null) {
          projects.setSelectAllValue(checked);
        }

      }
    });

    treeSelectAllPanel
        .setStyleName(Gerrit.RESOURCES.css().treeSelectAllPanel());
    treeSelectAllPanel.setWidth("100%");
    treeSelectAllPanel.add(cbTreeSelectAll);

    treePanel.add(treeSelectAllPanel);

    treePanel.add(projectsTree);

    vp = new VerticalPanel();
    vp.setWidth("100%");
    vp.add(treePanel);

    /* Add setParent panel */
    setParentProjects = new ParentProjectsSetting();
    setParentProjects.addParentSettingHandler(new ParentSettingHandler() {
      @Override
      public void onResult(ParentSettingOnResultEvent parentSettingEvent) {
        reloadProjectOnCallback();
        selectedProjectsTable.deleteAllRows();
        if (cbTreeSelectAll.getValue()) {
          cbTreeSelectAll.setValue(false);
        }
        if (projects != null) {
          projects.setSelectAllValue(false);
        }
        setParentButton.setEnabled(false);
      }

      @Override
      public void onClose(ParentSettingOnCloseEvent parentSettingEvent) {
        setParentButton.setVisible(true);
      }
    });

    vp.add(setParentProjects.getParentSettingPanel());

    /* Add setParent button */
    setParentButton = new Button(Util.C.buttonSetParentProject());
    setParentButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        showSetParentPanel();
      }
    });
    setParentButton.setEnabled(false);

    vp.add(setParentButton);

    /* Add grid view and is contents */
    gridView = new Grid(2, 2);

    gridView.setWidth("100%");

    gridView.getCellFormatter().setWidth(0, 0, "75%");
    gridView.getCellFormatter().setWidth(0, 1, "25%");
    gridView.getCellFormatter().setAlignment(0, 0,
        HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_TOP);
    gridView.getCellFormatter().setAlignment(0, 1,
        HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_TOP);
    gridView.setCellPadding(5);

    gridView.setWidget(0, 0, vp);

    projects = new ProjectsSelectionTable() {
      @Override
      public void setRegisterKeys(boolean on) {
        if (on) {
          enableNavigationKeys();
        } else {
          disableNavigationKeys();
        }
      }
    };

    projects.setWidth("100%");
    addProjectsTableHandlers();
    projects.setSavePointerId(PageLinks.ADMIN_PROJECTS);

    /* Creates a table to keep selected projects */
    selectedProjectsTable =
        new SelectedItemsTable(Util.C.headingSelectedProjectsTable());

    selectedProjectsTable.addTableDeleteHandler(new TableDeleteHandler() {
      @Override
      public void onDeleteRow(TableDeleteEvent tableDeleteEvent) {
        final String rowText = tableDeleteEvent.getRowText();
        setProjectCheck(rowText, false);
        if (selectedProjectsTable.getRowCount() <= 1) {
          setParentButton.setEnabled(false);
          if (cbTreeSelectAll.getValue()) {
            cbTreeSelectAll.setValue(false);
          }
          if (projects != null) {
            projects.setSelectAllValue(false);
          }
        }
      }
    });

    gridView.setWidget(0, 1, selectedProjectsTable);

    add(gridView);
  }

  private void loadProjects() {
    com.google.gerrit.client.admin.Util.PROJECT_SVC
        .visibleProjects(new ScreenLoadCallback<List<ProjectData>>(this) {
          @Override
          protected void preDisplay(final List<ProjectData> result) {
            buildProjectsTree(result);
            projects.display(result);
            projects.finishDisplay();

            if (result.size() <= 1) {
              cbTreeSelectAll.setEnabled(false);
            }

            if (searchProjects != null) {
              searchProjects.setProjectDataList(result);
            }
          }
        });
  }

  private void addProjectsTableHandlers() {
    projects.addCheckBoxSelectionHandler(new CheckBoxSelectionHandler() {

      @Override
      public void onValueChange(CheckBoxSelectionEvent checkBoxSelectionEvent) {
        final boolean value = checkBoxSelectionEvent.isChecked();
        final String selectedProject = checkBoxSelectionEvent.getProjectName();

        hideOnSuccessPanel();
        setSelectedProjectsTable(value, selectedProject);
      }
    });

    projects
        .addChangeAllCheckBoxSelectionHandler(new ChangeAllCheckBoxSelectionHandler() {

          @Override
          public void onChangeAllSelection(
              ChangeAllCheckBoxSelectionEvent changeAllCheckBoxSelectionEvent) {
            final boolean value = changeAllCheckBoxSelectionEvent.isChecked();

            setParentButton.setEnabled(value);
            if (!value && setParentProjects.getParentSettingPanel().isVisible()) {
              setParentProjects.hideParentSettingPanel();
              setParentButton.setVisible(true);
            }

            if (cbTreeSelectAll != null) {
              cbTreeSelectAll.setValue(value);
            }
          }
        });
  }


  private void buildProjectsTree(final List<ProjectData> result) {
    treeContents = null;
    treeContents = new ProjectsTreeContents(result, ProjectScreen.INFO);

    treeContents.addTreeNodeCheckHandler(new CheckBoxSelectionHandler() {
      @Override
      public void onValueChange(CheckBoxSelectionEvent checkBoxSelectionEvent) {
        final boolean isChecked = checkBoxSelectionEvent.isChecked();
        final String projectName = checkBoxSelectionEvent.getProjectName();
        hideOnSuccessPanel();
        setSelectedProjectsTable(isChecked, projectName);
      }
    });

    treeNodesMap = null;
    projectNodesMap = null;
    treeNodesMap = treeContents.getTreeNodesMap();
    projectNodesMap = treeContents.getProjectNodesMap();

    rootWildProject = treeContents.getRootWildProject();

    projectsTree.addItem(rootWildProject);
    projectsTree.getItem(0).setState(true);
  }

  private void hideOnSuccessPanel() {
    if (onSuccessPanel.isVisible()) {
      onSuccessPanel.setVisible(false);
    }
  }

  private void showSetParentPanel() {
    setParentProjects.setChildProjects(selectedProjectsTable.getInsertedRows());
    setParentButton.setVisible(false);
    setParentProjects.getParentSettingPanel().setVisible(true);
  }

  private void changeAllTreeNodesSelection(boolean checked,
      boolean updateSelectedList) {
    if (treeNodesMap != null) {
      for (final TreeNode key : treeNodesMap.values()) {
        final Widget widget = key.getTreeItem().getWidget();
        if (widget instanceof TreeNodeWidget) {
          ((TreeNodeWidget) widget).getCheckBox().setValue(
              new Boolean(checked), updateSelectedList);
        }
      }
    }
    setParentButton.setEnabled(checked);
    if (!checked && setParentProjects.getParentSettingPanel().isVisible()) {
      setParentProjects.hideParentSettingPanel();
      setParentButton.setVisible(true);
    }
  }

  private void expandTreeNode(TreeNode parentNode) {
    parentNode.getTreeItem().setState(true);
    if (parentNode.getParentName() != null) {
      final TreeNode parent = treeNodesMap.get(parentNode.getParentId());
      expandTreeNode(parent);
    }
  }

  private void switchProjectsView() {
    final List<String> selectedProjectsList =
        selectedProjectsTable.getInsertedRows();

    hideOnSuccessPanel();

    if (treeViewFlag) {
      treeViewFlag = false;
      changeAllTreeNodesSelection(false, false);
      vp.remove(treePanel);

      vp.insert(projects, 0);
      viewButton.setText(Util.C.buttonTreeView());
      searchProjects.changeButtonCaption(true);
    } else {
      treeViewFlag = true;
      projects.changeAllProjectsSelection(false, false);
      vp.remove(projects);

      vp.insert(treePanel, 0);
      viewButton.setText(Util.C.buttonFlatView());
      searchProjects.changeButtonCaption(false);
    }

    if (setParentProjects.getParentSettingPanel().isVisible()) {
      setParentProjects.hideParentSettingPanel();
      setParentButton.setVisible(true);
    }

    if (selectedProjectsList.size() == 0) {
      setParentButton.setEnabled(false);
    } else {
      setParentButton.setEnabled(true);

      for (String selectedProject : selectedProjectsList) {
        setProjectCheck(selectedProject, true);
      }
    }
  }

  private void setProjectCheck(String projectName, boolean check) {
    // This is only to keep projects checked or remove check.
    if (treeViewFlag) {
      int projectId = projectNodesMap.get(projectName);
      final TreeNode treeNode = treeNodesMap.get(projectId);
      final Widget widget = treeNode.getTreeItem().getWidget();
      if (widget instanceof TreeNodeWidget) {
        ((TreeNodeWidget) widget).getCheckBox().setValue(check);
      }
    } else {
      final CheckBox cb = projects.getCheckBoxByProject(projectName);
      if (cb != null) {
        cb.setValue(check);
      }
    }
  }

  private void setSelectedProjectsTable(boolean isChecked, String projectName) {
    // Add or removes items from selected projects table.
    if (isChecked) {
      selectedProjectsTable.addRow(projectName);
    } else {
      selectedProjectsTable.deleteRow(projectName);
    }

    // Controls if the "Set parent" button should be enabled or not
    if (selectedProjectsTable.getRowCount() == 1) {
      setParentButton.setEnabled(false);
    } else {
      setParentButton.setEnabled(true);
    }
  }

  private void reloadProjectContent(boolean isAfterSearch) {
    treePanel.remove(projectsTree);
    if (!isAfterSearch) {
      projectsTree = null;
      projectsTree = new Tree();
    }
    treePanel.add(projectsTree);

    vp.remove(projects);
    projects = null;
    projects = new ProjectsSelectionTable();

    addProjectsTableHandlers();

    if (!treeViewFlag) {
      vp.insert(projects, 0);
    }
  }

  private void reloadProjectOnCallback() {
    reloadProjectContent(false);
    loadProjects();
    onSuccessPanel.setVisible(true);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    projects.setRegisterKeys(true);
  }
}
