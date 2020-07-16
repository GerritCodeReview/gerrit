// Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmoduleSubscription;
import com.google.gerrit.entities.SubscribeSection;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.submit.MergeOpRepoManager.OpenRepo;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

/**
 * A container which stores subscription relationship. A SubscriptionGraph is calculated every time
 * changes are pushed. Some branches are updated in these changes, and if these branches are
 * subscribed by other projects, SubscriptionGraph would record information about these updated
 * branches and branches/projects affected.
 */
public class SubscriptionGraph {
  /** Branches updated as part of the enclosing submit or push batch. */
  private final ImmutableSet<BranchNameKey> updatedBranches;

  /**
   * All branches affected, including those in superprojects and submodules, sorted by submodule
   * traversal order. To support nested subscriptions, GitLink commits need to be updated in order.
   * The closer to topological "leaf", the earlier a commit should be updated.
   *
   * <p>For example, there are three projects, top level project p1 subscribed to p2, p2 subscribed
   * to bottom level project p3. When submit a change for p3. We need update both p2 and p1. To be
   * more precise, we need update p2 first and then update p1.
   */
  private final ImmutableSet<BranchNameKey> sortedBranches;

  /** Multimap of superproject branch to submodule subscriptions contained in that branch. */
  private final ImmutableSetMultimap<BranchNameKey, SubmoduleSubscription> targets;

  /**
   * Multimap of superproject name to all branch names within that superproject which have submodule
   * subscriptions.
   */
  private final ImmutableSetMultimap<Project.NameKey, BranchNameKey> branchesByProject;

  /** All branches subscribed by other projects. */
  private final ImmutableSet<BranchNameKey> subscribedBranches;

  public SubscriptionGraph(
      Set<BranchNameKey> updatedBranches,
      SetMultimap<BranchNameKey, SubmoduleSubscription> targets,
      SetMultimap<Project.NameKey, BranchNameKey> branchesByProject,
      Set<BranchNameKey> subscribedBranches,
      Set<BranchNameKey> sortedBranches) {
    this.updatedBranches = ImmutableSet.copyOf(updatedBranches);
    this.targets = ImmutableSetMultimap.copyOf(targets);
    this.branchesByProject = ImmutableSetMultimap.copyOf(branchesByProject);
    this.subscribedBranches = ImmutableSet.copyOf(subscribedBranches);
    this.sortedBranches = ImmutableSet.copyOf(sortedBranches);
  }

  /** Returns an empty {@code SubscriptionGraph}. */
  static SubscriptionGraph createEmptyGraph(Set<BranchNameKey> updatedBranches) {
    return new SubscriptionGraph(
        updatedBranches,
        ImmutableSetMultimap.of(),
        ImmutableSetMultimap.of(),
        ImmutableSet.of(),
        ImmutableSet.of());
  }

  /** Get branches updated as part of the enclosing submit or push batch. */
  public ImmutableSet<BranchNameKey> getUpdatedBranches() {
    return updatedBranches;
  }

  /** Get all superprojects affected. */
  public ImmutableSet<Project.NameKey> getAffectedSuperProjects() {
    return branchesByProject.keySet();
  }

  /** See if a {@code project} is a superproject affected. */
  boolean isAffectedSuperProject(Project.NameKey project) {
    return branchesByProject.containsKey(project);
  }

  /**
   * Returns all branches within the superproject {@code project} which have submodule
   * subscriptions.
   */
  public ImmutableSet<BranchNameKey> getAffectedSuperBranches(Project.NameKey project) {
    return branchesByProject.get(project);
  }

  /**
   * Get all affected branches, including the submodule branches and superproject branches, sorted
   * by traversal order.
   *
   * @see SubscriptionGraph#sortedBranches
   */
  public ImmutableSet<BranchNameKey> getSortedSuperprojectAndSubmoduleBranches() {
    return sortedBranches;
  }

  /** Check if a {@code branch} is a submodule of a superproject. */
  public boolean hasSuperproject(BranchNameKey branch) {
    return subscribedBranches.contains(branch);
  }

  /** See if a {@code branch} is a superproject branch affected. */
  public boolean hasSubscription(BranchNameKey branch) {
    return targets.containsKey(branch);
  }

  /** Get all related {@code SubmoduleSubscription}s whose super branch is {@code branch}. */
  public ImmutableSet<SubmoduleSubscription> getSubscriptions(BranchNameKey branch) {
    return targets.get(branch);
  }

  public interface Factory {
    SubscriptionGraph compute(Set<BranchNameKey> updatedBranches, MergeOpRepoManager orm)
        throws SubmoduleConflictException;
  }

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(Factory.class).to(DefaultFactory.class);
    }
  }

  static class DefaultFactory implements Factory {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
    private final ProjectCache projectCache;
    private final GitModules.Factory gitmodulesFactory;

    @Inject
    DefaultFactory(GitModules.Factory gitmodulesFactory, ProjectCache projectCache) {
      this.gitmodulesFactory = gitmodulesFactory;
      this.projectCache = projectCache;
    }

    @Override
    public SubscriptionGraph compute(Set<BranchNameKey> updatedBranches, MergeOpRepoManager orm)
        throws SubmoduleConflictException {
      Map<BranchNameKey, GitModules> branchGitModules = new HashMap<>();
      // All affected branches, including those in superprojects and submodules.
      Set<BranchNameKey> affectedBranches = new HashSet<>();

      // See SubscriptionGraph#targets.
      SetMultimap<BranchNameKey, SubmoduleSubscription> targets =
          MultimapBuilder.hashKeys().hashSetValues().build();

      // See SubscriptionGraph#branchesByProject.
      SetMultimap<Project.NameKey, BranchNameKey> branchesByProject =
          MultimapBuilder.hashKeys().hashSetValues().build();

      // See SubscriptionGraph#subscribedBranches.
      Set<BranchNameKey> subscribedBranches = new HashSet<>();

      Set<BranchNameKey> sortedBranches =
          calculateSubscriptionMaps(
              updatedBranches,
              affectedBranches,
              targets,
              branchesByProject,
              subscribedBranches,
              branchGitModules,
              orm);

      return new SubscriptionGraph(
          updatedBranches, targets, branchesByProject, subscribedBranches, sortedBranches);
    }

    /**
     * Calculate the internal maps used by the operation.
     *
     * <p>In addition to the return value, the following fields are populated as a side effect:
     *
     * <ul>
     *   <li>{@code affectedBranches}
     *   <li>{@code targets}
     *   <li>{@code branchesByProject}
     *   <li>{@code subscribedBranches}
     * </ul>
     *
     * @return the ordered set to be stored in {@link #sortedBranches}.
     */
    private Set<BranchNameKey> calculateSubscriptionMaps(
        Set<BranchNameKey> updatedBranches,
        Set<BranchNameKey> affectedBranches,
        SetMultimap<BranchNameKey, SubmoduleSubscription> targets,
        SetMultimap<Project.NameKey, BranchNameKey> branchesByProject,
        Set<BranchNameKey> subscribedBranches,
        Map<BranchNameKey, GitModules> branchGitModules,
        MergeOpRepoManager orm)
        throws SubmoduleConflictException {
      logger.atFine().log("Calculating superprojects - submodules map");
      LinkedHashSet<BranchNameKey> allVisited = new LinkedHashSet<>();
      for (BranchNameKey updatedBranch : updatedBranches) {
        if (allVisited.contains(updatedBranch)) {
          continue;
        }

        searchForSuperprojects(
            updatedBranch,
            new LinkedHashSet<>(),
            allVisited,
            affectedBranches,
            targets,
            branchesByProject,
            subscribedBranches,
            branchGitModules,
            orm);
      }

      // Since the searchForSuperprojects will add all branches (related or
      // unrelated) and ensure the superproject's branches get added first before
      // a submodule branch. Need remove all unrelated branches and reverse
      // the order.
      allVisited.retainAll(affectedBranches);
      reverse(allVisited);
      return allVisited;
    }

    private void searchForSuperprojects(
        BranchNameKey current,
        LinkedHashSet<BranchNameKey> currentVisited,
        LinkedHashSet<BranchNameKey> allVisited,
        Set<BranchNameKey> affectedBranches,
        SetMultimap<BranchNameKey, SubmoduleSubscription> targets,
        SetMultimap<Project.NameKey, BranchNameKey> branchesByProject,
        Set<BranchNameKey> subscribedBranches,
        Map<BranchNameKey, GitModules> branchGitModules,
        MergeOpRepoManager orm)
        throws SubmoduleConflictException {
      logger.atFine().log("Now processing %s", current);

      if (currentVisited.contains(current)) {
        throw new SubmoduleConflictException(
            "Branch level circular subscriptions detected:  "
                + CircularPathFinder.printCircularPath(currentVisited, current));
      }

      if (allVisited.contains(current)) {
        return;
      }

      currentVisited.add(current);
      try {
        Collection<SubmoduleSubscription> subscriptions =
            superProjectSubscriptionsForSubmoduleBranch(current, branchGitModules, orm);
        for (SubmoduleSubscription sub : subscriptions) {
          BranchNameKey superBranch = sub.getSuperProject();
          searchForSuperprojects(
              superBranch,
              currentVisited,
              allVisited,
              affectedBranches,
              targets,
              branchesByProject,
              subscribedBranches,
              branchGitModules,
              orm);
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

    private Collection<BranchNameKey> getDestinationBranches(
        BranchNameKey src, SubscribeSection s, MergeOpRepoManager orm) throws IOException {
      OpenRepo or;
      try {
        or = orm.getRepo(s.project());
      } catch (NoSuchProjectException e) {
        // A project listed a non existent project to be allowed
        // to subscribe to it. Allow this for now, i.e. no exception is
        // thrown.
        return s.getDestinationBranches(src, ImmutableList.of());
      }

      List<Ref> refs = or.repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_HEADS);
      return s.getDestinationBranches(src, refs);
    }

    private Collection<SubmoduleSubscription> superProjectSubscriptionsForSubmoduleBranch(
        BranchNameKey srcBranch,
        Map<BranchNameKey, GitModules> branchGitModules,
        MergeOpRepoManager orm)
        throws IOException {
      logger.atFine().log("Calculating possible superprojects for %s", srcBranch);
      Collection<SubmoduleSubscription> ret = new ArrayList<>();
      Project.NameKey srcProject = srcBranch.project();
      for (SubscribeSection s :
          projectCache
              .get(srcProject)
              .orElseThrow(illegalState(srcProject))
              .getSubscribeSections(srcBranch)) {
        logger.atFine().log("Checking subscribe section %s", s);
        Collection<BranchNameKey> branches = getDestinationBranches(srcBranch, s, orm);
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
  }
}
