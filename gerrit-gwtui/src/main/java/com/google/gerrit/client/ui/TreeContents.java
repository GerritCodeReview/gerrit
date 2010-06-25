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
import com.google.gerrit.common.data.ProjectData;
import com.google.gerrit.common.data.ProjectRightsBased;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
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
public class TreeContents {

  private TreeMap<Integer,TreeNode> treeNodesMap;
  private TreeMap<String, Integer> projectNodesMap;
  private TreeItem rootWildProject;
  private TreeNodeCheckListener checkListener;
  private ToolTip tip;

  /**
   * Constructor
   * @param projectsList Projects list.
   * @param linkScreen Project tab to which each tree node will link.
   */
  public TreeContents(final List<ProjectRightsBased> projectsList, String linkScreen) {

    if (treeNodesMap == null) {
      treeNodesMap = new TreeMap<Integer, TreeNode>();
    }

    if (projectNodesMap == null) {
      projectNodesMap = new TreeMap<String, Integer>();
    }

    for (final ProjectRightsBased k : projectsList) {
      final String linkToken = Dispatcher.toProjectAdmin(k.getProjectData().getNameKey(), linkScreen);

      if (k.getProjectData().getNameKey().equals(Gerrit.getConfig().getWildProject()) || k.getProjectData().getParentId() == 0 ) {
        if (k.isVisible()) {
          final Hyperlink hLink = new Hyperlink(k.getProjectData().getName(), linkToken);
          hLink.addMouseOverHandler(new MouseOverHandler() {
            @Override
            public void onMouseOver(MouseOverEvent event) {
              int left = hLink.getOffsetWidth();
              int top = hLink.getAbsoluteTop();

              if (tip == null) {
                tip = new ToolTip(k.getProjectData().getDescription(), left, top);
              }

              tip.showToolTip();
            }
          });

          rootWildProject = new TreeItem(hLink);
        } else {
          final Label notVisibleLabel = new Label(k.getProjectData().getName());
          rootWildProject = new TreeItem(notVisibleLabel);
        }
      } else {
        TreeNode node = new TreeNode(k.getProjectData(), linkToken, k.isVisible());

        final Widget widget = node.getTreeItem().getWidget();
        if (widget instanceof TreeNodeWidget) {
          ((TreeNodeWidget) widget).getCheckBox().addValueChangeHandler(new ValueChangeHandler<Boolean>() {

            @Override
            public void onValueChange(ValueChangeEvent<Boolean> event) {
              Boolean checked = event.getValue();

              if (checkListener != null) {
                checkListener.onChecked(checked.booleanValue(), k.getProjectData().getName());
              }
            }
          });
        }

        treeNodesMap.put(k.getProjectData().getId(), node);
        projectNodesMap.put(k.getProjectData().getName(), k.getProjectData().getId());
      }
    }

    List<TreeNode> sortedNodes = new ArrayList<TreeNode>();

    for(final TreeNode key : treeNodesMap.values()) {
      if (key.getProjectData().getParentName() != null) {
        final TreeNode node =  treeNodesMap.get(key.getParentId());
        node.addChild(key.getTreeItem());
      } else {
        sortedNodes.add(key);
      }
    }

    for(final TreeNode key : sortedNodes) {
      rootWildProject.addItem(key.getTreeItem());
    }
  }

  public void setCheckListener(TreeNodeCheckListener checkListener) {
    this.checkListener = checkListener;
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
  public static class TreeNodeWidget extends Composite implements MouseOverHandler {

    private final CheckBox checkBox;
    private final Hyperlink hLink;
    private final HorizontalPanel nodePanel;
    private final String toolTipText;
    private ToolTip tip;

    /**
     * Constructor
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

        tip.showToolTip();
      }
    }
  }

  /** Class created to manipulate the nodes of the project inheritance tree **/
  public static class TreeNode {
    private final TreeItem projectNodeItem;
    private final ProjectData projectData;

    /**
     * Constructor
     * @param p Project.
     * @param linkToken The history token to which the node will link.
     * @param isVisible If the project is visible or not to the logged user.
     */
    public TreeNode(ProjectData p, String linkToken, boolean isVisible) {
      this.projectData = p;

      if (isVisible) {
        final TreeNodeWidget nodeWidget = new TreeNodeWidget(p.getName(), linkToken, p.getDescription());
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
      return projectData.getParentName();
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

  /** Listener to checkBox (customized widget) click event. */
  public static interface TreeNodeCheckListener {
    public void onChecked(boolean isChecked, String projectName);
  }
}

