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
import com.google.gerrit.server.project.CreateProjectHierarchy;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectNode;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

final class ListProjects extends BaseCommand {
  private static final Logger log = LoggerFactory.getLogger(ListProjects.class);

  private static final String NODE_PREFIX = "|-- ";
  private static final String LAST_NODE_PREFIX = "`-- ";
  private static final String DEFAULT_TAB_SEPARATOR = "|";
  private static final String NOT_VISIBLE_PROJECT = "(x)";

  static enum FilterType {
    CODE {
      @Override
      boolean matches(Repository git) throws IOException {
        return !PERMISSIONS.matches(git);
      }
    },
    PERMISSIONS {
      @Override
      boolean matches(Repository git) throws IOException {
        Ref head = git.getRef(Constants.HEAD);
        return head != null
          && head.isSymbolic()
          && GitRepositoryManager.REF_CONFIG.equals(head.getLeaf().getName());
      }
    },
    ALL {
      @Override
      boolean matches(Repository git) {
        return true;
      }
    };

    abstract boolean matches(Repository git) throws IOException;
  }

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  private CreateProjectHierarchy.Factory createProjectHierarchyFactory;

  @Option(name = "--show-branch", aliases = {"-b"}, multiValued = true,
      usage = "displays the sha of each project in the specified branch")
  private List<String> showBranch;

  @Option(name = "--tree", aliases = {"-t"}, usage = "displays project inheritance in a tree-like format\n" +
      "this option does not work together with the show-branch option")
  private boolean showTree;

  @Option(name = "--type", usage = "type of project")
  private FilterType type = FilterType.CODE;

  @Option(name = "--description", aliases = {"-d"}, usage = "include description of project in list")
  private boolean showDescription;

  @Option(name = "--all", usage = "display all projects that are accessible by the calling user")
  private boolean all;

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

    if (showTree && showDescription) {
      throw new UnloggedFailure(1, "fatal: --tree and --description options are not compatible.");
    }

    final PrintWriter stdout = toPrintWriter(out);
    try {
      if (!showTree) {
        printProjectList(stdout);
      } else {
        printProjectTree(stdout);
      }
    } finally {
      stdout.flush();
    }
  }

  private void printProjectList(final PrintWriter stdout) {
    for (final Project.NameKey projectName : projectCache.all()) {
      final ProjectState e = projectCache.get(projectName);
      if (e == null) {
        // If we can't get it from the cache, pretend its not present.
        //
        continue;
      }
      final ProjectControl pctl = e.controlFor(currentUser);
      final boolean isVisible = pctl.isVisible() || (all && pctl.isOwner());
      if (!isVisible) {
        // Require the project itself to be visible to the user.
        //
        continue;
      }

      try {
        if (showBranch != null) {
          Repository git = repoManager.openRepository(projectName);
          try {
            if (!type.matches(git)) {
              continue;
            }

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
          } finally {
            git.close();
          }
        } else if (type != FilterType.ALL) {
          Repository git = repoManager.openRepository(projectName);
          try {
            if (!type.matches(git)) {
              continue;
            }
          } finally {
            git.close();
          }
        }
      } catch (RepositoryNotFoundException err) {
        // If the Git repository is gone, the project doesn't actually exist
        // anymore.
        continue;
      } catch (IOException err) {
        log.warn("Unexpected error reading " + projectName, err);
        continue;
      }

      stdout.print(projectName.get());

      String desc;
      if (showDescription
          && !(desc = e.getProject().getDescription()).isEmpty()) {
        // We still want to list every project as one-liners, hence escaping \n.
        stdout.print(" - " + desc.replace("\n", "\\n"));
      }

      stdout.print("\n");
    }
  }

  private void printProjectTree(final PrintWriter stdout) {
    final CreateProjectHierarchy createProjectHierarchy =
        createProjectHierarchyFactory.create();
    createProjectHierarchy.setAll(all);
    final List<ProjectNode> rootProjectNodes =
        createProjectHierarchy.getRootProjectHierarchies();
    if (!rootProjectNodes.isEmpty()) {
      for (final ProjectNode rootProjectNode : rootProjectNodes) {
        // Check if is not the last "root" parent node,
        // so the "|" separator will not longer be needed.
        //
        final boolean isLast = isLast(rootProjectNodes, rootProjectNode);
        if (!currentTabSeparator.equals(" ") && isLast) {
          currentTabSeparator = " ";
        }

        printElement(stdout, rootProjectNode, 0, isLast);
      }
    }
  }

  private void printElement(final PrintWriter stdout,
      final ProjectNode projectNode, int level, final boolean isLast) {
    if (level > 0) {
      stdout.print(String.format("%-" + 4 * level + "s", currentTabSeparator));
    }

    final String prefix = isLast ? LAST_NODE_PREFIX : NODE_PREFIX;
    final String printout;
    if (projectNode.isVisible()) {
      printout = prefix + projectNode.getProject().getName();
    } else {
      printout = prefix + NOT_VISIBLE_PROJECT;
    }
    stdout.print(printout + "\n");

    if (projectNode.isLeaf()) {
      return;
    } else {
      final List<ProjectNode> children = projectNode.getChildren();
      ++level;
      for (final ProjectNode child : children) {
        printElement(stdout, child, level, isLast(children, child));
      }
    }
  }

  private static <E> boolean isLast(final List<E> list, final E element) {
    return list.indexOf(element) == list.size() - 1;
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
            && (projectControl.controlForRef(ref.getLeaf().getName()).isVisible())
                || (all && projectControl.isOwner())) {
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
}
