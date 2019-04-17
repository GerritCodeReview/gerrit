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

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.VerboseSuperprojectUpdate;
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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;

public class SubmoduleOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Only used for branches without code review changes */
  public class GitlinkOp implements RepoOnlyOp {
    private final Branch.NameKey branch;

    GitlinkOp(Branch.NameKey branch) {
      this.branch = branch;
    }

    @Override
    public void updateRepo(RepoContext ctx) throws Exception {
      CodeReviewCommit c = composeGitlinksCommit(branch);
      if (c != null) {
        ctx.addRefUpdate(c.getParent(0), c, branch.branch());
        addBranchTip(branch, c);
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

    public SubmoduleOp create(Set<Branch.NameKey> updatedBranches, MergeOpRepoManager orm)
        throws SubmoduleException {
      return new SubmoduleOp(
          gitmodulesFactory, serverIdent.get(), cfg, projectCache, updatedBranches, orm);
    }
  }

  private final GitModules.Factory gitmodulesFactory;
  private final PersonIdent myIdent;
  private final ProjectCache projectCache;
  private final VerboseSuperprojectUpdate verboseSuperProject;
  private final boolean enableSuperProjectSubscriptions;
  private final long maxCombinedCommitMessageSize;
  private final long maxCommitMessages;
  private final MergeOpRepoManager orm;
  private final Map<Branch.NameKey, GitModules> branchGitModules;

  /** Branches updated as part of the enclosing submit or push batch. */
  private final ImmutableSet<Branch.NameKey> updatedBranches;

  /**
   * Current branch tips, taking into account commits created during the submit process as well as
   * submodule updates produced by this class.
   */
  private final Map<Branch.NameKey, CodeReviewCommit> branchTips;

  /**
   * Branches in a superproject that contain submodule subscriptions, plus branches in submodules
   * which are subscribed to by some superproject.
   */
  private final Set<Branch.NameKey> affectedBranches;

  /** Copy of {@link #affectedBranches}, sorted by submodule traversal order. */
  private final ImmutableSet<Branch.NameKey> sortedBranches;

  /** Multimap of superproject branch to submodule subscriptions contained in that branch. */
  private final SetMultimap<Branch.NameKey, SubmoduleSubscription> targets;

  /**
   * Multimap of superproject name to all branch names within that superproject which have submodule
   * subscriptions.
   */
  private final SetMultimap<Project.NameKey, Branch.NameKey> branchesByProject;

  private SubmoduleOp(
      GitModules.Factory gitmodulesFactory,
      PersonIdent myIdent,
      Config cfg,
      ProjectCache projectCache,
      Set<Branch.NameKey> updatedBranches,
      MergeOpRepoManager orm)
      throws SubmoduleException {
    this.gitmodulesFactory = gitmodulesFactory;
    this.myIdent = myIdent;
    this.projectCache = projectCache;
    this.verboseSuperProject =
        cfg.getEnum("submodule", null, "verboseSuperprojectUpdate", VerboseSuperprojectUpdate.TRUE);
    this.enableSuperProjectSubscriptions =
        cfg.getBoolean("submodule", "enableSuperProjectSubscriptions", true);
    this.maxCombinedCommitMessageSize =
        cfg.getLong("submodule", "maxCombinedCommitMessageSize", 256 << 10);
    this.maxCommitMessages = cfg.getLong("submodule", "maxCommitMessages", 1000);
    this.orm = orm;
    this.updatedBranches = ImmutableSet.copyOf(updatedBranches);
    this.targets = MultimapBuilder.hashKeys().hashSetValues().build();
    this.affectedBranches = new HashSet<>();
    this.branchTips = new HashMap<>();
    this.branchGitModules = new HashMap<>();
    this.branchesByProject = MultimapBuilder.hashKeys().hashSetValues().build();
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
   * </ul>
   *
   * @return the ordered set to be stored in {@link #sortedBranches}.
   * @throws SubmoduleException if an error occurred walking projects.
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
  private ImmutableSet<Branch.NameKey> calculateSubscriptionMaps() throws SubmoduleException {
    if (!enableSuperProjectSubscriptions) {
      logger.atFine().log("Updating superprojects disabled");
      return null;
    }

    logger.atFine().log("Calculating superprojects - submodules map");
    LinkedHashSet<Branch.NameKey> allVisited = new LinkedHashSet<>();
    for (Branch.NameKey updatedBranch : updatedBranches) {
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
      Branch.NameKey current,
      LinkedHashSet<Branch.NameKey> currentVisited,
      LinkedHashSet<Branch.NameKey> allVisited)
      throws SubmoduleException {
    logger.atFine().log("Now processing %s", current);

    if (currentVisited.contains(current)) {
      throw new SubmoduleException(
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
        Branch.NameKey superBranch = sub.getSuperProject();
        searchForSuperprojects(superBranch, currentVisited, allVisited);
        targets.put(superBranch, sub);
        branchesByProject.put(superBranch.project(), superBranch);
        affectedBranches.add(superBranch);
        affectedBranches.add(sub.getSubmodule());
      }
    } catch (IOException e) {
      throw new SubmoduleException("Cannot find superprojects for " + current, e);
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

  private Collection<Branch.NameKey> getDestinationBranches(Branch.NameKey src, SubscribeSection s)
      throws IOException {
    Collection<Branch.NameKey> ret = new HashSet<>();
    logger.atFine().log("Inspecting SubscribeSection %s", s);
    for (RefSpec r : s.getMatchingRefSpecs()) {
      logger.atFine().log("Inspecting [matching] ref %s", r);
      if (!r.matchSource(src.branch())) {
        continue;
      }
      if (r.isWildcard()) {
        // refs/heads/*[:refs/somewhere/*]
        ret.add(Branch.nameKey(s.getProject(), r.expandFromSource(src.branch()).getDestination()));
      } else {
        // e.g. refs/heads/master[:refs/heads/stable]
        String dest = r.getDestination();
        if (dest == null) {
          dest = r.getSource();
        }
        ret.add(Branch.nameKey(s.getProject(), dest));
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
        Branch.NameKey b = Branch.nameKey(s.getProject(), ref.getName());
        if (!ret.contains(b)) {
          ret.add(b);
        }
      }
    }
    logger.atFine().log("Returning possible branches: %s for project %s", ret, s.getProject());
    return ret;
  }

  @UsedAt(UsedAt.Project.PLUGIN_DELETE_PROJECT)
  public Collection<SubmoduleSubscription> superProjectSubscriptionsForSubmoduleBranch(
      Branch.NameKey srcBranch) throws IOException {
    logger.atFine().log("Calculating possible superprojects for %s", srcBranch);
    Collection<SubmoduleSubscription> ret = new ArrayList<>();
    Project.NameKey srcProject = srcBranch.project();
    for (SubscribeSection s : projectCache.get(srcProject).getSubscribeSections(srcBranch)) {
      logger.atFine().log("Checking subscribe section %s", s);
      Collection<Branch.NameKey> branches = getDestinationBranches(srcBranch, s);
      for (Branch.NameKey targetBranch : branches) {
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

  public void updateSuperProjects() throws SubmoduleException {
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
          for (Branch.NameKey branch : branchesByProject.get(project)) {
            addOp(or.getUpdate(), branch);
          }
        }
      }
      BatchUpdate.execute(orm.batchUpdates(superProjects), BatchUpdateListener.NONE, false);
    } catch (RestApiException | UpdateException | IOException | NoSuchProjectException e) {
      throw new SubmoduleException("Cannot update gitlinks", e);
    }
  }

  /** Create a separate gitlink commit */
  private CodeReviewCommit composeGitlinksCommit(Branch.NameKey subscriber)
      throws IOException, SubmoduleException {
    OpenRepo or;
    try {
      or = orm.getRepo(subscriber.project());
    } catch (NoSuchProjectException | IOException e) {
      throw new SubmoduleException("Cannot access superproject", e);
    }

    CodeReviewCommit currentCommit;
    if (branchTips.containsKey(subscriber)) {
      currentCommit = branchTips.get(subscriber);
    } else {
      Ref r = or.repo.exactRef(subscriber.branch());
      if (r == null) {
        throw new SubmoduleException(
            "The branch was probably deleted from the subscriber repository");
      }
      currentCommit = or.rw.parseCommit(r.getObjectId());
      addBranchTip(subscriber, currentCommit);
    }

    StringBuilder msgbuf = new StringBuilder();
    PersonIdent author = null;
    DirCache dc = readTree(or.rw, currentCommit);
    DirCacheEditor ed = dc.editor();
    int count = 0;

    List<SubmoduleSubscription> subscriptions =
        targets.get(subscriber).stream()
            .sorted(comparing(SubmoduleSubscription::getPath))
            .collect(toList());
    for (SubmoduleSubscription s : subscriptions) {
      if (count > 0) {
        msgbuf.append("\n\n");
      }
      RevCommit newCommit = updateSubmodule(dc, ed, msgbuf, s);
      count++;
      if (newCommit != null) {
        PersonIdent newCommitAuthor = newCommit.getAuthorIdent();
        if (author == null) {
          author = new PersonIdent(newCommitAuthor, myIdent.getWhen());
        } else if (!author.getName().equals(newCommitAuthor.getName())
            || !author.getEmailAddress().equals(newCommitAuthor.getEmailAddress())) {
          author = myIdent;
        }
      }
    }
    ed.finish();
    ObjectId newTreeId = dc.writeTree(or.ins);

    // Gitlinks are already in the branch, return null
    if (newTreeId.equals(currentCommit.getTree())) {
      return null;
    }
    CommitBuilder commit = new CommitBuilder();
    commit.setTreeId(newTreeId);
    commit.setParentId(currentCommit);
    StringBuilder commitMsg = new StringBuilder("Update git submodules\n\n");
    if (verboseSuperProject != VerboseSuperprojectUpdate.FALSE) {
      commitMsg.append(msgbuf);
    }
    commit.setMessage(commitMsg.toString());
    commit.setAuthor(author);
    commit.setCommitter(myIdent);
    ObjectId id = or.ins.insert(commit);
    return or.rw.parseCommit(id);
  }

  /** Amend an existing commit with gitlink updates */
  CodeReviewCommit composeGitlinksCommit(Branch.NameKey subscriber, CodeReviewCommit currentCommit)
      throws IOException, SubmoduleException {
    OpenRepo or;
    try {
      or = orm.getRepo(subscriber.project());
    } catch (NoSuchProjectException | IOException e) {
      throw new SubmoduleException("Cannot access superproject", e);
    }

    StringBuilder msgbuf = new StringBuilder();
    DirCache dc = readTree(or.rw, currentCommit);
    DirCacheEditor ed = dc.editor();
    for (SubmoduleSubscription s : targets.get(subscriber)) {
      updateSubmodule(dc, ed, msgbuf, s);
    }
    ed.finish();
    ObjectId newTreeId = dc.writeTree(or.ins);

    // Gitlinks are already updated, just return the commit
    if (newTreeId.equals(currentCommit.getTree())) {
      return currentCommit;
    }
    or.rw.parseBody(currentCommit);
    CommitBuilder commit = new CommitBuilder();
    commit.setTreeId(newTreeId);
    commit.setParentIds(currentCommit.getParents());
    if (verboseSuperProject != VerboseSuperprojectUpdate.FALSE) {
      // TODO(czhen): handle cherrypick footer
      commit.setMessage(currentCommit.getFullMessage() + "\n\n* submodules:\n" + msgbuf.toString());
    } else {
      commit.setMessage(currentCommit.getFullMessage());
    }
    commit.setAuthor(currentCommit.getAuthorIdent());
    commit.setCommitter(myIdent);
    ObjectId id = or.ins.insert(commit);
    CodeReviewCommit newCommit = or.rw.parseCommit(id);
    newCommit.copyFrom(currentCommit);
    return newCommit;
  }

  private RevCommit updateSubmodule(
      DirCache dc, DirCacheEditor ed, StringBuilder msgbuf, SubmoduleSubscription s)
      throws SubmoduleException, IOException {
    logger.atFine().log("Updating gitlink for %s", s);
    OpenRepo subOr;
    try {
      subOr = orm.getRepo(s.getSubmodule().project());
    } catch (NoSuchProjectException | IOException e) {
      throw new SubmoduleException("Cannot access submodule", e);
    }

    DirCacheEntry dce = dc.getEntry(s.getPath());
    RevCommit oldCommit = null;
    if (dce != null) {
      if (!dce.getFileMode().equals(FileMode.GITLINK)) {
        String errMsg =
            "Requested to update gitlink "
                + s.getPath()
                + " in "
                + s.getSubmodule().project().get()
                + " but entry "
                + "doesn't have gitlink file mode.";
        throw new SubmoduleException(errMsg);
      }
      // Parse the current gitlink entry commit in the subproject repo. This is used to add a
      // shortlog for this submodule to the commit message in the superproject.
      //
      // Even if we don't strictly speaking need that commit message, parsing the commit is a sanity
      // check that the old gitlink is a commit that actually exists. If not, then there is an
      // inconsistency between the superproject and subproject state, and we don't want to risk
      // making things worse by updating the gitlink to something else.
      try {
        oldCommit = subOr.rw.parseCommit(dce.getObjectId());
      } catch (IOException e) {
        // Broken gitlink; sanity check failed. Warn and continue so the submit operation can
        // proceed, it will just skip this gitlink update.
        logger.atSevere().withCause(e).log("Failed to read commit %s", dce.getObjectId().name());
        return null;
      }
    }

    final CodeReviewCommit newCommit;
    if (branchTips.containsKey(s.getSubmodule())) {
      // This submodule's branch was updated as part of this specific submit batch: update the
      // gitlink to point to the new commit from the batch.
      newCommit = branchTips.get(s.getSubmodule());
    } else {
      // For whatever reason, this submodule was not updated as part of this submit batch, but the
      // superproject is still subscribed to this branch. Re-read the ref to see if anything has
      // changed since the last time the gitlink was updated, and roll that update into the same
      // commit as all other submodule updates.
      Ref ref = subOr.repo.getRefDatabase().exactRef(s.getSubmodule().branch());
      if (ref == null) {
        ed.add(new DeletePath(s.getPath()));
        return null;
      }
      newCommit = subOr.rw.parseCommit(ref.getObjectId());
      addBranchTip(s.getSubmodule(), newCommit);
    }

    if (Objects.equals(newCommit, oldCommit)) {
      // gitlink have already been updated for this submodule
      return null;
    }
    ed.add(
        new PathEdit(s.getPath()) {
          @Override
          public void apply(DirCacheEntry ent) {
            ent.setFileMode(FileMode.GITLINK);
            ent.setObjectId(newCommit.getId());
          }
        });

    if (verboseSuperProject != VerboseSuperprojectUpdate.FALSE) {
      createSubmoduleCommitMsg(msgbuf, s, subOr, newCommit, oldCommit);
    }
    subOr.rw.parseBody(newCommit);
    return newCommit;
  }

  private void createSubmoduleCommitMsg(
      StringBuilder msgbuf,
      SubmoduleSubscription s,
      OpenRepo subOr,
      RevCommit newCommit,
      RevCommit oldCommit)
      throws SubmoduleException {
    msgbuf.append("* Update ");
    msgbuf.append(s.getPath());
    msgbuf.append(" from branch '");
    msgbuf.append(s.getSubmodule().shortName());
    msgbuf.append("'");
    msgbuf.append("\n  to ");
    msgbuf.append(newCommit.getName());

    // newly created submodule gitlink, do not append whole history
    if (oldCommit == null) {
      return;
    }

    try {
      subOr.rw.resetRetain(subOr.canMergeFlag);
      subOr.rw.markStart(newCommit);
      subOr.rw.markUninteresting(oldCommit);
      int numMessages = 0;
      for (Iterator<RevCommit> iter = subOr.rw.iterator(); iter.hasNext(); ) {
        RevCommit c = iter.next();
        subOr.rw.parseBody(c);

        String message =
            verboseSuperProject == VerboseSuperprojectUpdate.SUBJECT_ONLY
                ? c.getShortMessage()
                : StringUtils.replace(c.getFullMessage(), "\n", "\n    ");

        String bullet = "\n  - ";
        String ellipsis = "\n\n[...]";
        int newSize = msgbuf.length() + bullet.length() + message.length();
        if (++numMessages > maxCommitMessages
            || newSize > maxCombinedCommitMessageSize
            || iter.hasNext() && (newSize + ellipsis.length()) > maxCombinedCommitMessageSize) {
          msgbuf.append(ellipsis);
          break;
        }
        msgbuf.append(bullet);
        msgbuf.append(message);
      }
    } catch (IOException e) {
      throw new SubmoduleException(
          "Could not perform a revwalk to create superproject commit message", e);
    }
  }

  private static DirCache readTree(RevWalk rw, ObjectId base) throws IOException {
    final DirCache dc = DirCache.newInCore();
    final DirCacheBuilder b = dc.builder();
    b.addTree(
        new byte[0], // no prefix path
        DirCacheEntry.STAGE_0, // standard stage
        rw.getObjectReader(),
        rw.parseTree(base));
    b.finish();
    return dc;
  }

  ImmutableSet<Project.NameKey> getProjectsInOrder() throws SubmoduleException {
    LinkedHashSet<Project.NameKey> projects = new LinkedHashSet<>();
    for (Project.NameKey project : branchesByProject.keySet()) {
      addAllSubmoduleProjects(project, new LinkedHashSet<>(), projects);
    }

    for (Branch.NameKey branch : updatedBranches) {
      projects.add(branch.project());
    }
    return ImmutableSet.copyOf(projects);
  }

  private void addAllSubmoduleProjects(
      Project.NameKey project,
      LinkedHashSet<Project.NameKey> current,
      LinkedHashSet<Project.NameKey> projects)
      throws SubmoduleException {
    if (current.contains(project)) {
      throw new SubmoduleException(
          "Project level circular subscriptions detected:  " + printCircularPath(current, project));
    }

    if (projects.contains(project)) {
      return;
    }

    current.add(project);
    Set<Project.NameKey> subprojects = new HashSet<>();
    for (Branch.NameKey branch : branchesByProject.get(project)) {
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

  ImmutableSet<Branch.NameKey> getBranchesInOrder() {
    LinkedHashSet<Branch.NameKey> branches = new LinkedHashSet<>();
    if (sortedBranches != null) {
      branches.addAll(sortedBranches);
    }
    branches.addAll(updatedBranches);
    return ImmutableSet.copyOf(branches);
  }

  boolean hasSubscription(Branch.NameKey branch) {
    return targets.containsKey(branch);
  }

  void addBranchTip(Branch.NameKey branch, CodeReviewCommit tip) {
    branchTips.put(branch, tip);
  }

  void addOp(BatchUpdate bu, Branch.NameKey branch) {
    bu.addRepoOnlyOp(new GitlinkOp(branch));
  }
}
