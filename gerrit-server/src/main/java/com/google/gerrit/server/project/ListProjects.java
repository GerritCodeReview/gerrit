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

package com.google.gerrit.server.project;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.util.TreeFormatter;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/** List projects visible to the calling user. */
public class ListProjects {
  private static final Logger log = LoggerFactory.getLogger(ListProjects.class);

  public static enum FilterType {
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

  private final CurrentUser currentUser;
  private final ProjectCache projectCache;
  private final GitRepositoryManager repoManager;
  private final ProjectNode.Factory projectNodeFactory;

  @Option(name = "--show-branch", aliases = {"-b"}, multiValued = true,
      usage = "displays the sha of each project in the specified branch")
  private List<String> showBranch;

  @Option(name = "--tree", aliases = {"-t"}, usage =
      "displays project inheritance in a tree-like format\n"
      + "this option does not work together with the show-branch option")
  private boolean showTree;

  @Option(name = "--type", usage = "type of project")
  private FilterType type = FilterType.CODE;

  @Option(name = "--description", aliases = {"-d"}, usage = "include description of project in list")
  private boolean showDescription;

  @Option(name = "--all", usage = "display all projects that are accessible by the calling user")
  private boolean all;

  @Inject
  protected ListProjects(CurrentUser currentUser, ProjectCache projectCache,
      GitRepositoryManager repoManager,
      ProjectNode.Factory projectNodeFactory) {
    this.currentUser = currentUser;
    this.projectCache = projectCache;
    this.repoManager = repoManager;
    this.projectNodeFactory = projectNodeFactory;
  }

  public List<String> getShowBranch() {
    return showBranch;
  }

  public boolean isShowTree() {
    return showTree;
  }

  public boolean isShowDescription() {
    return showDescription;
  }

  public void display(OutputStream out) {
    final PrintWriter stdout;
    try {
      stdout = new PrintWriter(new BufferedWriter(new OutputStreamWriter(out, "UTF-8")));
    } catch (UnsupportedEncodingException e) {
      // Our encoding is required by the specifications for the runtime.
      throw new RuntimeException("JVM lacks UTF-8 encoding", e);
    }

    final TreeMap<Project.NameKey, ProjectNode> treeMap =
        new TreeMap<Project.NameKey, ProjectNode>();
    try {
      for (final Project.NameKey projectName : projectCache.all()) {
        final ProjectState e = projectCache.get(projectName);
        if (e == null) {
          // If we can't get it from the cache, pretend its not present.
          //
          continue;
        }

        final ProjectControl pctl = e.controlFor(currentUser);
        final boolean isVisible = pctl.isVisible() || (all && pctl.isOwner());
        if (showTree) {
          treeMap.put(projectName,
              projectNodeFactory.create(pctl.getProject(), isVisible));
          continue;
        }

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
          // If the Git repository is gone, the project doesn't actually exist anymore.
          continue;
        } catch (IOException err) {
          log.warn("Unexpected error reading " + projectName, err);
          continue;
        }

        stdout.print(projectName.get());

        String desc;
        if (showDescription && !(desc = e.getProject().getDescription()).isEmpty()) {
          // We still want to list every project as one-liners, hence escaping \n.
          stdout.print(" - " + desc.replace("\n", "\\n"));
        }

        stdout.print("\n");
      }

      if (showTree && treeMap.size() > 0) {
        printProjectTree(stdout, treeMap);
      }
    } finally {
      stdout.flush();
    }
  }

  private void printProjectTree(final PrintWriter stdout,
      final TreeMap<Project.NameKey, ProjectNode> treeMap) {
    final SortedSet<ProjectNode> sortedNodes = new TreeSet<ProjectNode>();

    // Builds the inheritance tree using a list.
    //
    for (final ProjectNode key : treeMap.values()) {
      if (key.isAllProjects()) {
        sortedNodes.add(key);
        continue;
      }

      ProjectNode node = treeMap.get(key.getParentName());
      if (node != null) {
        node.addChild(key);
      } else {
        sortedNodes.add(key);
      }
    }

    final TreeFormatter treeFormatter = new TreeFormatter(stdout);
    treeFormatter.printTree(sortedNodes);
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
