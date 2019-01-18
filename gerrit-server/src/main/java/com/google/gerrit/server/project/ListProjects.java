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

import static com.google.gerrit.extensions.client.ProjectState.HIDDEN;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.AuthException;
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
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
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

      @Override
      boolean useMatch() {
        return true;
      }
    },
    PARENT_CANDIDATES {
      @Override
      boolean matches(Repository git) {
        return true;
      }

      @Override
      boolean useMatch() {
        return false;
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

      @Override
      boolean useMatch() {
        return true;
      }
    },
    ALL {
      @Override
      boolean matches(Repository git) {
        return true;
      }

      @Override
      boolean useMatch() {
        return false;
      }
    };

    abstract boolean matches(Repository git) throws IOException;

    abstract boolean useMatch();
  }

  private final CurrentUser currentUser;
  private final ProjectCache projectCache;
  private final GroupsCollection groupsCollection;
  private final GroupControl.Factory groupControlFactory;
  private final GitRepositoryManager repoManager;
  private final PermissionBackend permissionBackend;
  private final ProjectNode.Factory projectNodeFactory;
  private final WebLinks webLinks;

  @Deprecated
  @Option(name = "--format", usage = "(deprecated) output format")
  private OutputFormat format = OutputFormat.TEXT;

  @Option(
      name = "--show-branch",
      aliases = {"-b"},
      usage = "displays the sha of each project in the specified branch")
  public void addShowBranch(String branch) {
    showBranch.add(branch);
  }

  @Option(
      name = "--tree",
      aliases = {"-t"},
      usage =
          "displays project inheritance in a tree-like format\n"
              + "this option does not work together with the show-branch option")
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
      usage = "include description of project in list")
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
      usage = "maximum number of projects to list")
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(
      name = "--start",
      aliases = {"-S"},
      metaVar = "CNT",
      usage = "number of projects to skip")
  public void setStart(int start) {
    this.start = start;
  }

  @Option(
      name = "--prefix",
      aliases = {"-p"},
      metaVar = "PREFIX",
      usage = "match project prefix")
  public void setMatchPrefix(String matchPrefix) {
    this.matchPrefix = matchPrefix;
  }

  @Option(
      name = "--match",
      aliases = {"-m"},
      metaVar = "MATCH",
      usage = "match project substring")
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
      usage = "displays only projects on which access rights for this group are directly assigned")
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
      PermissionBackend permissionBackend,
      ProjectNode.Factory projectNodeFactory,
      WebLinks webLinks) {
    this.currentUser = currentUser;
    this.projectCache = projectCache;
    this.groupsCollection = groupsCollection;
    this.groupControlFactory = groupControlFactory;
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
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
  public Object apply(TopLevelResource resource)
      throws BadRequestException, PermissionBackendException {
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

  public SortedMap<String, ProjectInfo> display(@Nullable OutputStream displayOutputStream)
      throws BadRequestException {
    if (groupUuid != null) {
      try {
        if (!groupControlFactory.controlFor(groupUuid).isVisible()) {
          return Collections.emptySortedMap();
        }
      } catch (NoSuchGroupException ex) {
        return Collections.emptySortedMap();
      }
    }

    PrintWriter stdout;
    if (displayOutputStream != null) {
      stdout =
          new PrintWriter(new BufferedWriter(new OutputStreamWriter(displayOutputStream, UTF_8)));
    } else {
      stdout = null;
    }

    if (type == FilterType.PARENT_CANDIDATES) {
      // Historically, PARENT_CANDIDATES implied showDescription.
      showDescription = true;
    }

    TreeMap<String, ProjectInfo> output = new TreeMap<>();
    Map<String, String> hiddenNames = new HashMap<>();
    Map<Project.NameKey, Boolean> accessibleParents = new HashMap<>();
    PermissionBackend.WithUser perm = permissionBackend.user(currentUser);
    final TreeMap<Project.NameKey, ProjectNode> treeMap = new TreeMap<>();
    try {

      Stream<ProjectState> projectStates =
          filter(perm)
              .map(projectCache::get)
              .filter(ps -> !isHidden(ps) && isVisibleToCurrentUser(ps));

      if (showTree && !format.isJson()) {
        addProjectsToTreeMap(treeMap, projectStates);
      } else {
        projectStates = projectStates.filter(this::shouldDisplayProject).skip(start);
        if (limit > 0) {
          projectStates = projectStates.limit(limit);
        }

        projectStates.forEach(
            e -> {
              Project.NameKey projectName = e.getNameKey();
              ProjectInfo info = new ProjectInfo();

              info.name = projectName.get();

              if (showTree && format.isJson()) {
                addParentProjectInfo(hiddenNames, accessibleParents, perm, e, info);
              }

              if (showDescription) {
                info.description = Strings.emptyToNull(e.getProject().getDescription());
              }

              info.state = e.getProject().getState();

              if (!showBranch.isEmpty()) {
                addProjectBranchesInfo(e, info);
              }

              if (type != FilterType.PARENT_CANDIDATES) {
                List<WebLinkInfo> links = webLinks.getProjectLinks(projectName.get());
                info.webLinks = links.isEmpty() ? null : links;
              }

              if (stdout == null || format.isJson()) {
                output.put(info.name, info);
              } else {

                if (!showBranch.isEmpty()) {
                  printProjectBranches(stdout, info);
                }
                stdout.print(info.name);

                if (info.description != null) {
                  // We still want to list every project as one-liners, hence escaping \n.
                  stdout.print(" - " + StringUtil.escapeString(info.description));
                }
                stdout.print('\n');
              }
            });
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

  private void addProjectsToTreeMap(
      TreeMap<Project.NameKey, ProjectNode> treeMap, Stream<ProjectState> projectStates) {
    projectStates.forEach(
        ps ->
            treeMap.put(
                ps.getNameKey(),
                projectNodeFactory.create(ps.controlFor(currentUser).getProject(), true)));
  }

  private void printProjectBranches(PrintWriter stdout, ProjectInfo info) {
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

  private boolean shouldDisplayProject(ProjectState e) {
    if (!showBranch.isEmpty()) {
      if (!gitRepoHasValidRefs(e) || !matchesGitFilterType(e)) {
        return false;
      }
    } else if (!showTree && type.useMatch()) {
      if (!matchesGitFilterType(e)) {
        return false;
      }
    }

    return true;
  }

  private void addParentProjectInfo(
      Map<String, String> hiddenNames,
      Map<Project.NameKey, Boolean> accessibleParents,
      PermissionBackend.WithUser perm,
      ProjectState e,
      ProjectInfo info) {
    ProjectState parent = Iterables.getFirst(e.parents(), null);
    if (parent != null) {
      if (isParentAccessible(accessibleParents, perm, parent)) {
        info.parent = parent.getName();
      } else {
        info.parent = hiddenNames.get(parent.getName());
        if (info.parent == null) {
          info.parent = "?-" + (hiddenNames.size() + 1);
          hiddenNames.put(parent.getName(), info.parent);
        }
      }
    }
  }

  private void addProjectBranchesInfo(ProjectState projectState, ProjectInfo info) {
    Project.NameKey projectName = projectState.getNameKey();
    try (Repository git = repoManager.openRepository(projectName)) {

      List<Ref> refs = getBranchRefs(projectName, projectState.controlFor(currentUser));

      for (int i = 0; i < showBranch.size(); i++) {
        Ref ref = refs.get(i);
        if (ref != null && ref.getObjectId() != null) {
          if (info.branches == null) {
            info.branches = new LinkedHashMap<>();
          }
          info.branches.put(showBranch.get(i), ref.getObjectId().name());
        }
      }
    } catch (IOException e) {
      log.warn("Unexpected error reading %s", projectName, e);
    }
  }

  private boolean gitRepoHasValidRefs(ProjectState projectState) {
    Project.NameKey projectName = projectState.getNameKey();
    try (Repository git = repoManager.openRepository(projectName)) {
      List<Ref> refs = getBranchRefs(projectName, projectState.controlFor(currentUser));
      return hasValidRef(refs);
    } catch (IOException e) {
      log.warn("Unexpected error reading %s", projectName, e);
      return false;
    }
  }

  private boolean matchesGitFilterType(ProjectState projectState) {
    Project.NameKey projectName = projectState.getNameKey();
    try (Repository git = repoManager.openRepository(projectName)) {
      return type.matches(git);
    } catch (IOException e) {
      log.warn("Unexpected error reading %s", projectName, e);
      return false;
    }
  }

  private boolean isHidden(ProjectState e) {
    return (e == null || (!all && e.getProject().getState() == HIDDEN));
  }

  private boolean isVisibleToCurrentUser(ProjectState e) {
    return (groupUuid == null
        || e.controlFor(currentUser)
            .getProjectState()
            .getLocalGroups()
            .contains(GroupReference.forGroup(groupsCollection.parseId(groupUuid.get()))));
  }

  private Stream<Project.NameKey> filter(PermissionBackend.WithUser perm)
      throws BadRequestException {
    Stream<Project.NameKey> matches = StreamSupport.stream(scan().spliterator(), false).sorted();
    if (type == FilterType.PARENT_CANDIDATES) {
      matches = parentsOf(matches);
    }
    return matches.filter(p -> perm.project(p).testOrFalse(ProjectPermission.ACCESS));
  }

  private Stream<Project.NameKey> parentsOf(Stream<Project.NameKey> matches) {
    return matches.filter(
        p -> {
          ProjectState ps = projectCache.get(p);
          if (ps != null) {
            Project.NameKey parent = ps.getProject().getParent();
            if (parent != null) {
              if (projectCache.get(parent) != null) {
                return true;
              }
              log.warn("parent project {} of project {} not found", parent.get(), ps.getName());
            }
          }
          return false;
        });
  }

  private boolean isParentAccessible(
      Map<Project.NameKey, Boolean> checked, PermissionBackend.WithUser perm, ProjectState p) {
    Project.NameKey name = p.getNameKey();
    Boolean b = checked.get(name);
    if (b == null) {
      b = perm.project(name).testOrFalse(ProjectPermission.ACCESS);
      checked.put(name, b);
    }
    return b;
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
      final PrintWriter stdout, TreeMap<Project.NameKey, ProjectNode> treeMap) {
    final SortedSet<ProjectNode> sortedNodes = new TreeSet<>();

    // Builds the inheritance tree using a list.
    //
    for (ProjectNode key : treeMap.values()) {
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
      PermissionBackend.ForProject perm = permissionBackend.user(currentUser).project(projectName);
      for (int i = 0; i < showBranch.size(); i++) {
        Ref ref = git.findRef(showBranch.get(i));
        if (all && projectControl.isOwner()) {
          result[i] = ref;
        } else if (ref != null && ref.getObjectId() != null) {
          try {
            perm.ref(ref.getLeaf().getName()).check(RefPermission.READ);
            result[i] = ref;
          } catch (AuthException e) {
            continue;
          }
        }
      }
    } catch (IOException | PermissionBackendException e) {
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
