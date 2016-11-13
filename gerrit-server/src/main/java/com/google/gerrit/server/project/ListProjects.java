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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.StringUtil;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.util.RegexListSearcher;
import com.google.gerrit.server.util.TreeFormatter;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** List projects visible to the calling user. */
public class ListProjects implements RestReadView<TopLevelResource> {
  private static final Logger log = LoggerFactory.getLogger(ListProjects.class);

  public enum FilterType {
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
        Ref head = git.getRefDatabase().exactRef(Constants.HEAD);
        return head != null
            && head.isSymbolic()
            && RefNames.REFS_CONFIG.equals(head.getLeaf().getName());
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
  private final GroupsCollection groupsCollection;
  private final GroupControl.Factory groupControlFactory;
  private final GitRepositoryManager repoManager;
  private final ProjectNode.Factory projectNodeFactory;
  private final WebLinks webLinks;

  @Deprecated
  @Option(name = "--format", usage = "(deprecated) output format")
  private OutputFormat format = OutputFormat.TEXT;

  @Option(
    name = "--show-branch",
    aliases = {"-b"},
    usage = "displays the sha of each project in the specified branch"
  )
  public void addShowBranch(String branch) {
    showBranch.add(branch);
  }

  @Option(
    name = "--tree",
    aliases = {"-t"},
    usage =
        "displays project inheritance in a tree-like format\n"
            + "this option does not work together with the show-branch option"
  )
  public void setShowTree(boolean showTree) {
    this.showTree = showTree;
  }

  @Option(name = "--type", usage = "type of project")
  public void setFilterType(FilterType type) {
    this.type = type;
  }

  @Option(
    name = "--description",
    aliases = {"-d"},
    usage = "include description of project in list"
  )
  public void setShowDescription(boolean showDescription) {
    this.showDescription = showDescription;
  }

  @Option(name = "--all", usage = "display all projects that are accessible by the calling user")
  public void setAll(boolean all) {
    this.all = all;
  }

  @Option(
    name = "--limit",
    aliases = {"-n"},
    metaVar = "CNT",
    usage = "maximum number of projects to list"
  )
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(
    name = "--start",
    aliases = {"-S"},
    metaVar = "CNT",
    usage = "number of projects to skip"
  )
  public void setStart(int start) {
    this.start = start;
  }

  @Option(
    name = "--prefix",
    aliases = {"-p"},
    metaVar = "PREFIX",
    usage = "match project prefix"
  )
  public void setMatchPrefix(String matchPrefix) {
    this.matchPrefix = matchPrefix;
  }

  @Option(
    name = "--match",
    aliases = {"-m"},
    metaVar = "MATCH",
    usage = "match project substring"
  )
  public void setMatchSubstring(String matchSubstring) {
    this.matchSubstring = matchSubstring;
  }

  @Option(name = "-r", metaVar = "REGEX", usage = "match project regex")
  public void setMatchRegex(String matchRegex) {
    this.matchRegex = matchRegex;
  }

  @Option(
    name = "--has-acl-for",
    metaVar = "GROUP",
    usage = "displays only projects on which access rights for this group are directly assigned"
  )
  public void setGroupUuid(AccountGroup.UUID groupUuid) {
    this.groupUuid = groupUuid;
  }

  private final List<String> showBranch = new ArrayList<>();
  private boolean showTree;
  private FilterType type = FilterType.ALL;
  private boolean showDescription;
  private boolean all;
  private int limit;
  private int start;
  private String matchPrefix;
  private String matchSubstring;
  private String matchRegex;
  private AccountGroup.UUID groupUuid;

  @Inject
  protected ListProjects(
      CurrentUser currentUser,
      ProjectCache projectCache,
      GroupsCollection groupsCollection,
      GroupControl.Factory groupControlFactory,
      GitRepositoryManager repoManager,
      ProjectNode.Factory projectNodeFactory,
      WebLinks webLinks) {
    this.currentUser = currentUser;
    this.projectCache = projectCache;
    this.groupsCollection = groupsCollection;
    this.groupControlFactory = groupControlFactory;
    this.repoManager = repoManager;
    this.projectNodeFactory = projectNodeFactory;
    this.webLinks = webLinks;
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
  public Object apply(TopLevelResource resource) throws BadRequestException {
    if (format == OutputFormat.TEXT) {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      display(buf);
      return BinaryResult.create(buf.toByteArray())
          .setContentType("text/plain")
          .setCharacterEncoding(UTF_8);
    }
    return apply();
  }

  public SortedMap<String, ProjectInfo> apply() throws BadRequestException {
    format = OutputFormat.JSON;
    return display(null);
  }

  public SortedMap<String, ProjectInfo> display(OutputStream displayOutputStream)
      throws BadRequestException {
    PrintWriter stdout = null;
    if (displayOutputStream != null) {
      stdout =
          new PrintWriter(new BufferedWriter(new OutputStreamWriter(displayOutputStream, UTF_8)));
    }

    int foundIndex = 0;
    int found = 0;
    TreeMap<String, ProjectInfo> output = new TreeMap<>();
    Map<String, String> hiddenNames = new HashMap<>();
    Set<String> rejected = new HashSet<>();

    final TreeMap<Project.NameKey, ProjectNode> treeMap = new TreeMap<>();
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
          if (!pctl.getLocalGroups()
              .contains(GroupReference.forGroup(groupsCollection.parseId(groupUuid.get())))) {
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
              info.description = Strings.emptyToNull(parentState.getProject().getDescription());
              info.state = parentState.getProject().getState();
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
            treeMap.put(projectName, projectNodeFactory.create(pctl.getProject(), isVisible));
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

          info.state = e.getProject().getState();

          try {
            if (!showBranch.isEmpty()) {
              try (Repository git = repoManager.openRepository(projectName)) {
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
                      info.branches = new LinkedHashMap<>();
                    }
                    info.branches.put(showBranch.get(i), ref.getObjectId().name());
                  }
                }
              }
            } else if (!showTree && type != FilterType.ALL) {
              try (Repository git = repoManager.openRepository(projectName)) {
                if (!type.matches(git)) {
                  continue;
                }
              }
            }

          } catch (RepositoryNotFoundException err) {
            // If the Git repository is gone, the project doesn't actually exist anymore.
            continue;
          } catch (IOException err) {
            log.warn("Unexpected error reading " + projectName, err);
            continue;
          }
          FluentIterable<WebLinkInfo> links = webLinks.getProjectLinks(projectName.get());
          info.webLinks = links.isEmpty() ? null : links.toList();
        }

        if (foundIndex++ < start) {
          continue;
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
        info.id = Url.encode(info.name);
        info.name = null;
      }
      if (stdout == null) {
        return output;
      } else if (format.isJson()) {
        format
            .newGson()
            .toJson(output, new TypeToken<Map<String, ProjectInfo>>() {}.getType(), stdout);
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

  private Iterable<Project.NameKey> scan() throws BadRequestException {
    if (matchPrefix != null) {
      checkMatchOptions(matchSubstring == null && matchRegex == null);
      return projectCache.byName(matchPrefix);
    } else if (matchSubstring != null) {
      checkMatchOptions(matchPrefix == null && matchRegex == null);
      return Iterables.filter(
          projectCache.all(),
          p -> p.get().toLowerCase(Locale.US).contains(matchSubstring.toLowerCase(Locale.US)));
    } else if (matchRegex != null) {
      checkMatchOptions(matchPrefix == null && matchSubstring == null);
      RegexListSearcher<Project.NameKey> searcher;
      try {
        searcher =
            new RegexListSearcher<Project.NameKey>(matchRegex) {
              @Override
              public String apply(Project.NameKey in) {
                return in.get();
              }
            };
      } catch (IllegalArgumentException e) {
        throw new BadRequestException(e.getMessage());
      }
      return searcher.search(ImmutableList.copyOf(projectCache.all()));
    } else {
      return projectCache.all();
    }
  }

  private static void checkMatchOptions(boolean cond) throws BadRequestException {
    if (!cond) {
      throw new BadRequestException("specify exactly one of p/m/r");
    }
  }

  private void printProjectTree(
      final PrintWriter stdout, final TreeMap<Project.NameKey, ProjectNode> treeMap) {
    final SortedSet<ProjectNode> sortedNodes = new TreeSet<>();

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

  private List<Ref> getBranchRefs(Project.NameKey projectName, ProjectControl projectControl) {
    Ref[] result = new Ref[showBranch.size()];
    try (Repository git = repoManager.openRepository(projectName)) {
      for (int i = 0; i < showBranch.size(); i++) {
        Ref ref = git.findRef(showBranch.get(i));
        if (ref != null
                && ref.getObjectId() != null
                && (projectControl.controlForRef(ref.getLeaf().getName()).isVisible())
            || (all && projectControl.isOwner())) {
          result[i] = ref;
        }
      }
    } catch (IOException ioe) {
      // Fall through and return what is available.
    }
    return Arrays.asList(result);
  }

  private static boolean hasValidRef(List<Ref> refs) {
    for (Ref ref : refs) {
      if (ref != null) {
        return true;
      }
    }
    return false;
  }
}
