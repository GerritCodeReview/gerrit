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

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.StringUtil;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectJson.ProjectInfo;
import com.google.gerrit.server.util.TreeFormatter;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/** List projects visible to the calling user. */
public class ListProjects implements RestReadView<TopLevelResource> {
  private static final Logger log = LoggerFactory.getLogger(ListProjects.class);

  public static enum FilterType {
    CODE {
      @Override
      boolean matches(Repository git) throws IOException {
        return !PERMISSIONS.matches(git);
      }
    },
    PARENT_CANDIDATES {
      @Override
      boolean matches(Repository git) {
        return true;
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
  private final GroupCache groupCache;
  private final GroupControl.Factory groupControlFactory;
  private final GitRepositoryManager repoManager;
  private final ProjectNode.Factory projectNodeFactory;

  @Deprecated
  @Option(name = "--format", usage = "(deprecated) output format")
  private OutputFormat format = OutputFormat.TEXT;

  @Option(name = "--show-branch", aliases = {"-b"}, multiValued = true,
      usage = "displays the sha of each project in the specified branch")
  public void addShowBranch(String branch) {
    showBranch.add(branch);
  }

  @Option(name = "--tree", aliases = {"-t"}, usage =
      "displays project inheritance in a tree-like format\n"
      + "this option does not work together with the show-branch option")
  public void setShowTree(boolean showTree) {
    this.showTree = showTree;
  }

  @Option(name = "--type", usage = "type of project")
  public void setFilterType(FilterType type) {
    this.type = type;
  }

  @Option(name = "--description", aliases = {"-d"}, usage = "include description of project in list")
  public void setShowDescription(boolean showDescription) {
    this.showDescription = showDescription;
  }

  @Option(name = "--all", usage = "display all projects that are accessible by the calling user")
  public void setAll(boolean all) {
    this.all = all;
  }

  @Option(name = "--limit", aliases = {"-n"}, metaVar = "CNT", usage = "maximum number of projects to list")
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(name = "-p", metaVar = "PREFIX", usage = "match project prefix")
  public void setMatchPrefix(String matchPrefix) {
    this.matchPrefix = matchPrefix;
  }

  @Option(name = "-m", metaVar = "MATCH", usage = "match project substring")
  public void setMatchSubstring(String matchSubstring) {
    this.matchSubstring = matchSubstring;
  }

  @Option(name = "--has-acl-for", metaVar = "GROUP", usage =
      "displays only projects on which access rights for this group are directly assigned")
  public void setGroupUuid(AccountGroup.UUID groupUuid) {
    this.groupUuid = groupUuid;
  }

  private final List<String> showBranch = Lists.newArrayList();
  private boolean showTree;
  private FilterType type = FilterType.CODE;
  private boolean showDescription;
  private boolean all;
  private int limit;
  private String matchPrefix;
  private String matchSubstring;
  private AccountGroup.UUID groupUuid;

  @Inject
  protected ListProjects(CurrentUser currentUser, ProjectCache projectCache,
      GroupCache groupCache, GroupControl.Factory groupControlFactory,
      GitRepositoryManager repoManager, ProjectNode.Factory projectNodeFactory) {
    this.currentUser = currentUser;
    this.projectCache = projectCache;
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
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

  public OutputFormat getFormat() {
    return format;
  }

  public ListProjects setFormat(OutputFormat fmt) {
    format = fmt;
    return this;
  }

  @Override
  public Object apply(TopLevelResource resource) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    if (format == OutputFormat.TEXT) {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      display(buf);
      return BinaryResult.create(buf.toByteArray())
          .setContentType("text/plain")
          .setCharacterEncoding("UTF-8");
    }
    format = OutputFormat.JSON;
    return display(null);
  }

  public JsonElement display(OutputStream displayOutputStream) {
    PrintWriter stdout = null;
    if (displayOutputStream != null) {
      try {
        stdout = new PrintWriter(new BufferedWriter(
            new OutputStreamWriter(displayOutputStream, "UTF-8")));
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException("JVM lacks UTF-8 encoding", e);
      }
    }

    int found = 0;
    Map<String, ProjectInfo> output = Maps.newTreeMap();
    Map<String, String> hiddenNames = Maps.newHashMap();
    Set<String> rejected = new HashSet<String>();

    final TreeMap<Project.NameKey, ProjectNode> treeMap =
        new TreeMap<Project.NameKey, ProjectNode>();
    try {
      for (final Project.NameKey projectName : scan()) {
        final ProjectState e = projectCache.get(projectName);
        if (e == null) {
          // If we can't get it from the cache, pretend its not present.
          //
          continue;
        }

        final ProjectControl pctl = e.controlFor(currentUser);
        if (groupUuid != null) {
          try {
            if (!groupControlFactory.controlFor(groupUuid).isVisible()) {
              break;
            }
          } catch (NoSuchGroupException ex) {
            break;
          }
          if (!pctl.getLocalGroups().contains(
              GroupReference.forGroup(groupCache.get(groupUuid)))) {
            continue;
          }
        }

        ProjectInfo info = new ProjectInfo();
        if (type == FilterType.PARENT_CANDIDATES) {
          ProjectState parentState = Iterables.getFirst(e.parents(), null);
          if (parentState != null
              && !output.keySet().contains(parentState.getProject().getName())
              && !rejected.contains(parentState.getProject().getName())) {
            ProjectControl parentCtrl = parentState.controlFor(currentUser);
            if (parentCtrl.isVisible() || parentCtrl.isOwner()) {
              info.name = parentState.getProject().getName();
              info.description = Strings.emptyToNull(
                  parentState.getProject().getDescription());
            } else {
              rejected.add(parentState.getProject().getName());
              continue;
            }
          } else {
            continue;
          }

        } else {
          final boolean isVisible = pctl.isVisible() || (all && pctl.isOwner());
          if (showTree && !format.isJson()) {
            treeMap.put(projectName,
                projectNodeFactory.create(pctl.getProject(), isVisible));
            continue;
          }

          if (!isVisible && !(showTree && pctl.isOwner())) {
            // Require the project itself to be visible to the user.
            //
            continue;
          }

          info.name = projectName.get();
          if (showTree && format.isJson()) {
            ProjectState parent = Iterables.getFirst(e.parents(), null);
            if (parent != null) {
              ProjectControl parentCtrl = parent.controlFor(currentUser);
              if (parentCtrl.isVisible() || parentCtrl.isOwner()) {
                info.parent = parent.getProject().getName();
              } else {
                info.parent = hiddenNames.get(parent.getProject().getName());
                if (info.parent == null) {
                  info.parent = "?-" + (hiddenNames.size() + 1);
                  hiddenNames.put(parent.getProject().getName(), info.parent);
                }
              }
            }
          }
          if (showDescription) {
            info.description = Strings.emptyToNull(e.getProject().getDescription());
          }

          try {
            if (!showBranch.isEmpty()) {
              Repository git = repoManager.openRepository(projectName);
              try {
                if (!type.matches(git)) {
                  continue;
                }

                List<Ref> refs = getBranchRefs(projectName, pctl);
                if (!hasValidRef(refs)) {
                  continue;
                }

                for (int i = 0; i < showBranch.size(); i++) {
                  Ref ref = refs.get(i);
                  if (ref != null && ref.getObjectId() != null) {
                    if (info.branches == null) {
                      info.branches = Maps.newLinkedHashMap();
                    }
                    info.branches.put(showBranch.get(i), ref.getObjectId().name());
                  }
                }
              } finally {
                git.close();
              }
            } else if (!showTree && type != FilterType.ALL) {
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
        }

        if (limit > 0 && ++found > limit) {
          break;
        }

        if (stdout == null || format.isJson()) {
          output.put(info.name, info);
          continue;
        }

        if (!showBranch.isEmpty()) {
          for (String name : showBranch) {
            String ref = info.branches != null ? info.branches.get(name) : null;
            if (ref == null) {
              // Print stub (forty '-' symbols)
              ref = "----------------------------------------";
            }
            stdout.print(ref);
            stdout.print(' ');
          }
        }
        stdout.print(info.name);

        if (info.description != null) {
          // We still want to list every project as one-liners, hence escaping \n.
          stdout.print(" - " + StringUtil.escapeString(info.description));
        }
        stdout.print('\n');
      }

      for (ProjectInfo info : output.values()) {
        info.finish();
        info.name = null;
      }
      if (stdout == null) {
        return OutputFormat.JSON.newGson().toJsonTree(
            output,
            new TypeToken<Map<String, Object>>() {}.getType());
      } else if (format.isJson()) {
        format.newGson().toJson(
            output, new TypeToken<Map<String, ProjectInfo>>() {}.getType(), stdout);
        stdout.print('\n');
      } else if (showTree && treeMap.size() > 0) {
        printProjectTree(stdout, treeMap);
      }
      return null;
    } finally {
      if (stdout != null) {
        stdout.flush();
      }
    }
  }

  private Iterable<Project.NameKey> scan() {
    if (matchPrefix != null) {
      return projectCache.byName(matchPrefix);
    } else if (matchSubstring != null) {
      return Iterables.filter(projectCache.all(),
          new Predicate<Project.NameKey>() {
            public boolean apply(Project.NameKey in) {
              return in.get().toLowerCase(Locale.US)
                  .contains(matchSubstring.toLowerCase(Locale.US));
            }
          });
    } else {
      return projectCache.all();
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
