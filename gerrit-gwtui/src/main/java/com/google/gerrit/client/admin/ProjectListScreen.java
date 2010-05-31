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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.ParentProjectsSetting;
import com.google.gerrit.client.ui.SearchProjects;
import com.google.gerrit.client.ui.SelectedItemsTable;
import com.google.gerrit.client.ui.TreeContents;
import com.google.gerrit.client.ui.ParentProjectsSetting.ParentSettingListener;
import com.google.gerrit.client.ui.SearchProjects.SearchListener;
import com.google.gerrit.client.ui.SelectedItemsTable.TableDeleteListener;
import com.google.gerrit.client.ui.TreeContents.TreeNode;
import com.google.gerrit.client.ui.TreeContents.TreeNodeCheckListener;
import com.google.gerrit.client.ui.TreeContents.TreeNodeWidget;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.ProjectRightsBased;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Tree;
import com.google.gwt.user.client.ui.TreeItem;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.Cell;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class ProjectListScreen extends AccountScreen {
  private ProjectTable projects;

  private Button setParentButton;
  private SimplePanel onSuccessPanel;
  private Label onSuccessLabel;
  private TreeItem rootWildProject;
  private boolean treeViewFlag;
  private Button viewButton;
  private VerticalPanel treePanel;
  private SelectedItemsTable selectedProjectsTable;
  private SearchProjects searchProjects;
  private ParentProjectsSetting setParentProjects;
  private Grid gridView;

  private TreeMap<String, TreeNode> treeMap;
  private Tree projectsTree;
  private final List<TreeNode> highlightedNodes = new ArrayList<TreeNode>();
  private TreeContents treeContents;
  private CheckBox cbTreeSelectAll;

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
    searchProjects.setSearchListener(new SearchListener() {
      @Override
      public void onSearch(List<ProjectRightsBased> filterProjectsList,
          boolean searchAll) {
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

          for (final ProjectRightsBased k : filterProjectsList) {
            if (treeMap != null) {
              final TreeNode treeNode = treeMap.get(k.getProject().getName());
              if (treeNode != null) {
                final Widget widget = treeNode.getTreeItem().getWidget();
                if (widget instanceof TreeNodeWidget) {
                  ((TreeNodeWidget) widget).highlightNode();
                  highlightedNodes.add(treeNode);
                  if (treeNode.getParentName() != null) {
                    final TreeNode parentNode =
                      treeMap.get(treeNode.getParentName());
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

        for (ProjectRightsBased filteredProjects : filterProjectsList) {
          if (selectedProjectsList.contains(filteredProjects.getProject()
              .getName())) {
            setProjectCheck(filteredProjects.getProject().getName(), true);
          }
        }
      }

      @Override
      public void onClick() {
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
        setProjectsView();
      }
    });

    viewPanel.add(viewButton);
    add(viewPanel);

    /* Add tree panel and its contents */
    treePanel = new VerticalPanel();
    treePanel.setWidth("100%");
    treePanel.addStyleName(Gerrit.RESOURCES.css().treePanel());

    projectsTree = new Tree();

    SimplePanel treeSelectAllPanel = new SimplePanel();

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

    /* Add grid view and is contents */
    gridView = new Grid(1, 2);

    gridView.setWidth("100%");

    gridView.getCellFormatter().setWidth(0, 0, "75%");
    gridView.getCellFormatter().setWidth(0, 1, "25%");
    gridView.getCellFormatter().setAlignment(0, 0,
        HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_TOP);
    gridView.getCellFormatter().setAlignment(0, 1,
        HasHorizontalAlignment.ALIGN_LEFT, HasVerticalAlignment.ALIGN_TOP);
    gridView.setCellPadding(5);

    gridView.setWidget(0, 0, treePanel);

    projects = new ProjectTable();
    projects.setWidth("100%");

    /* Creates a table to keep selected projects */
    selectedProjectsTable =
      new SelectedItemsTable(Util.C.headingSelectedProjectsTable());
    selectedProjectsTable.setDeleteListener(new TableDeleteListener() {
      @Override
      public void onDeleteRow(String rowText) {
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

    /* Add setParent panel */
    setParentProjects = new ParentProjectsSetting();
    setParentProjects.setParentSettingListener(new ParentSettingListener() {
      @Override
      public void onResult(String result) {
        boolean showSuccessMessage = true;
        if (result != null && !result.equals("")) {
          if (result.startsWith("All:")) {
            showSuccessMessage = false;
          }
        }

        if (showSuccessMessage) {
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
      }

      @Override
      public void onClose() {
        setParentButton.setVisible(true);
      }
    });

    add(setParentProjects.getParentSettingPanel());

    /* Add setParent button */
    setParentButton = new Button(Util.C.buttonSetParentProject());
    setParentButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        showSetParentPanel();
      }
    });
    setParentButton.setEnabled(false);

    add(setParentButton);
  }

  private void expandTreeNode(TreeNode parentNode) {
    parentNode.getTreeItem().setState(true);

    String parentName = parentNode.getParentName();

    if (parentName != null) {
      final TreeNode parent = treeMap.get(parentName);
      expandTreeNode(parent);
    }
  }

  private void changeAllTreeNodesSelection(boolean checked,
      boolean updateSelectedList) {
    if (treeMap != null) {
      for (final TreeNode key : treeMap.values()) {
        final Widget widget = key.getTreeItem().getWidget();
        if (widget instanceof TreeNodeWidget) {
          ((TreeNodeWidget) widget).getCheckBox().setValue(new Boolean(checked), updateSelectedList);
        }
      }
    }
    setParentButton.setEnabled(checked);
    if (!checked && setParentProjects.getParentSettingPanel().isVisible()) {
      setParentProjects.hideParentSettingPanel();
      setParentButton.setVisible(true);
    }
  }

  private void setProjectsView() {
    final List<String> selectedProjectsList =
      selectedProjectsTable.getInsertedRows();

    hideOnSuccessPanel();

    if (treeViewFlag) {
      treeViewFlag = false;
      changeAllTreeNodesSelection(false, false);
      gridView.remove(treePanel);

      gridView.setWidget(0, 0, projects);
      viewButton.setText(Util.C.buttonTreeView());
    } else {
      treeViewFlag = true;
      projects.changeAllProjectsSelection(false, false);
      gridView.remove(projects);

      gridView.setWidget(0, 0, treePanel);
      viewButton.setText(Util.C.buttonFlatView());
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
      final TreeNode treeNode = treeMap.get(projectName);
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

  private void setSelectedProjectsTable(boolean isChecked, String projectName){
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

  private void loadProjects() {
    Util.PROJECT_SVC.projectsTO(new ScreenLoadCallback<List<ProjectRightsBased>>(this) {
      @Override
      protected void preDisplay(final List<ProjectRightsBased> result) {
        projects.display(result);
        projects.finishDisplay();
        buildProjectsTree(result);

        if (searchProjects != null) {
          searchProjects.setProjectsRightsBasedList(result);
        }
      }
    });
  }

  private void buildProjectsTree(final List<ProjectRightsBased> result) {
    treeContents = null;
    treeContents = new TreeContents(result, ProjectAdminScreen.INFO_TAB);

    treeContents.setCheckListener(new TreeNodeCheckListener() {

      @Override
      public void onChecked(boolean isChecked, String projectName) {
        hideOnSuccessPanel();
        setSelectedProjectsTable(isChecked, projectName);
      }
    });

    treeMap = null;
    treeMap = treeContents.getTreeMap();

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

  private void reloadProjectContent(boolean isAfterSearch) {
    treePanel.remove(projectsTree);
    if (!isAfterSearch) {
      projectsTree = null;
      projectsTree = new Tree();
    }
    treePanel.add(projectsTree);

    gridView.remove(projects);
    projects = null;
    projects = new ProjectTable();

    if (!treeViewFlag) {
      gridView.setWidget(0, 0, projects);
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

  private class ProjectTable extends NavigationTable<Project> {

    private boolean hasSelectAllRow;
    private CheckBox cbSelectAll;

    ProjectTable() {
      setSavePointerId(PageLinks.ADMIN_PROJECTS);
      keysNavigation.add(new PrevKeyCommand(0, 'k', Util.C.projectListPrev()));
      keysNavigation.add(new NextKeyCommand(0, 'j', Util.C.projectListNext()));
      keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.projectListOpen()));
      keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, Util.C
          .projectListOpen()));

      table.setText(0, 2, Util.C.columnProjectName());
      table.setText(0, 3, Util.C.columnProjectDescription());
      table.setText(0, 4, Util.C.columnParentProject());
      table.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          final Cell cell = table.getCellForEvent(event);
          if (cell != null && cell.getCellIndex() != 1
              && getRowItem(cell.getRowIndex()) != null) {
            movePointerTo(cell.getRowIndex());
            hideOnSuccessPanel();
          }
        }
      });

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());
    }

    @Override
    protected Object getRowItemKey(final Project item) {
      return item.getNameKey();
    }

    @Override
    protected void onOpenRow(final int row) {
      History.newItem(link(getRowItem(row)));
    }

    private String link(final Project item) {
      return Dispatcher.toProjectAdmin(item.getNameKey(),
          ProjectAdminScreen.INFO_TAB);
    }

    void display(final List<ProjectRightsBased> result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final ProjectRightsBased k : result) {
        if (k.isVisible()) {
          final int row = table.getRowCount();
          table.insertRow(row);
          applyDataRowStyle(row);
          populate(row, k.getProject());
        }
      }

      if (result != null && table.getRowCount() > 1) {
        hasSelectAllRow = true;
        populateSelectAllRow();
      } else {
        hasSelectAllRow = false;
      }
    }

    void populate(final int row, final Project k) {

      /* Add a checkBox column for each row, except for "All projects" row */
      if (!k.getNameKey().equals(Gerrit.getConfig().getWildProject())) {
        CheckBox ch = new CheckBox();
        ch.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
          @Override
          public void onValueChange(ValueChangeEvent<Boolean> event) {
            Boolean checked = event.getValue();

            hideOnSuccessPanel();
            setSelectedProjectsTable(checked, k.getName());
          }
        });

        table.setWidget(row, 1, ch);
      }

      table.setWidget(row, 2, new Hyperlink(k.getName(), link(k)));

      table.setText(row, 3, k.getDescription());

      if (k.getParent() != null) {
        table.setText(row, 4, k.getParent().get());
      }

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().cPROJECT());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().dataCell());

      setRowItem(row, k);
    }

    void populateSelectAllRow() {
      int rowCount = table.getRowCount();
      table.insertRow(rowCount);

      cbSelectAll = new CheckBox();

      cbSelectAll.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
        @Override
        public void onValueChange(ValueChangeEvent<Boolean> event) {
          boolean checked = event.getValue();

          changeAllProjectsSelection(checked, true);

          if (cbTreeSelectAll != null) {
            cbTreeSelectAll.setValue(checked);
          }
        }
      });

      table.setWidget(rowCount, 1, cbSelectAll);
      table.setText(rowCount, 2, Util.C.checkBoxSelectAll());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.setColSpan(rowCount, 2, 2);
      fmt.setStyleName(rowCount, 0, Gerrit.RESOURCES.css().selectAllRow());
      fmt.setStyleName(rowCount, 1, Gerrit.RESOURCES.css().selectAllRow());
      fmt.setStyleName(rowCount, 2, Gerrit.RESOURCES.css().selectAllRow());
      fmt.setStyleName(rowCount, 3, Gerrit.RESOURCES.css().selectAllRow());
    }

    void setSelectAllValue(boolean check) {
      if (cbSelectAll != null) {
        cbSelectAll.setValue(check);
      }
    }

    void changeAllProjectsSelection(boolean checked, boolean updateSelectedList){
      for (int row = 1; row < table.getRowCount() - 1; row++) {
        if (table.getWidget(row, 1) instanceof CheckBox) {
          ((CheckBox) table.getWidget(row, 1)).setValue(new Boolean(checked), updateSelectedList);
        }
      }
      setParentButton.setEnabled(checked);
      if (!checked && setParentProjects.getParentSettingPanel().isVisible()) {
        setParentProjects.hideParentSettingPanel();
        setParentButton.setVisible(true);
      }
    }

    public CheckBox getCheckBoxByProject(String projectName) {

      CheckBox cb = null;

      final int projectRowCount = hasSelectAllRow ? table.getRowCount() - 1 : table.getRowCount();

      for (int row = 1; row < projectRowCount; row++) {
        Project p = getRowItem(row);
        if ((p != null) && (p.getName().equals(projectName))) {
          Widget widget = table.getWidget(row, 1);
          if (widget instanceof CheckBox) {
            cb = (CheckBox) widget;
          }
          break;
        }
      }

      return cb;
    }
  }
}