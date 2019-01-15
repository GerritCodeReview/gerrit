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

package com.google.gerrit.server.restapi.project;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Ordering.natural;
import static com.google.gerrit.extensions.client.ProjectState.HIDDEN;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.ioutil.RegexListSearcher;
import com.google.gerrit.server.ioutil.StringUtil;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.util.TreeFormatter;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
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
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

/** List projects visible to the calling user. */
public class ListProjects implements RestReadView<TopLevelResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
  private final GroupResolver groupResolver;
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
      name = "--state",
      aliases = {"-s"},
      usage = "filter by project state")
  public void setState(com.google.gerrit.extensions.client.ProjectState state) {
    this.state = state;
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
  private com.google.gerrit.extensions.client.ProjectState state;
  private int limit;
  private int start;
  private String matchPrefix;
  private String matchSubstring;
  private String matchRegex;
  private AccountGroup.UUID groupUuid;
  private final Provider<QueryProjects> queryProjectsProvider;
  private final boolean listProjectsFromIndex;

  @Inject
  protected ListProjects(
      CurrentUser currentUser,
      ProjectCache projectCache,
      GroupResolver groupResolver,
      GroupControl.Factory groupControlFactory,
      GitRepositoryManager repoManager,
      PermissionBackend permissionBackend,
      ProjectNode.Factory projectNodeFactory,
      WebLinks webLinks,
      Provider<QueryProjects> queryProjectsProvider,
      @GerritServerConfig Config config) {
    this.currentUser = currentUser;
    this.projectCache = projectCache;
    this.groupResolver = groupResolver;
    this.groupControlFactory = groupControlFactory;
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
    this.projectNodeFactory = projectNodeFactory;
    this.webLinks = webLinks;
    this.queryProjectsProvider = queryProjectsProvider;
    this.listProjectsFromIndex = config.getBoolean("gerrit", "listProjectsFromIndex", false);
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
      displayToStream(buf);
      return BinaryResult.create(buf.toByteArray())
          .setContentType("text/plain")
          .setCharacterEncoding(UTF_8);
    }
    return apply();
  }

  public SortedMap<String, ProjectInfo> apply()
      throws BadRequestException, PermissionBackendException {
    Optional<String> projectQuery = expressAsProjectsQuery();
    if (projectQuery.isPresent()) {
      return applyAsQuery(projectQuery.get());
    }

    format = OutputFormat.JSON;
    return display(null);
  }

  private Optional<String> expressAsProjectsQuery() {
    return listProjectsFromIndex
            && !all
            && state != HIDDEN
            && isNullOrEmpty(matchPrefix)
            && isNullOrEmpty(matchRegex)
            && isNullOrEmpty(matchSubstring) // TODO: see Issue 10446
            && type == FilterType.ALL
            && showBranch.isEmpty()
            && !showTree
        ? Optional.of(stateToQuery())
        : Optional.empty();
  }

  private String stateToQuery() {
    List<String> queries = new ArrayList<>();
    if (state == null) {
      queries.add("(state:active OR state:read-only)");
    } else {
      queries.add(String.format("(state:%s)", state.name()));
    }

    return Joiner.on(" AND ").join(queries).toString();
  }

  private SortedMap<String, ProjectInfo> applyAsQuery(String query) throws BadRequestException {
    try {
      return queryProjectsProvider.get().withQuery(query).withStart(start).withLimit(limit).apply()
          .stream()
          .collect(
              ImmutableSortedMap.toImmutableSortedMap(
                  natural(), p -> p.name, p -> showDescription ? p : nullifyDescription(p)));
    } catch (OrmException | MethodNotAllowedException e) {
      logger.atWarning().withCause(e).log(
          "Internal error while processing the query '%s' request", query);
      throw new BadRequestException("Internal error while processing the query request");
    }
  }

  private ProjectInfo nullifyDescription(ProjectInfo p) {
    p.description = null;
    return p;
  }

  private void printQueryResults(String query, PrintWriter out) throws BadRequestException {
    try {
      if (format.isJson()) {
        format.newGson().toJson(applyAsQuery(query), out);
      } else {
        newProjectsNamesStream(query).forEach(out::println);
      }
      out.flush();
    } catch (OrmException | MethodNotAllowedException e) {
      logger.atWarning().withCause(e).log(
          "Internal error while processing the query '%s' request", query);
      throw new BadRequestException("Internal error while processing the query request");
    }
  }

  private Stream<String> newProjectsNamesStream(String query)
      throws OrmException, MethodNotAllowedException, BadRequestException {
    Stream<String> projects =
        queryProjectsProvider.get().withQuery(query).apply().stream().map(p -> p.name).skip(start);
    if (limit > 0) {
      projects = projects.limit(limit);
    }

    return projects;
  }

  public void displayToStream(OutputStream displayOutputStream)
      throws BadRequestException, PermissionBackendException {
    PrintWriter stdout =
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(displayOutputStream, UTF_8)));
    Optional<String> projectsQuery = expressAsProjectsQuery();

    if (projectsQuery.isPresent()) {
      printQueryResults(projectsQuery.get(), stdout);
    } else {
      display(stdout);
    }
  }

  @Nullable
  public SortedMap<String, ProjectInfo> display(@Nullable PrintWriter stdout)
      throws BadRequestException, PermissionBackendException {
    if (all && state != null) {
      throw new BadRequestException("'all' and 'state' may not be used together");
    }
    if (groupUuid != null) {
      try {
        if (!groupControlFactory.controlFor(groupUuid).isVisible()) {
          return Collections.emptySortedMap();
        }
      } catch (NoSuchGroupException ex) {
        return Collections.emptySortedMap();
      }
    }

    int foundIndex = 0;
    int found = 0;
    TreeMap<String, ProjectInfo> output = new TreeMap<>();
    Map<String, String> hiddenNames = new HashMap<>();
    Map<Project.NameKey, Boolean> accessibleParents = new HashMap<>();
    PermissionBackend.WithUser perm = permissionBackend.user(currentUser);
    final TreeMap<Project.NameKey, ProjectNode> treeMap = new TreeMap<>();
    try {
      Iterable<ProjectState> projectStatesIt = filter(perm)::iterator;
      for (ProjectState e : projectStatesIt) {
        Project.NameKey projectName = e.getNameKey();
        if (e.getProject().getState() == HIDDEN && !all && state != HIDDEN) {
          // If we can't get it from the cache, pretend it's not present.
          // If all wasn't selected, and it's HIDDEN, pretend it's not present.
          // If state HIDDEN wasn't selected, and it's HIDDEN, pretend it's not present.
          continue;
        }

        if (state != null && e.getProject().getState() != state) {
          continue;
        }

        if (groupUuid != null
            && !e.getLocalGroups()
                .contains(GroupReference.forGroup(groupResolver.parseId(groupUuid.get())))) {
          continue;
        }

        if (showTree && !format.isJson()) {
          treeMap.put(projectName, projectNodeFactory.create(e.getProject(), true));
          continue;
        }

        if (foundIndex++ < start) {
          continue;
        }
        if (limit > 0 && ++found > limit) {
          break;
        }

        ProjectInfo info = new ProjectInfo();
        info.name = projectName.get();
        if (showTree && format.isJson()) {
          addParentProjectInfo(hiddenNames, accessibleParents, perm, e, info);
        }

        if (showDescription) {
          info.description = emptyToNull(e.getProject().getDescription());
        }
        info.state = e.getProject().getState();

        try {
          if (!showBranch.isEmpty()) {
            try (Repository git = repoManager.openRepository(projectName)) {
              if (!type.matches(git)) {
                continue;
              }

              List<Ref> refs = retieveBranchRefs(e);
              if (!hasValidRef(refs)) {
                continue;
              }

              addProjectBranchesInfo(info, refs);
            }
          } else if (!showTree && type.useMatch()) {
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
          logger.atWarning().withCause(err).log("Unexpected error reading %s", projectName);
          continue;
        }

        List<WebLinkInfo> links = webLinks.getProjectLinks(projectName.get());
        info.webLinks = links.isEmpty() ? null : links;

        if (stdout == null || format.isJson()) {
          output.put(info.name, info);
          continue;
        }

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

  private void addProjectBranchesInfo(ProjectInfo info, List<Ref> refs) {
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

  private List<Ref> retieveBranchRefs(ProjectState e) throws PermissionBackendException {
    boolean canReadAllRefs = e.statePermitsRead();
    if (canReadAllRefs) {
      try {
        permissionBackend.user(currentUser).project(e.getNameKey()).check(ProjectPermission.READ);
      } catch (AuthException exp) {
        canReadAllRefs = false;
      }
    }

    return getBranchRefs(e.getNameKey(), canReadAllRefs);
  }

  private void addParentProjectInfo(
      Map<String, String> hiddenNames,
      Map<Project.NameKey, Boolean> accessibleParents,
      PermissionBackend.WithUser perm,
      ProjectState e,
      ProjectInfo info)
      throws PermissionBackendException {
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

  private Stream<ProjectState> filter(PermissionBackend.WithUser perm) throws BadRequestException {
    return StreamSupport.stream(scan().spliterator(), false)
        .map(projectCache::get)
        .filter(Objects::nonNull)
        .filter(p -> permissionCheck(p, perm));
  }

  private boolean permissionCheck(ProjectState state, PermissionBackend.WithUser perm) {
    // Hidden projects(permitsRead = false) should only be accessible by the project owners.
    // READ_CONFIG is checked here because it's only allowed to project owners(ACCESS may also
    // be allowed for other users). Allowing project owners to access here will help them to view
    // and update the config of hidden projects easily.
    return perm.project(state.getNameKey())
        .testOrFalse(
            state.statePermitsRead() ? ProjectPermission.ACCESS : ProjectPermission.READ_CONFIG);
  }

  private boolean isParentAccessible(
      Map<Project.NameKey, Boolean> checked, PermissionBackend.WithUser perm, ProjectState state)
      throws PermissionBackendException {
    Project.NameKey name = state.getNameKey();
    Boolean b = checked.get(name);
    if (b == null) {
      try {
        // Hidden projects(permitsRead = false) should only be accessible by the project owners.
        // READ_CONFIG is checked here because it's only allowed to project owners(ACCESS may also
        // be allowed for other users). Allowing project owners to access here will help them to
        // view
        // and update the config of hidden projects easily.
        ProjectPermission permissionToCheck =
            state.statePermitsRead() ? ProjectPermission.ACCESS : ProjectPermission.READ_CONFIG;
        perm.project(name).check(permissionToCheck);
        b = true;
      } catch (AuthException denied) {
        b = false;
      }
      checked.put(name, b);
    }
    return b;
  }

  private Stream<Project.NameKey> scan() throws BadRequestException {
    if (matchPrefix != null) {
      checkMatchOptions(matchSubstring == null && matchRegex == null);
      return projectCache.byName(matchPrefix).stream();
    } else if (matchSubstring != null) {
      checkMatchOptions(matchPrefix == null && matchRegex == null);
      return projectCache.all().stream()
          .filter(
              p -> p.get().toLowerCase(Locale.US).contains(matchSubstring.toLowerCase(Locale.US)));
    } else if (matchRegex != null) {
      checkMatchOptions(matchPrefix == null && matchSubstring == null);
      RegexListSearcher<Project.NameKey> searcher;
      try {
        searcher = new RegexListSearcher<>(matchRegex, Project.NameKey::get);
      } catch (IllegalArgumentException e) {
        throw new BadRequestException(e.getMessage());
      }
      return searcher.search(ImmutableList.copyOf(projectCache.all()));
    } else {
      return projectCache.all().stream();
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

  private List<Ref> getBranchRefs(Project.NameKey projectName, boolean canReadAllRefs) {
    Ref[] result = new Ref[showBranch.size()];
    try (Repository git = repoManager.openRepository(projectName)) {
      PermissionBackend.ForProject perm = permissionBackend.user(currentUser).project(projectName);
      for (int i = 0; i < showBranch.size(); i++) {
        Ref ref = git.findRef(showBranch.get(i));
        if (all && canReadAllRefs) {
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
