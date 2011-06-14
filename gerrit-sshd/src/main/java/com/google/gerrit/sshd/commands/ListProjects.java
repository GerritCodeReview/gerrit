// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

final class ListProjects extends BaseCommand {
  private static final String NODE_PREFIX = "|-- ";
  private static final String LAST_NODE_PREFIX = "`-- ";
  private static final String DEFAULT_TAB_SEPARATOR = "|";
  private static final String NOT_VISIBLE_PROJECT = "(x)";

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private GitRepositoryManager repoManager;

  @Option(name = "--show-branch", aliases = {"-b"}, multiValued = true,
      usage = "displays the sha of each project in the specified branch")
  private List<String> showBranch;

  @Option(name = "--tree", aliases = {"-t"}, usage = "displays project inheritance in a tree-like format\n" +
      "this option does not work together with the show-branch option")
  private boolean showTree;

  private String currentTabSeparator = DEFAULT_TAB_SEPARATOR;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        ListProjects.this.display();
      }
    });
  }

  private void display() throws Failure {
    if (showTree && (showBranch != null)) {
      throw new UnloggedFailure(1, "fatal: --tree and --show-branch options are not compatible.");
    }

    final PrintWriter stdout = toPrintWriter(out);
    final TreeMap<String, TreeNode> treeMap = new TreeMap<String, TreeNode>();
    try {
      for (final Project.NameKey projectName : projectCache.all()) {
        final ProjectState e = projectCache.get(projectName);
        if (e == null) {
          // If we can't get it from the cache, pretend its not present.
          //
          continue;
        }

        final ProjectControl pctl = e.controlFor(currentUser);
        final boolean isVisible = pctl.isVisible();
        if (showTree) {
          treeMap.put(projectName.get(), new TreeNode(pctl.getProject(), isVisible));
          continue;
        }

        if (!isVisible) {
          // Require the project itself to be visible to the user.
          //
          continue;
        }

        if (showBranch != null) {
          List<Ref> refs = getBranchRefs(projectName, pctl);
          if (!hasValidRef(refs)) {
           continue;
          }

          for (Ref ref : refs) {
            if (ref == null) {
              // Print stub (forty '-' symbols)
              stdout.print("----------------------------------------");
            } else {
              stdout.print(ref.getObjectId().name());
            }
            stdout.print(' ');
          }
        }

        stdout.print(projectName.get() + "\n");
      }

      if (showTree && treeMap.size() > 0) {
        printProjectTree(stdout, treeMap);
      }
    } finally {
      stdout.flush();
    }
  }

  private void printProjectTree(final PrintWriter stdout,
      final TreeMap<String, TreeNode> treeMap) {
    final List<TreeNode> sortedNodes = new ArrayList<TreeNode>();

    // Builds the inheritance tree using a list.
    //
    for (final TreeNode key : treeMap.values()) {
      final String parentName = key.getParentName();
      if (parentName != null) {
        final TreeNode node = treeMap.get(parentName);
        if (node != null) {
          node.addChild(key);
        } else {
          sortedNodes.add(key);
        }
      } else {
        sortedNodes.add(key);
      }
    }

    // Builds a fake root node, which contains the sorted projects.
    //
    final TreeNode fakeRoot = new TreeNode(null, sortedNodes, false);
    printElement(stdout, fakeRoot, -1, false, sortedNodes.get(sortedNodes.size() - 1));
    stdout.flush();
  }

  private List<Ref> getBranchRefs(Project.NameKey projectName,
      ProjectControl projectControl) {
  Ref[] result = new Ref[showBranch.size()];
  try {
      Repository git = repoManager.openRepository(projectName);
      try {
        for (int i = 0; i < showBranch.size(); i++) {
          Ref ref = git.getRef(showBranch.get(i));
          if (ref != null
            && ref.getObjectId() != null
            && projectControl.controlForRef(ref.getLeaf().getName()).isVisible()) {
            result[i] = ref;
          }
        }
      } finally {
        git.close();
      }
    } catch (IOException ioe) {
      // Fall through and return what is available.
    }
    return Arrays.asList(result);
  }

  private static boolean hasValidRef(List<Ref> refs) {
    for (int i = 0; i < refs.size(); i++) {
      if (refs.get(i) != null) {
        return true;
      }
    }
    return false;
  }

  /** Class created to manipulate the nodes of the project inheritance tree **/
  private static class TreeNode {
    private final List<TreeNode> children;
    private final Project project;
    private final boolean isVisible;

    /**
     * Constructor
     * @param p Project
     */
    public TreeNode(Project p, boolean visible) {
      this.children = new ArrayList<TreeNode>();
      this.project = p;
      this.isVisible = visible;
    }

    /**
     * Constructor used for creating the fake node
     * @param p Project
     * @param c List of nodes
     */
    public TreeNode(Project p, List<TreeNode> c, boolean visible) {
      this.children = c;
      this.project = p;
      this.isVisible = visible;
    }

    /**
     * Returns if the the node is leaf
     * @return True if is lead, false, otherwise
     */
    public boolean isLeaf() {
      return children.size() == 0;
    }

    /**
     * Returns the project parent name
     * @return Project parent name
     */
    public String getParentName() {
      if (project.getParent() != null) {
        return project.getParent().get();
      }

      return null;
    }

    /**
     * Adds a child to the list
     * @param node TreeNode child
     */
    public void addChild(TreeNode node) {
      children.add(node);
    }

    /**
     * Returns the project instance
     * @return Project instance
     */
    public Project getProject() {
      return project;
    }

    /**
     * Returns the list of children nodes
     * @return List of children nodes
     */
    public List<TreeNode> getChildren() {
      return children;
    }

    /**
     * Returns if the project is visible to the user
     * @return True if is visible, false, otherwise
     */
    public boolean isVisible() {
      return isVisible;
    }
  }

  /**
   * Used to display the project inheritance tree recursively
   * @param stdout PrintWriter used do print
   * @param node Tree node
   * @param level Current level of the tree
   * @param isLast True, if is the last node of a level, false, otherwise
   * @param lastParentNode Last "root" parent node
   */
  private void printElement(final PrintWriter stdout, TreeNode node, int level, boolean isLast,
      final TreeNode lastParentNode) {
    // Checks if is not the "fake" root project.
    //
    if (node.getProject() != null) {

      // Check if is not the last "root" parent node,
      // so the "|" separator will not longer be needed.
      //
      if (!currentTabSeparator.equals(" ")) {
        final String nodeProject = node.getProject().getName();
        final String lastParentProject = lastParentNode.getProject().getName();

        if (nodeProject.equals(lastParentProject)) {
          currentTabSeparator = " ";
        }
      }

      if (level > 0) {
        stdout.print(String.format("%-" + 4 * level + "s", currentTabSeparator));
      }

      final String prefix = isLast ? LAST_NODE_PREFIX : NODE_PREFIX ;

      String printout;

      if (node.isVisible()) {
        printout = prefix + node.getProject().getName();
      } else {
        printout = prefix + NOT_VISIBLE_PROJECT;
      }

      stdout.print(printout + "\n");
    }

    if (node.isLeaf()) {
      return;
    } else {
      final List<TreeNode> children = node.getChildren();
      ++level;
      for(TreeNode treeNode : children) {
        final boolean isLastIndex = children.indexOf(treeNode) == children.size() - 1;
        printElement(stdout, treeNode, level, isLastIndex, lastParentNode);
      }
    }
  }
}
