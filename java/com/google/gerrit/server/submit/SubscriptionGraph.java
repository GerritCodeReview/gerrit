package com.google.gerrit.server.submit;

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmoduleSubscription;
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
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec;

/**
 * A container which stores subscription relationship. A SubscriptionGraph is calculated every time
 * changes are pushed. Some branches are updated in these changes, and if these branches are
 * subscribed by other projects, SubscriptionGraph would record information about these updated
 * branches and branches/projects affected.
 */
public class SubscriptionGraph {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Branches updated as part of the enclosing submit or push batch. */
  private final ImmutableSet<BranchNameKey> updatedBranches;

  /**
   * All branches affected, including those in superprojects and submodules, sorted by submodule
   * traversal order.
   */
  private final ImmutableSet<BranchNameKey> sortedBranches;

  /** Multimap of superproject branch to submodule subscriptions contained in that branch. */
  private final SetMultimap<BranchNameKey, SubmoduleSubscription> targets;

  /**
   * Multimap of superproject name to all branch names within that superproject which have submodule
   * subscriptions.
   */
  private final SetMultimap<Project.NameKey, BranchNameKey> branchesByProject;

  /** All branches subscribed by other projects. */
  private final Set<BranchNameKey> subscribedBranches;

  public SubscriptionGraph(
      ImmutableSet<BranchNameKey> updatedBranches,
      SetMultimap<BranchNameKey, SubmoduleSubscription> targets,
      SetMultimap<Project.NameKey, BranchNameKey> branchesByProject,
      Set<BranchNameKey> subscribedBranches,
      ImmutableSet<BranchNameKey> sortedBranches) {
    this.updatedBranches = updatedBranches;
    this.targets = targets;
    this.branchesByProject = branchesByProject;
    this.subscribedBranches = subscribedBranches;
    this.sortedBranches = sortedBranches;
  }

  /** Returns an empty {@code SubscriptionGraph}. */
  static SubscriptionGraph createEmptyGraph(ImmutableSet<BranchNameKey> updatedBranches) {
    return new SubscriptionGraph(
        updatedBranches,
        MultimapBuilder.hashKeys().hashSetValues().build(),
        MultimapBuilder.hashKeys().hashSetValues().build(),
        new HashSet<>(),
        null);
  }

  /** Get branches updated as part of the enclosing submit or push batch. */
  ImmutableSet<BranchNameKey> getUpdatedBranches() {
    return updatedBranches;
  }

  /** Get all superprojects affected. */
  Set<Project.NameKey> getAffectedSuperProjects() {
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
  Set<BranchNameKey> getAffectedSuperBranches(Project.NameKey project) {
    return branchesByProject.get(project);
  }

  /**
   * Get all affected branches, including the submodule branches and superproject branches, sorted
   * by traversal order.
   */
  ImmutableSet<BranchNameKey> getSortedSuperprojectAndSubmoduleBranches() {
    return sortedBranches;
  }

  /** Check if a {@code branch} is a submodule of a superproject. */
  boolean hasSuperproject(BranchNameKey branch) {
    return subscribedBranches.contains(branch);
  }

  /** See if a {@code branch} is a superproject branch affected. */
  boolean hasSubscription(BranchNameKey branch) {
    return targets.containsKey(branch);
  }

  /** Get all related {@code SubmoduleSubscription}s whose super branch is {@code branch}. */
  Collection<SubmoduleSubscription> getSubscriptions(BranchNameKey branch) {
    return targets.get(branch);
  }

  public interface Factory {
    SubscriptionGraph create(
        ImmutableSet<BranchNameKey> updatedBranches,
        ProjectCache projectCache,
        MergeOpRepoManager orm)
        throws SubmoduleConflictException;
  }

  public static class Module extends AbstractModule {
    @Override
    protected void configure(){
      bind(Factory.class).to(DefaultFactory.class);
    }
  }

  static class DefaultFactory implements Factory {
    private final GitModules.Factory gitmodulesFactory;
    private Map<BranchNameKey, GitModules> branchGitModules;
    private MergeOpRepoManager orm;
    private ProjectCache projectCache;

    // Fields required to the constructor of SubmoduleGraph.
    /** All affected branches, including those in superprojects and submodules. */
    private Set<BranchNameKey> affectedBranches;

    /** @see SubscriptionGraph#targets */
    private SetMultimap<BranchNameKey, SubmoduleSubscription> targets;

    /** @see SubscriptionGraph#branchesByProject */
    private SetMultimap<Project.NameKey, BranchNameKey> branchesByProject;

    /** @see SubscriptionGraph#subscribedBranches */
    private Set<BranchNameKey> subscribedBranches;

    @Inject
    DefaultFactory(GitModules.Factory gitmodulesFactory) {
      this.gitmodulesFactory = gitmodulesFactory;
    }

    @Override
    public SubscriptionGraph create(
        ImmutableSet<BranchNameKey> updatedBranches,
        ProjectCache projectCache,
        MergeOpRepoManager orm)
        throws SubmoduleConflictException {
      this.projectCache = projectCache;
      this.orm = orm;
      this.branchGitModules = new HashMap<>();

      this.affectedBranches = new HashSet<>();
      this.targets = MultimapBuilder.hashKeys().hashSetValues().build();
      this.branchesByProject = MultimapBuilder.hashKeys().hashSetValues().build();
      this.subscribedBranches = new HashSet<>();
      return new SubscriptionGraph(
          updatedBranches,
          targets,
          branchesByProject,
          subscribedBranches,
          calculateSubscriptionMaps(updatedBranches));
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
    private ImmutableSet<BranchNameKey> calculateSubscriptionMaps(
        Set<BranchNameKey> updatedBranches) throws SubmoduleConflictException {
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
                + CircularPathFinder.printCircularPath(currentVisited, current));
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
