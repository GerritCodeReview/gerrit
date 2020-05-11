// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmoduleSubscription;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.submit.MergeOpRepoManager.OpenRepo;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateListener;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.update.RepoOnlyOp;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec;

public class SubmoduleOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Only used for branches without code review changes */
  public static class GitlinkOp implements RepoOnlyOp {
    private final BranchNameKey branch;
    private final BranchTips currentBranchTips;
    private final SubmoduleCommits commitHelper;
    private final List<SubmoduleSubscription> branchTargets;

    GitlinkOp(
        BranchNameKey branch,
        BranchTips branchTips,
        SubmoduleCommits commitHelper,
        List<SubmoduleSubscription> branchTargets) {
      this.branch = branch;
      this.currentBranchTips = branchTips;
      this.commitHelper = commitHelper;
      this.branchTargets = branchTargets;
    }

    @Override
    public void updateRepo(RepoContext ctx) throws Exception {
      Optional<CodeReviewCommit> commit = commitHelper.composeGitlinksCommit(branch, branchTargets);
      if (commit.isPresent()) {
        CodeReviewCommit c = commit.get();
        ctx.addRefUpdate(c.getParent(0), c, branch.branch());
        currentBranchTips.put(branch, c);
      }
    }
  }

  @Singleton
  public static class Factory {
    private final GitModules.Factory gitmodulesFactory;
    private final Provider<PersonIdent> serverIdent;
    private final Config cfg;
    private final ProjectCache projectCache;

    @Inject
    Factory(
        GitModules.Factory gitmodulesFactory,
        @GerritPersonIdent Provider<PersonIdent> serverIdent,
        @GerritServerConfig Config cfg,
        ProjectCache projectCache) {
      this.gitmodulesFactory = gitmodulesFactory;
      this.serverIdent = serverIdent;
      this.cfg = cfg;
      this.projectCache = projectCache;
    }

    public SubmoduleOp create(Set<BranchNameKey> updatedBranches, MergeOpRepoManager orm)
        throws SubmoduleConflictException {
      return new SubmoduleOp(
          gitmodulesFactory, serverIdent.get(), cfg, projectCache, updatedBranches, orm);
    }
  }

  private final GitModules.Factory gitmodulesFactory;
  private final ProjectCache projectCache;
  private final boolean enableSuperProjectSubscriptions;
  private final MergeOpRepoManager orm;
  private final Map<BranchNameKey, GitModules> branchGitModules;

  /** Branches updated as part of the enclosing submit or push batch. */
  private final ImmutableSet<BranchNameKey> updatedBranches;

  /**
   * Branches in a superproject that contain submodule subscriptions, plus branches in submodules
   * which are subscribed to by some superproject.
   */
  private final Set<BranchNameKey> affectedBranches;

  /** Copy of {@link #affectedBranches}, sorted by submodule traversal order. */
  private final ImmutableSet<BranchNameKey> sortedBranches;

  /** Multimap of superproject branch to submodule subscriptions contained in that branch. */
  private final SetMultimap<BranchNameKey, SubmoduleSubscription> targets;

  private final BranchTips branchTips = new BranchTips();
  /**
   * Multimap of superproject name to all branch names within that superproject which have submodule
   * subscriptions.
   */
  private final SetMultimap<Project.NameKey, BranchNameKey> branchesByProject;

  /** All branches subscribed by other projects. */
  private final Set<BranchNameKey> subscribedBranches;

  private final SubmoduleCommits submoduleCommits;

  private SubmoduleOp(
      GitModules.Factory gitmodulesFactory,
      PersonIdent myIdent,
      Config cfg,
      ProjectCache projectCache,
      Set<BranchNameKey> updatedBranches,
      MergeOpRepoManager orm)
      throws SubmoduleConflictException {
    this.gitmodulesFactory = gitmodulesFactory;
    this.projectCache = projectCache;
    this.enableSuperProjectSubscriptions =
        cfg.getBoolean("submodule", "enableSuperProjectSubscriptions", true);
    this.orm = orm;
    this.updatedBranches = ImmutableSet.copyOf(updatedBranches);
    this.targets = MultimapBuilder.hashKeys().hashSetValues().build();
    this.affectedBranches = new HashSet<>();
    this.branchGitModules = new HashMap<>();
    this.branchesByProject = MultimapBuilder.hashKeys().hashSetValues().build();
    this.subscribedBranches = new HashSet<>();
    this.sortedBranches = calculateSubscriptionMaps();
    this.submoduleCommits = new SubmoduleCommits(orm, myIdent, cfg, branchTips);
  }

  /**
   * Calculate the internal maps used by the operation.
   *
   * <p>In addition to the return value, the following fields are populated as a side effect:
   *
   * <ul>
   *   <li>{@link #affectedBranches}
   *   <li>{@link #targets}
   *   <li>{@link #branchesByProject}
   *   <li>{@link #subscribedBranches}
   * </ul>
   *
   * @return the ordered set to be stored in {@link #sortedBranches}.
   */
  // TODO(dborowitz): This setup process is hard to follow, in large part due to the accumulation of
  // mutable maps, which makes this whole class difficult to understand.
  //
  // A cleaner architecture for this process might be:
  //   1. Separate out the code to parse submodule subscriptions and build up an in-memory data
  //      structure representing the subscription graph, using a separate class with a properly-
  //      documented interface.
  //   2. Walk the graph to produce a work plan. This would be a list of items indicating: create a
  //      commit in project X reading branch tips for submodules S1..Sn and updating gitlinks in X.
  //   3. Execute the work plan, i.e. convert the items into BatchUpdate.Ops and add them to the
  //      relevant updates.
  //
  // In addition to improving readability, this approach has the advantage of making (1) and (2)
  // testable using small tests.
  private ImmutableSet<BranchNameKey> calculateSubscriptionMaps()
      throws SubmoduleConflictException {
    if (!enableSuperProjectSubscriptions) {
      logger.atFine().log("Updating superprojects disabled");
      return null;
    }

    logger.atFine().log("Calculating superprojects - submodules map");
    LinkedHashSet<BranchNameKey> allVisited = new LinkedHashSet<>();
    for (BranchNameKey updatedBranch : updatedBranches) {
      if (allVisited.contains(updatedBranch)) {
        continue;
      }

      searchForSuperprojects(updatedBranch, new LinkedHashSet<>(), allVisited);
    }

    // Since the searchForSuperprojects will add all branches (related or
    // unrelated) and ensure the superproject's branches get added first before
    // a submodule branch. Need remove all unrelated branches and reverse
    // the order.
    allVisited.retainAll(affectedBranches);
    reverse(allVisited);
    return ImmutableSet.copyOf(allVisited);
  }

  private void searchForSuperprojects(
      BranchNameKey current,
      LinkedHashSet<BranchNameKey> currentVisited,
      LinkedHashSet<BranchNameKey> allVisited)
      throws SubmoduleConflictException {
    logger.atFine().log("Now processing %s", current);

    if (currentVisited.contains(current)) {
      throw new SubmoduleConflictException(
          "Branch level circular subscriptions detected:  "
              + printCircularPath(currentVisited, current));
    }

    if (allVisited.contains(current)) {
      return;
    }

    currentVisited.add(current);
    try {
      Collection<SubmoduleSubscription> subscriptions =
          superProjectSubscriptionsForSubmoduleBranch(current);
      for (SubmoduleSubscription sub : subscriptions) {
        BranchNameKey superBranch = sub.getSuperProject();
        searchForSuperprojects(superBranch, currentVisited, allVisited);
        targets.put(superBranch, sub);
        branchesByProject.put(superBranch.project(), superBranch);
        affectedBranches.add(superBranch);
        affectedBranches.add(sub.getSubmodule());
        subscribedBranches.add(sub.getSubmodule());
      }
    } catch (IOException e) {
      throw new StorageException("Cannot find superprojects for " + current, e);
    }
    currentVisited.remove(current);
    allVisited.add(current);
  }

  private static <T> void reverse(LinkedHashSet<T> set) {
    if (set == null) {
      return;
    }

    Deque<T> q = new ArrayDeque<>(set);
    set.clear();

    while (!q.isEmpty()) {
      set.add(q.removeLast());
    }
  }

  private <T> String printCircularPath(LinkedHashSet<T> p, T target) {
    StringBuilder sb = new StringBuilder();
    sb.append(target);
    ArrayList<T> reverseP = new ArrayList<>(p);
    Collections.reverse(reverseP);
    for (T t : reverseP) {
      sb.append("->");
      sb.append(t);
      if (t.equals(target)) {
        break;
      }
    }
    return sb.toString();
  }

  private Collection<BranchNameKey> getDestinationBranches(BranchNameKey src, SubscribeSection s)
      throws IOException {
    Collection<BranchNameKey> ret = new HashSet<>();
    logger.atFine().log("Inspecting SubscribeSection %s", s);
    for (RefSpec r : s.getMatchingRefSpecs()) {
      logger.atFine().log("Inspecting [matching] ref %s", r);
      if (!r.matchSource(src.branch())) {
        continue;
      }
      if (r.isWildcard()) {
        // refs/heads/*[:refs/somewhere/*]
        ret.add(
            BranchNameKey.create(
                s.getProject(), r.expandFromSource(src.branch()).getDestination()));
      } else {
        // e.g. refs/heads/master[:refs/heads/stable]
        String dest = r.getDestination();
        if (dest == null) {
          dest = r.getSource();
        }
        ret.add(BranchNameKey.create(s.getProject(), dest));
      }
    }

    for (RefSpec r : s.getMultiMatchRefSpecs()) {
      logger.atFine().log("Inspecting [all] ref %s", r);
      if (!r.matchSource(src.branch())) {
        continue;
      }
      OpenRepo or;
      try {
        or = orm.getRepo(s.getProject());
      } catch (NoSuchProjectException e) {
        // A project listed a non existent project to be allowed
        // to subscribe to it. Allow this for now, i.e. no exception is
        // thrown.
        continue;
      }

      for (Ref ref : or.repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_HEADS)) {
        if (r.getDestination() != null && !r.matchDestination(ref.getName())) {
          continue;
        }
        BranchNameKey b = BranchNameKey.create(s.getProject(), ref.getName());
        if (!ret.contains(b)) {
          ret.add(b);
        }
      }
    }
    logger.atFine().log("Returning possible branches: %s for project %s", ret, s.getProject());
    return ret;
  }

  private Collection<SubmoduleSubscription> superProjectSubscriptionsForSubmoduleBranch(
      BranchNameKey srcBranch) throws IOException {
    logger.atFine().log("Calculating possible superprojects for %s", srcBranch);
    Collection<SubmoduleSubscription> ret = new ArrayList<>();
    Project.NameKey srcProject = srcBranch.project();
    for (SubscribeSection s :
        projectCache
            .get(srcProject)
            .orElseThrow(illegalState(srcProject))
            .getSubscribeSections(srcBranch)) {
      logger.atFine().log("Checking subscribe section %s", s);
      Collection<BranchNameKey> branches = getDestinationBranches(srcBranch, s);
      for (BranchNameKey targetBranch : branches) {
        Project.NameKey targetProject = targetBranch.project();
        try {
          OpenRepo or = orm.getRepo(targetProject);
          ObjectId id = or.repo.resolve(targetBranch.branch());
          if (id == null) {
            logger.atFine().log("The branch %s doesn't exist.", targetBranch);
            continue;
          }
        } catch (NoSuchProjectException e) {
          logger.atFine().log("The project %s doesn't exist", targetProject);
          continue;
        }

        GitModules m = branchGitModules.get(targetBranch);
        if (m == null) {
          m = gitmodulesFactory.create(targetBranch, orm);
          branchGitModules.put(targetBranch, m);
        }
        ret.addAll(m.subscribedTo(srcBranch));
      }
    }
    logger.atFine().log("Calculated superprojects for %s are %s", srcBranch, ret);
    return ret;
  }

  @UsedAt(UsedAt.Project.PLUGIN_DELETE_PROJECT)
  public boolean hasSuperproject(BranchNameKey branch) {
    return subscribedBranches.contains(branch);
  }

  public void updateSuperProjects() throws RestApiException {
    ImmutableSet<Project.NameKey> projects = getProjectsInOrder();
    if (projects == null) {
      return;
    }

    LinkedHashSet<Project.NameKey> superProjects = new LinkedHashSet<>();
    try {
      for (Project.NameKey project : projects) {
        // only need superprojects
        if (branchesByProject.containsKey(project)) {
          superProjects.add(project);
          // get a new BatchUpdate for the super project
          OpenRepo or = orm.getRepo(project);
          for (BranchNameKey branch : branchesByProject.get(project)) {
            addOp(or.getUpdate(), branch);
          }
        }
      }
      BatchUpdate.execute(orm.batchUpdates(superProjects), BatchUpdateListener.NONE, false);
    } catch (UpdateException | IOException | NoSuchProjectException e) {
      throw new StorageException("Cannot update gitlinks", e);
    }
  }

  CodeReviewCommit amendGitlinksCommit(BranchNameKey branch, CodeReviewCommit commit)
      throws SubmoduleConflictException, IOException {
    return submoduleCommits.amendGitlinksCommit(branch, commit, getSubscriptions(branch));
  }

  ImmutableSet<Project.NameKey> getProjectsInOrder() throws SubmoduleConflictException {
    LinkedHashSet<Project.NameKey> projects = new LinkedHashSet<>();
    for (Project.NameKey project : branchesByProject.keySet()) {
      addAllSubmoduleProjects(project, new LinkedHashSet<>(), projects);
    }

    for (BranchNameKey branch : updatedBranches) {
      projects.add(branch.project());
    }
    return ImmutableSet.copyOf(projects);
  }

  private void addAllSubmoduleProjects(
      Project.NameKey project,
      LinkedHashSet<Project.NameKey> current,
      LinkedHashSet<Project.NameKey> projects)
      throws SubmoduleConflictException {
    if (current.contains(project)) {
      throw new SubmoduleConflictException(
          "Project level circular subscriptions detected:  " + printCircularPath(current, project));
    }

    if (projects.contains(project)) {
      return;
    }

    current.add(project);
    Set<Project.NameKey> subprojects = new HashSet<>();
    for (BranchNameKey branch : branchesByProject.get(project)) {
      Collection<SubmoduleSubscription> subscriptions = targets.get(branch);
      for (SubmoduleSubscription s : subscriptions) {
        subprojects.add(s.getSubmodule().project());
      }
    }

    for (Project.NameKey p : subprojects) {
      addAllSubmoduleProjects(p, current, projects);
    }

    current.remove(project);
    projects.add(project);
  }

  ImmutableSet<BranchNameKey> getBranchesInOrder() {
    LinkedHashSet<BranchNameKey> branches = new LinkedHashSet<>();
    if (sortedBranches != null) {
      branches.addAll(sortedBranches);
    }
    branches.addAll(updatedBranches);
    return ImmutableSet.copyOf(branches);
  }

  boolean hasSubscription(BranchNameKey branch) {
    return targets.containsKey(branch);
  }

  void addBranchTip(BranchNameKey branch, CodeReviewCommit tip) {
    branchTips.put(branch, tip);
  }

  void addOp(BatchUpdate bu, BranchNameKey branch) {
    bu.addRepoOnlyOp(new GitlinkOp(branch, branchTips, submoduleCommits, getSubscriptions(branch)));
  }

  private List<SubmoduleSubscription> getSubscriptions(BranchNameKey branch) {
    return targets.get(branch).stream()
        .sorted(comparing(SubmoduleSubscription::getPath))
        .collect(toList());
  }
}
