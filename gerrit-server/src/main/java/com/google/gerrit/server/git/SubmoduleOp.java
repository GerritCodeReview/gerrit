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

package com.google.gerrit.server.git;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.VerboseSuperprojectUpdate;
import com.google.gerrit.server.git.MergeOpRepoManager.OpenRepo;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateListener;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.update.RepoOnlyOp;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
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
import java.util.Objects;
import java.util.Set;
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
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.RefSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubmoduleOp {

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
        ctx.addRefUpdate(new ReceiveCommand(c.getParent(0), c, branch.get()));
        addBranchTip(branch, c);
      }
    }
  }

  public interface Factory {
    SubmoduleOp create(Set<Branch.NameKey> updatedBranches, MergeOpRepoManager orm);
  }

  private static final Logger log = LoggerFactory.getLogger(SubmoduleOp.class);

  private final GitModules.Factory gitmodulesFactory;
  private final PersonIdent myIdent;
  private final ProjectCache projectCache;
  private final ProjectState.Factory projectStateFactory;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final VerboseSuperprojectUpdate verboseSuperProject;
  private final boolean enableSuperProjectSubscriptions;
  private final MergeOpRepoManager orm;
  private final Map<Branch.NameKey, GitModules> branchGitModules;

  // always update-to-current branch tips during submit process
  private final Map<Branch.NameKey, CodeReviewCommit> branchTips;
  // branches for all the submitting changes
  private final Set<Branch.NameKey> updatedBranches;
  // branches which in either a submodule or a superproject
  private final Set<Branch.NameKey> affectedBranches;
  // sorted version of affectedBranches
  private final ImmutableSet<Branch.NameKey> sortedBranches;
  // map of superproject branch and its submodule subscriptions
  private final SetMultimap<Branch.NameKey, SubmoduleSubscription> targets;
  // map of superproject and its branches which has submodule subscriptions
  private final SetMultimap<Project.NameKey, Branch.NameKey> branchesByProject;

  @AssistedInject
  public SubmoduleOp(
      GitModules.Factory gitmodulesFactory,
      @GerritPersonIdent PersonIdent myIdent,
      @GerritServerConfig Config cfg,
      ProjectCache projectCache,
      ProjectState.Factory projectStateFactory,
      BatchUpdate.Factory batchUpdateFactory,
      @Assisted Set<Branch.NameKey> updatedBranches,
      @Assisted MergeOpRepoManager orm)
      throws SubmoduleException {
    this.gitmodulesFactory = gitmodulesFactory;
    this.myIdent = myIdent;
    this.projectCache = projectCache;
    this.projectStateFactory = projectStateFactory;
    this.batchUpdateFactory = batchUpdateFactory;
    this.verboseSuperProject =
        cfg.getEnum("submodule", null, "verboseSuperprojectUpdate", VerboseSuperprojectUpdate.TRUE);
    this.enableSuperProjectSubscriptions =
        cfg.getBoolean("submodule", "enableSuperProjectSubscriptions", true);
    this.orm = orm;
    this.updatedBranches = updatedBranches;
    this.targets = MultimapBuilder.hashKeys().hashSetValues().build();
    this.affectedBranches = new HashSet<>();
    this.branchTips = new HashMap<>();
    this.branchGitModules = new HashMap<>();
    this.branchesByProject = MultimapBuilder.hashKeys().hashSetValues().build();
    this.sortedBranches = calculateSubscriptionMap();
  }

  private ImmutableSet<Branch.NameKey> calculateSubscriptionMap() throws SubmoduleException {
    if (!enableSuperProjectSubscriptions) {
      logDebug("Updating superprojects disabled");
      return null;
    }

    logDebug("Calculating superprojects - submodules map");
    LinkedHashSet<Branch.NameKey> allVisited = new LinkedHashSet<>();
    for (Branch.NameKey updatedBranch : updatedBranches) {
      if (allVisited.contains(updatedBranch)) {
        continue;
      }

      searchForSuperprojects(updatedBranch, new LinkedHashSet<Branch.NameKey>(), allVisited);
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
    logDebug("Now processing " + current);

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
        branchesByProject.put(superBranch.getParentKey(), superBranch);
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
    logDebug("Inspecting SubscribeSection " + s);
    for (RefSpec r : s.getMatchingRefSpecs()) {
      logDebug("Inspecting [matching] ref " + r);
      if (!r.matchSource(src.get())) {
        continue;
      }
      if (r.isWildcard()) {
        // refs/heads/*[:refs/somewhere/*]
        ret.add(new Branch.NameKey(s.getProject(), r.expandFromSource(src.get()).getDestination()));
      } else {
        // e.g. refs/heads/master[:refs/heads/stable]
        String dest = r.getDestination();
        if (dest == null) {
          dest = r.getSource();
        }
        ret.add(new Branch.NameKey(s.getProject(), dest));
      }
    }

    for (RefSpec r : s.getMultiMatchRefSpecs()) {
      logDebug("Inspecting [all] ref " + r);
      if (!r.matchSource(src.get())) {
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

      for (Ref ref : or.repo.getRefDatabase().getRefs(RefNames.REFS_HEADS).values()) {
        if (r.getDestination() != null && !r.matchDestination(ref.getName())) {
          continue;
        }
        Branch.NameKey b = new Branch.NameKey(s.getProject(), ref.getName());
        if (!ret.contains(b)) {
          ret.add(b);
        }
      }
    }
    logDebug("Returning possible branches: " + ret + "for project " + s.getProject());
    return ret;
  }

  public Collection<SubmoduleSubscription> superProjectSubscriptionsForSubmoduleBranch(
      Branch.NameKey srcBranch) throws IOException {
    logDebug("Calculating possible superprojects for " + srcBranch);
    Collection<SubmoduleSubscription> ret = new ArrayList<>();
    Project.NameKey srcProject = srcBranch.getParentKey();
    ProjectConfig cfg = projectCache.get(srcProject).getConfig();
    for (SubscribeSection s : projectStateFactory.create(cfg).getSubscribeSections(srcBranch)) {
      logDebug("Checking subscribe section " + s);
      Collection<Branch.NameKey> branches = getDestinationBranches(srcBranch, s);
      for (Branch.NameKey targetBranch : branches) {
        Project.NameKey targetProject = targetBranch.getParentKey();
        try {
          OpenRepo or = orm.getRepo(targetProject);
          ObjectId id = or.repo.resolve(targetBranch.get());
          if (id == null) {
            logDebug("The branch " + targetBranch + " doesn't exist.");
            continue;
          }
        } catch (NoSuchProjectException e) {
          logDebug("The project " + targetProject + " doesn't exist");
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
    logDebug("Calculated superprojects for " + srcBranch + " are " + ret);
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
      batchUpdateFactory.execute(
          orm.batchUpdates(superProjects), BatchUpdateListener.NONE, orm.getSubmissionId(), false);
    } catch (RestApiException | UpdateException | IOException | NoSuchProjectException e) {
      throw new SubmoduleException("Cannot update gitlinks", e);
    }
  }

  /** Create a separate gitlink commit */
  public CodeReviewCommit composeGitlinksCommit(final Branch.NameKey subscriber)
      throws IOException, SubmoduleException {
    OpenRepo or;
    try {
      or = orm.getRepo(subscriber.getParentKey());
    } catch (NoSuchProjectException | IOException e) {
      throw new SubmoduleException("Cannot access superproject", e);
    }

    CodeReviewCommit currentCommit;
    if (branchTips.containsKey(subscriber)) {
      currentCommit = branchTips.get(subscriber);
    } else {
      Ref r = or.repo.exactRef(subscriber.get());
      if (r == null) {
        throw new SubmoduleException(
            "The branch was probably deleted from the subscriber repository");
      }
      currentCommit = or.rw.parseCommit(r.getObjectId());
      addBranchTip(subscriber, currentCommit);
    }

    StringBuilder msgbuf = new StringBuilder("");
    PersonIdent author = null;
    DirCache dc = readTree(or.rw, currentCommit);
    DirCacheEditor ed = dc.editor();
    int count = 0;
    for (SubmoduleSubscription s : targets.get(subscriber)) {
      if (count > 0) {
        msgbuf.append("\n\n");
      }
      RevCommit newCommit = updateSubmodule(dc, ed, msgbuf, s);
      count++;
      if (newCommit != null) {
        if (author == null) {
          author = newCommit.getAuthorIdent();
        } else if (!author.equals(newCommit.getAuthorIdent())) {
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
  public CodeReviewCommit composeGitlinksCommit(
      final Branch.NameKey subscriber, CodeReviewCommit currentCommit)
      throws IOException, SubmoduleException {
    OpenRepo or;
    try {
      or = orm.getRepo(subscriber.getParentKey());
    } catch (NoSuchProjectException | IOException e) {
      throw new SubmoduleException("Cannot access superproject", e);
    }

    StringBuilder msgbuf = new StringBuilder("");
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
      // TODO:czhen handle cherrypick footer
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
      DirCache dc, DirCacheEditor ed, StringBuilder msgbuf, final SubmoduleSubscription s)
      throws SubmoduleException, IOException {
    OpenRepo subOr;
    try {
      subOr = orm.getRepo(s.getSubmodule().getParentKey());
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
                + s.getSubmodule().getParentKey().get()
                + " but entry "
                + "doesn't have gitlink file mode.";
        throw new SubmoduleException(errMsg);
      }
      oldCommit = subOr.rw.parseCommit(dce.getObjectId());
    }

    final CodeReviewCommit newCommit;
    if (branchTips.containsKey(s.getSubmodule())) {
      newCommit = branchTips.get(s.getSubmodule());
    } else {
      Ref ref = subOr.repo.getRefDatabase().exactRef(s.getSubmodule().get());
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
    msgbuf.append("* Update " + s.getPath());
    msgbuf.append(" from branch '" + s.getSubmodule().getShortName() + "'");
    msgbuf.append("\n  to " + newCommit.getName());

    // newly created submodule gitlink, do not append whole history
    if (oldCommit == null) {
      return;
    }

    try {
      subOr.rw.resetRetain(subOr.canMergeFlag);
      subOr.rw.markStart(newCommit);
      subOr.rw.markUninteresting(oldCommit);
      for (RevCommit c : subOr.rw) {
        subOr.rw.parseBody(c);
        if (verboseSuperProject == VerboseSuperprojectUpdate.SUBJECT_ONLY) {
          msgbuf.append("\n  - " + c.getShortMessage());
        } else if (verboseSuperProject == VerboseSuperprojectUpdate.TRUE) {
          msgbuf.append("\n  - " + c.getFullMessage().replace("\n", "\n    "));
        }
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

  public ImmutableSet<Project.NameKey> getProjectsInOrder() throws SubmoduleException {
    LinkedHashSet<Project.NameKey> projects = new LinkedHashSet<>();
    for (Project.NameKey project : branchesByProject.keySet()) {
      addAllSubmoduleProjects(project, new LinkedHashSet<>(), projects);
    }

    for (Branch.NameKey branch : updatedBranches) {
      projects.add(branch.getParentKey());
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
        subprojects.add(s.getSubmodule().getParentKey());
      }
    }

    for (Project.NameKey p : subprojects) {
      addAllSubmoduleProjects(p, current, projects);
    }

    current.remove(project);
    projects.add(project);
  }

  public ImmutableSet<Branch.NameKey> getBranchesInOrder() {
    LinkedHashSet<Branch.NameKey> branches = new LinkedHashSet<>();
    if (sortedBranches != null) {
      branches.addAll(sortedBranches);
    }
    branches.addAll(updatedBranches);
    return ImmutableSet.copyOf(branches);
  }

  public boolean hasSubscription(Branch.NameKey branch) {
    return targets.containsKey(branch);
  }

  public void addBranchTip(Branch.NameKey branch, CodeReviewCommit tip) {
    branchTips.put(branch, tip);
  }

  public void addOp(BatchUpdate bu, Branch.NameKey branch) {
    bu.addRepoOnlyOp(new GitlinkOp(branch));
  }

  private void logDebug(String msg, Object... args) {
    if (log.isDebugEnabled()) {
      log.debug(orm.getSubmissionId() + msg, args);
    }
  }
}
