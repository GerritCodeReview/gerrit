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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.admin.CheckBoxSelectionEvent;
import com.google.gerrit.client.admin.CheckBoxSelectionHandler;
import com.google.gerrit.common.data.ProjectData;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TreeItem;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Class created to fit the project inheritance tree */
public class ProjectsTreeContents {
  final private HandlerManager handlerManager = new HandlerManager(this);

  private TreeMap<Integer, TreeNode> treeNodesMap;
  private TreeMap<String, Integer> projectNodesMap;
  private TreeItem rootWildProject;
  private ToolTip tip;

  /**
   * Constructor
   *
   * @param projectsList Projects list.
   * @param linkScreen Project tab to which each tree node will link.
   */
  public ProjectsTreeContents(final List<ProjectData> projectsList,
      String linkScreen) {

    if (treeNodesMap == null) {
      treeNodesMap = new TreeMap<Integer, TreeNode>();
    }

    if (projectNodesMap == null) {
      projectNodesMap = new TreeMap<String, Integer>();
    }

    for (final ProjectData k : projectsList) {
      final String linkToken =
          Dispatcher.toProjectAdmin(k.getNameKey(), linkScreen);

      if (k.isWildProject()) {
        if (k.isVisible()) {
          final Hyperlink hLink = new Hyperlink(k.getName(), linkToken);
          hLink.addMouseOverHandler(new MouseOverHandler() {
            @Override
            public void onMouseOver(MouseOverEvent event) {
              int left = hLink.getOffsetWidth();
              int top = hLink.getAbsoluteTop();

              if (tip == null) {
                tip = new ToolTip(k.getDescription(), left, top);
              }

              if (!k.getDescription().isEmpty()) {
                tip.showToolTip();
              }
            }
          });

          rootWildProject = new TreeItem(hLink);
        } else {
          final Label notVisibleLabel = new Label(k.getName());
          rootWildProject = new TreeItem(notVisibleLabel);
        }
      } else {
        TreeNode node = new TreeNode(k, linkToken, k.isVisible());

        final Widget widget = node.getTreeItem().getWidget();
        if (widget instanceof TreeNodeWidget) {
          ((TreeNodeWidget) widget).getCheckBox().addValueChangeHandler(
              new ValueChangeHandler<Boolean>() {

                @Override
                public void onValueChange(ValueChangeEvent<Boolean> event) {
                  Boolean checked = event.getValue();

                  handlerManager.fireEvent(new CheckBoxSelectionEvent(checked
                      .booleanValue(), k.getName()));
                }
              });
        }

        treeNodesMap.put(k.getId(), node);
        projectNodesMap.put(k.getName(), k.getId());
      }
    }

    List<TreeNode> sortedNodes = new ArrayList<TreeNode>();

    for (final Integer id : projectNodesMap.values()) {
      if (treeNodesMap.get(id).getProjectData().getParentNameKey() != null) {
        final TreeNode node =
            treeNodesMap.get(treeNodesMap.get(id).getParentId());
        node.addChild(treeNodesMap.get(id).getTreeItem());
      } else {
        sortedNodes.add(treeNodesMap.get(id));
      }
    }

    for (final TreeNode key : sortedNodes) {
      rootWildProject.addItem(key.getTreeItem());
    }
  }

  public TreeMap<Integer, TreeNode> getTreeNodesMap() {
    return treeNodesMap;
  }

  public TreeMap<String, Integer> getProjectNodesMap() {
    return projectNodesMap;
  }

  public TreeItem getRootWildProject() {
    return rootWildProject;
  }

  /** Customized widget for TreeNode TreeItem. */
  public static class TreeNodeWidget extends Composite implements
      MouseOverHandler {

    private final CheckBox checkBox;
    private final Hyperlink hLink;
    private final HorizontalPanel nodePanel;
    private final String toolTipText;
    private ToolTip tip;

    /**
     * Constructor
     *
     * @param text Hyperlink Text.
     * @param token The history token to which the node will link.
     * @param toolTipText Project Tool Tip Text.
     */
    public TreeNodeWidget(String text, String token, String toolTipText) {
      this.toolTipText = toolTipText;

      checkBox = new CheckBox();
      hLink = new Hyperlink(text, token);

      if (toolTipText != null) {
        hLink.addMouseOverHandler(this);
      }

      nodePanel = new HorizontalPanel();

      nodePanel.add(checkBox);
      nodePanel.add(hLink);

      initWidget(nodePanel);

      setStyleName(Gerrit.RESOURCES.css().cPROJECT());
    }

    public CheckBox getCheckBox() {
      return checkBox;
    }

    public void highlightNode() {
      nodePanel.addStyleName(Gerrit.RESOURCES.css().highlightPanel());
    }

    public void removeNodeHighlight() {
      nodePanel.removeStyleName(Gerrit.RESOURCES.css().highlightPanel());
    }

    @Override
    public void onMouseOver(MouseOverEvent event) {
      Object sender = event.getSource();
      if (sender == hLink) {
        int left = nodePanel.getOffsetWidth();
        int top = nodePanel.getAbsoluteTop();

        if (tip == null) {
          tip = new ToolTip(toolTipText, left, top);
        }

        if (!toolTipText.isEmpty()) {
          tip.showToolTip();
        }
      }
    }
  }

  /** Class created to manipulate the nodes of the project inheritance tree **/
  public static class TreeNode {
    private final TreeItem projectNodeItem;
    private final ProjectData projectData;

    /**
     * Constructor
     *
     * @param p Project.
     * @param linkToken The history token to which the node will link.
     * @param isVisible If the project is visible or not to the logged user.
     */
    public TreeNode(ProjectData p, String linkToken, boolean isVisible) {
      this.projectData = p;

      if (isVisible) {
        final TreeNodeWidget nodeWidget =
            new TreeNodeWidget(p.getName(), linkToken, p.getDescription());
        this.projectNodeItem = new TreeItem(nodeWidget);
      } else {
        final Label notVisibleLabel = new Label(p.getName());
        this.projectNodeItem = new TreeItem(notVisibleLabel);
      }
    }

    public int getParentId() {
      return projectData.getParentId();
    }

    public int getProjectId() {
      return projectData.getId();
    }

    public String getParentName() {
      return projectData.getParentNameKey() != null ? projectData
          .getParentNameKey().get() : null;
    }

    public void addChild(TreeItem node) {
      projectNodeItem.addItem(node);
    }

    public ProjectData getProjectData() {
      return projectData;
    }

    public TreeItem getTreeItem() {
      return projectNodeItem;
    }
  }

  public void addTreeNodeCheckHandler(CheckBoxSelectionHandler handler) {
    handlerManager.addHandler(CheckBoxSelectionEvent.getType(), handler);
  }
}
