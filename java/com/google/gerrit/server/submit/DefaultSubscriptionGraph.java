package com.google.gerrit.server.submit;

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmoduleSubscription;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.submit.MergeOpRepoManager.OpenRepo;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec;

/** A container which stores subscription relationship. */
class DefaultSubscriptionGraph implements SubscriptionGraph {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final boolean enableSuperProjectSubscriptions;
  private final ProjectCache projectCache;
  private final GitModules.Factory gitmodulesFactory;
  private final Map<BranchNameKey, GitModules> branchGitModules;
  private final MergeOpRepoManager orm;

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

  /**
   * Multimap of superproject name to all branch names within that superproject which have submodule
   * subscriptions.
   */
  private final SetMultimap<Project.NameKey, BranchNameKey> branchesByProject;

  /** All branches subscribed by other projects. */
  private final Set<BranchNameKey> subBranches;

  DefaultSubscriptionGraph(
      GitModules.Factory gitmodulesFactory,
      Set<BranchNameKey> updatedBranches,
      ProjectCache projectCache,
      MergeOpRepoManager orm,
      boolean enableSuperProjectSubscriptions)
      throws SubmoduleConflictException {
    this.gitmodulesFactory = gitmodulesFactory;
    this.updatedBranches = ImmutableSet.copyOf(updatedBranches);
    this.projectCache = projectCache;
    this.orm = orm;
    this.targets = MultimapBuilder.hashKeys().hashSetValues().build();
    this.branchGitModules = new HashMap<>();
    this.affectedBranches = new HashSet<>();
    this.branchesByProject = MultimapBuilder.hashKeys().hashSetValues().build();
    this.enableSuperProjectSubscriptions = enableSuperProjectSubscriptions;
    this.subBranches = new HashSet<>();
    this.sortedBranches = calculateSubscriptionMaps();
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
   *   <li>{@link #subBranches}
   * </ul>
   *
   * @return the ordered set to be stored in {@link #sortedBranches}.
   */
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
    SubmoduleUtils.reverse(allVisited);
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
              + SubmoduleUtils.printCircularPath(currentVisited, current));
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
        subBranches.add(sub.getSubmodule());
      }
    } catch (IOException e) {
      throw new StorageException("Cannot find superprojects for " + current, e);
    }
    currentVisited.remove(current);
    allVisited.add(current);
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

  /** {@inheritDoc} */
  @Override
  public Collection<SubmoduleSubscription> subscribedBy(BranchNameKey branch) {
    return targets.get(branch);
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasSubscription(BranchNameKey branch) {
    return targets.containsKey(branch);
  }

  /** {@inheritDoc} */
  @Override
  public Set<Project.NameKey> getAffectedSuperProjects() {
    return branchesByProject.keySet();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAffected(Project.NameKey project) {
    return branchesByProject.containsKey(project);
  }

  /** {@inheritDoc} */
  @Override
  public Set<BranchNameKey> getAffectedBranches(Project.NameKey project) {
    return branchesByProject.get(project);
  }

  /** {@inheritDoc} */
  @Override
  public ImmutableSet<BranchNameKey> getSortedBranches() {
    return sortedBranches;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasSuperproject(BranchNameKey branch) {
    return subBranches.contains(branch);
  }

  @VisibleForTesting
  Multimap<NameKey, BranchNameKey> getBranchesByProject() {
    return branchesByProject;
  }

  @VisibleForTesting
  Multimap<BranchNameKey, SubmoduleSubscription> getTargets() {
    return targets;
  }
}
