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
import com.google.gerrit.common.data.ProjectRightsBased;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.TreeItem;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Class created to fit the project inheritance tree **/
public class TreeContents {

  private TreeMap<String, TreeNode> treeMap;
  private TreeItem rootWildProject;
  private TreeNodeCheckListener checkListener;
  private ToolTip tip;
  private static final String NOT_VISIBLE_PROJECT = "(x)";

  /**
   * Constructor
   * @param projectsList Projects list.
   * @param linkScreen Project tab to which each tree node will link.
   */
  public TreeContents(final List<ProjectRightsBased> projectsList, String linkScreen) {
    if (treeMap == null) {
      treeMap = new TreeMap<String, TreeNode>();
    }

    for (final ProjectRightsBased k : projectsList) {
      final String linkToken = Dispatcher.toProjectAdmin(k.getProject().getNameKey(), linkScreen);

      if (k.getProject().getNameKey().equals(Gerrit.getConfig().getWildProject())) {
        final Hyperlink hLink = new Hyperlink(k.getProject().getName(), linkToken);
        hLink.addMouseOverHandler(new MouseOverHandler() {
          @Override
          public void onMouseOver(MouseOverEvent event) {
            int left = hLink.getOffsetWidth();
            int top = hLink.getAbsoluteTop();

            if (tip == null) {
              tip = new ToolTip(k.getProject().getDescription(), left, top);
            }

            tip.showToolTip();
          }
        });

        if (k.isVisible()) {
          rootWildProject = new TreeItem(hLink);
        } else {
          rootWildProject = new TreeItem(NOT_VISIBLE_PROJECT);
        }
      } else {
        TreeNode node = new TreeNode(k.getProject(), linkToken, k.isVisible());

        final Widget widget = node.getTreeItem().getWidget();
        if (widget instanceof TreeNodeWidget) {
          ((TreeNodeWidget) widget).getCheckBox().addValueChangeHandler(new ValueChangeHandler<Boolean>() {

            @Override
            public void onValueChange(ValueChangeEvent<Boolean> event) {
              Boolean checked = event.getValue();

              if (checkListener != null) {
                checkListener.onChecked(checked.booleanValue(), k.getProject().getName());
              }
            }
          });
        }

        treeMap.put(k.getProject().getName(), node);
      }
    }

    List<TreeNode> sortedNodes = new ArrayList<TreeNode>();

    // Builds the inheritance tree using a list.
    //
    for(final TreeNode key : treeMap.values()) {
      final String parentName = key.getParentName();
      if (parentName != null) {
        final TreeNode node = (TreeNode) treeMap.get((String) parentName);
        node.addChild(key.getTreeItem());
      }
      else {
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

  public TreeMap<String, TreeNode> getTreeMap() {
    return treeMap;
  }

  public TreeItem getRootWildProject() {
    return rootWildProject;
  }

  /** Customized widget for TreeNode TreeItem. **/
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
    private final Project project;

    /**
     * Constructor
     * @param p Project.
     * @param linkToken The history token to which the node will link.
     * @param isVisible If the project is visible or not to the logged user.
     */
    public TreeNode(Project p, String linkToken, boolean isVisible) {
      this.project = p;

      if (isVisible) {
        final TreeNodeWidget nodeWidget = new TreeNodeWidget(p.getName(), linkToken, p.getDescription());
        this.projectNodeItem = new TreeItem(nodeWidget);
      } else {
        this.projectNodeItem = new TreeItem(NOT_VISIBLE_PROJECT);
      }
    }

    public String getParentName() {
      if (project.getParent() != null) {
        return project.getParent().get();
      }

      return null;
    }

    public void addChild(TreeItem node) {
      projectNodeItem.addItem(node);
    }

    public Project getProject() {
      return project;
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

