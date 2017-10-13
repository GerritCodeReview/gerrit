// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.ProjectApi.ListRefsRequest;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.project.ProjectField;
import com.google.gerrit.server.index.project.ProjectIndex;
import com.google.gerrit.server.index.project.ProjectIndexCollection;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.query.project.InternalProjectQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

public class ListBranches implements RestReadView<ProjectResource> {
  private final GitRepositoryManager repoManager;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final DynamicMap<RestView<BranchResource>> branchViews;
  private final UiActions uiActions;
  private final WebLinks webLinks;
  private final ProjectIndexCollection projectIndexes;
  private final Provider<InternalProjectQuery> projectQuery;

  @Option(
    name = "--limit",
    aliases = {"-n"},
    metaVar = "CNT",
    usage = "maximum number of branches to list"
  )
  public void setLimit(int limit) {
    this.limit = limit;
  }

  @Option(
    name = "--start",
    aliases = {"-S", "-s"},
    metaVar = "CNT",
    usage = "number of branches to skip"
  )
  public void setStart(int start) {
    this.start = start;
  }

  @Option(
    name = "--match",
    aliases = {"-m"},
    metaVar = "MATCH",
    usage = "match branches substring"
  )
  public void setMatchSubstring(String matchSubstring) {
    this.matchSubstring = matchSubstring;
  }

  @Option(
    name = "--regex",
    aliases = {"-r"},
    metaVar = "REGEX",
    usage = "match branches regex"
  )
  public void setMatchRegex(String matchRegex) {
    this.matchRegex = matchRegex;
  }

  @Option(
    name = "--names-only",
    metaVar = "OPTION",
    usage = "whether only branch names should be returned (faster)."
  )
  public void setNamesOnly(boolean namesOnly) {
    this.namesOnly = namesOnly;
  }

  private int limit;
  private int start;
  private String matchSubstring;
  private String matchRegex;
  private boolean namesOnly;

  @Inject
  public ListBranches(
      GitRepositoryManager repoManager,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> user,
      DynamicMap<RestView<BranchResource>> branchViews,
      UiActions uiActions,
      WebLinks webLinks,
      ProjectIndexCollection indexes,
      Provider<InternalProjectQuery> projectQuery) {
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.branchViews = branchViews;
    this.uiActions = uiActions;
    this.webLinks = webLinks;
    this.projectIndexes = indexes;
    this.projectQuery = projectQuery;
  }

  public ListBranches request(ListRefsRequest<BranchInfo> request) {
    this.setLimit(request.getLimit());
    this.setStart(request.getStart());
    this.setMatchSubstring(request.getSubstring());
    this.setMatchRegex(request.getRegex());
    this.setNamesOnly(request.getNamesOnly());
    return this;
  }

  @Override
  public List<BranchInfo> apply(ProjectResource rsrc)
      throws ResourceNotFoundException, IOException, BadRequestException,
          PermissionBackendException, OrmException {
    return new RefFilter<BranchInfo>(Constants.R_HEADS)
        .subString(matchSubstring)
        .regex(matchRegex)
        .start(start)
        .limit(limit)
        .filter(allBranches(rsrc));
  }

  BranchInfo toBranchInfo(BranchResource rsrc)
      throws IOException, ResourceNotFoundException, PermissionBackendException {
    try (Repository db = repoManager.openRepository(rsrc.getNameKey())) {
      Ref r = db.exactRef(rsrc.getRef());
      if (r == null) {
        throw new ResourceNotFoundException();
      }
      return toBranchInfo(rsrc, ImmutableList.of(r)).get(0);
    } catch (RepositoryNotFoundException noRepo) {
      throw new ResourceNotFoundException();
    }
  }

  private List<BranchInfo> allBranches(ProjectResource rsrc)
      throws OrmException, IOException, PermissionBackendException, ResourceNotFoundException {
    if (namesOnly) {
      ProjectIndex searchIndex = projectIndexes.getSearchIndex();
      if (searchIndex != null && searchIndex.getSchema().hasField(ProjectField.BRANCH)) {
        return allBranchesFromIndex(rsrc);
      }
    }
    return allBranchesFromGit(rsrc);
  }

  private List<BranchInfo> allBranchesFromIndex(ProjectResource rsrc)
      throws OrmException, IOException, ResourceNotFoundException, PermissionBackendException {
    ProjectData project = projectQuery.get().oneByName(rsrc.getNameKey());
    if (project == null) {
      throw new ResourceNotFoundException();
    }

    PermissionBackend.ForProject perm = permissionBackend.user(user).project(rsrc.getNameKey());
    List<BranchInfo> branchInfos = new ArrayList<>();
    for (Branch.NameKey branchName : project.getBranches()) {
      if (perm.ref(branchName.get()).test(RefPermission.READ)) {
        BranchInfo info = new BranchInfo();
        info.ref = branchName.get();
        branchInfos.add(info);
      }
    }
    return branchInfos;
  }

  private List<BranchInfo> allBranchesFromGit(ProjectResource rsrc)
      throws IOException, ResourceNotFoundException, PermissionBackendException {
    try (Repository db = repoManager.openRepository(rsrc.getNameKey())) {
      return toBranchInfo(rsrc, allRefs(repoManager, rsrc.getNameKey()));
    } catch (RepositoryNotFoundException noGitRepository) {
      throw new ResourceNotFoundException();
    }
  }

  public static List<Ref> allRefs(GitRepositoryManager repoManager, Project.NameKey projectName)
      throws IOException {
    try (Repository db = repoManager.openRepository(projectName)) {
      Collection<Ref> heads = db.getRefDatabase().getRefs(Constants.R_HEADS).values();
      List<Ref> refs = new ArrayList<>(heads.size() + 3);
      refs.addAll(heads);
      refs.addAll(
          db.getRefDatabase()
              .exactRef(Constants.HEAD, RefNames.REFS_CONFIG, RefNames.REFS_USERS_DEFAULT)
              .values());
      return refs;
    }
  }

  private List<BranchInfo> toBranchInfo(ProjectResource rsrc, List<Ref> refs)
      throws PermissionBackendException {
    Set<String> targets = Sets.newHashSetWithExpectedSize(1);
    for (Ref ref : refs) {
      if (ref.isSymbolic()) {
        targets.add(ref.getTarget().getName());
      }
    }

    PermissionBackend.ForProject perm = permissionBackend.user(user).project(rsrc.getNameKey());
    List<BranchInfo> branches = new ArrayList<>(refs.size());
    for (Ref ref : refs) {
      if (ref.isSymbolic()) {
        // A symbolic reference to another branch, instead of
        // showing the resolved value, show the name it references.
        //
        String target = ref.getTarget().getName();
        if (!perm.ref(target).test(RefPermission.READ)) {
          continue;
        }
        if (target.startsWith(Constants.R_HEADS)) {
          target = target.substring(Constants.R_HEADS.length());
        }

        BranchInfo b = new BranchInfo();
        b.ref = ref.getName();
        branches.add(b);

        if (namesOnly) {
          continue;
        }

        b.revision = target;

        if (!Constants.HEAD.equals(ref.getName())) {
          b.canDelete = perm.ref(ref.getName()).testOrFalse(RefPermission.DELETE) ? true : null;
        }
        continue;
      }

      if (perm.ref(ref.getName()).test(RefPermission.READ)) {
        branches.add(
            createBranchInfo(
                perm.ref(ref.getName()), ref, rsrc.getProjectState(), rsrc.getUser(), targets));
      }
    }
    Collections.sort(branches, new BranchComparator());
    return branches;
  }

  private static class BranchComparator implements Comparator<BranchInfo> {
    @Override
    public int compare(BranchInfo a, BranchInfo b) {
      return ComparisonChain.start()
          .compareTrueFirst(isHead(a), isHead(b))
          .compareTrueFirst(isConfig(a), isConfig(b))
          .compare(a.ref, b.ref)
          .result();
    }

    private static boolean isHead(BranchInfo i) {
      return Constants.HEAD.equals(i.ref);
    }

    private static boolean isConfig(BranchInfo i) {
      return RefNames.REFS_CONFIG.equals(i.ref);
    }
  }

  private BranchInfo createBranchInfo(
      PermissionBackend.ForRef perm,
      Ref ref,
      ProjectState projectState,
      CurrentUser user,
      Set<String> targets) {
    BranchInfo info = new BranchInfo();
    info.ref = ref.getName();

    if (namesOnly) {
      return info;
    }

    info.revision = ref.getObjectId() != null ? ref.getObjectId().name() : null;
    info.canDelete =
        !targets.contains(ref.getName()) && perm.testOrFalse(RefPermission.DELETE) ? true : null;

    BranchResource rsrc = new BranchResource(projectState, user, ref);
    for (UiAction.Description d : uiActions.from(branchViews, rsrc)) {
      if (info.actions == null) {
        info.actions = new TreeMap<>();
      }
      info.actions.put(d.getId(), new ActionInfo(d));
    }

    List<WebLinkInfo> links = webLinks.getBranchLinks(projectState.getName(), ref.getName());
    info.webLinks = links.isEmpty() ? null : links;
    return info;
  }
}
