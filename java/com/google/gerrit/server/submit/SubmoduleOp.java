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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmoduleSubscription;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.VerboseSuperprojectUpdate;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.project.NoSuchProjectException;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class SubmoduleOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Only used for branches without code review changes */
  public class GitlinkOp implements RepoOnlyOp {
    private final BranchNameKey branch;
    private final BranchTips currentBranchTips;

    GitlinkOp(BranchNameKey branch, BranchTips branchTips) {
      this.branch = branch;
      this.currentBranchTips = branchTips;
    }

    @Override
    public void updateRepo(RepoContext ctx) throws Exception {
      CodeReviewCommit c = composeGitlinksCommit(branch);
      if (c != null) {
        ctx.addRefUpdate(c.getParent(0), c, branch.branch());
        currentBranchTips.put(branch, c);
      }
    }
  }

  @Singleton
  public static class Factory {
    private final SubscriptionGraph.Factory subscriptionGraphFactory;
    private final Provider<PersonIdent> serverIdent;
    private final Config cfg;

    @Inject
    Factory(
        SubscriptionGraph.Factory subscriptionGraphFactory,
        @GerritPersonIdent Provider<PersonIdent> serverIdent,
        @GerritServerConfig Config cfg) {
      this.subscriptionGraphFactory = subscriptionGraphFactory;
      this.serverIdent = serverIdent;
      this.cfg = cfg;
    }

    public SubmoduleOp create(Set<BranchNameKey> updatedBranches, MergeOpRepoManager orm)
        throws SubmoduleConflictException {
      SubscriptionGraph subscriptionGraph;
      if (cfg.getBoolean("submodule", "enableSuperProjectSubscriptions", true)) {
        subscriptionGraph = subscriptionGraphFactory.compute(updatedBranches, orm);
      } else {
        logger.atFine().log("Updating superprojects disabled");
        subscriptionGraph =
            SubscriptionGraph.createEmptyGraph(ImmutableSet.copyOf(updatedBranches));
      }
      return new SubmoduleOp(serverIdent.get(), cfg, orm, subscriptionGraph);
    }
  }

  private final PersonIdent myIdent;
  private final VerboseSuperprojectUpdate verboseSuperProject;
  private final long maxCombinedCommitMessageSize;
  private final long maxCommitMessages;
  private final MergeOpRepoManager orm;
  private final SubscriptionGraph subscriptionGraph;

  private final BranchTips branchTips = new BranchTips();

  private SubmoduleOp(
      PersonIdent myIdent,
      Config cfg,
      MergeOpRepoManager orm,
      SubscriptionGraph subscriptionGraph) {
    this.myIdent = myIdent;
    this.verboseSuperProject =
        cfg.getEnum("submodule", null, "verboseSuperprojectUpdate", VerboseSuperprojectUpdate.TRUE);
    this.maxCombinedCommitMessageSize =
        cfg.getLong("submodule", "maxCombinedCommitMessageSize", 256 << 10);
    this.maxCommitMessages = cfg.getLong("submodule", "maxCommitMessages", 1000);
    this.orm = orm;
    this.subscriptionGraph = subscriptionGraph;
  }

  @UsedAt(UsedAt.Project.PLUGIN_DELETE_PROJECT)
  public boolean hasSuperproject(BranchNameKey branch) {
    return subscriptionGraph.hasSuperproject(branch);
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
        if (subscriptionGraph.isAffectedSuperProject(project)) {
          superProjects.add(project);
          // get a new BatchUpdate for the super project
          OpenRepo or = orm.getRepo(project);
          for (BranchNameKey branch : subscriptionGraph.getAffectedSuperBranches(project)) {
            addOp(or.getUpdate(), branch);
          }
        }
      }
      BatchUpdate.execute(orm.batchUpdates(superProjects), BatchUpdateListener.NONE, false);
    } catch (UpdateException | IOException | NoSuchProjectException e) {
      throw new StorageException("Cannot update gitlinks", e);
    }
  }

  /** Create a separate gitlink commit */
  private CodeReviewCommit composeGitlinksCommit(BranchNameKey subscriber)
      throws IOException, SubmoduleConflictException {
    OpenRepo or;
    try {
      or = orm.getRepo(subscriber.project());
    } catch (NoSuchProjectException | IOException e) {
      throw new StorageException("Cannot access superproject", e);
    }

    CodeReviewCommit currentCommit =
        branchTips
            .getTip(subscriber, or)
            .orElseThrow(
                () ->
                    new SubmoduleConflictException(
                        "The branch was probably deleted from the subscriber repository"));

    StringBuilder msgbuf = new StringBuilder();
    PersonIdent author = null;
    DirCache dc = readTree(or.rw, currentCommit);
    DirCacheEditor ed = dc.editor();
    int count = 0;

    List<SubmoduleSubscription> subscriptions =
        subscriptionGraph.getSubscriptions(subscriber).stream()
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
    if (verboseSuperProject == VerboseSuperprojectUpdate.COPY_TIP_MESSAGE) {
      commitMsg.setLength(0);
    }
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
  CodeReviewCommit amendGitlinksCommit(BranchNameKey subscriber, CodeReviewCommit currentCommit)
      throws IOException, SubmoduleConflictException {
    OpenRepo or;
    try {
      or = orm.getRepo(subscriber.project());
    } catch (NoSuchProjectException | IOException e) {
      throw new StorageException("Cannot access superproject", e);
    }

    StringBuilder msgbuf = new StringBuilder();
    DirCache dc = readTree(or.rw, currentCommit);
    DirCacheEditor ed = dc.editor();
    for (SubmoduleSubscription s : subscriptionGraph.getSubscriptions(subscriber)) {
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
      throws SubmoduleConflictException, IOException {
    logger.atFine().log("Updating gitlink for %s", s);
    OpenRepo subOr;
    try {
      subOr = orm.getRepo(s.getSubmodule().project());
    } catch (NoSuchProjectException | IOException e) {
      throw new StorageException("Cannot access submodule", e);
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
        throw new SubmoduleConflictException(errMsg);
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

    Optional<CodeReviewCommit> maybeNewCommit = branchTips.getTip(s.getSubmodule(), subOr);
    if (!maybeNewCommit.isPresent()) {
      // This submodule branch is neither in the submit set nor in the repository itself
      ed.add(new DeletePath(s.getPath()));
      return null;
    }

    CodeReviewCommit newCommit = maybeNewCommit.get();

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
      RevCommit oldCommit) {
    if (verboseSuperProject != VerboseSuperprojectUpdate.COPY_TIP_MESSAGE) {
      msgbuf.append("* Update ");
      msgbuf.append(s.getPath());
      msgbuf.append(" from branch '");
      msgbuf.append(s.getSubmodule().shortName());
      msgbuf.append("'");
      msgbuf.append("\n  to ");
      msgbuf.append(newCommit.getName());
    }

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

        // Copy over top submodule commit message...
        if (verboseSuperProject == VerboseSuperprojectUpdate.COPY_TIP_MESSAGE) {
          msgbuf.append(c.getFullMessage());
          return;
        }

        String message =
            verboseSuperProject == VerboseSuperprojectUpdate.SUBJECT_ONLY
                ? c.getShortMessage()
                : StringUtils.replace(c.getFullMessage(), "\n", "\n    ");

        String bullet = "\n  - ";
        String ellipsis = "\n\n[...]";
        int newSize = msgbuf.length() + bullet.length() + message.length();
        if (++numMessages > maxCommitMessages
            || newSize > maxCombinedCommitMessageSize
            || (iter.hasNext() && (newSize + ellipsis.length()) > maxCombinedCommitMessageSize)) {
          msgbuf.append(ellipsis);
          break;
        }
        msgbuf.append(bullet);
        msgbuf.append(message);
      }
    } catch (IOException e) {
      throw new StorageException(
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

  ImmutableSet<Project.NameKey> getProjectsInOrder() throws SubmoduleConflictException {
    LinkedHashSet<Project.NameKey> projects = new LinkedHashSet<>();
    for (Project.NameKey project : subscriptionGraph.getAffectedSuperProjects()) {
      addAllSubmoduleProjects(project, new LinkedHashSet<>(), projects);
    }

    for (BranchNameKey branch : subscriptionGraph.getUpdatedBranches()) {
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
          "Project level circular subscriptions detected:  "
              + CircularPathFinder.printCircularPath(current, project));
    }

    if (projects.contains(project)) {
      return;
    }

    current.add(project);
    Set<Project.NameKey> subprojects = new HashSet<>();
    for (BranchNameKey branch : subscriptionGraph.getAffectedSuperBranches(project)) {
      Collection<SubmoduleSubscription> subscriptions = subscriptionGraph.getSubscriptions(branch);
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
    branches.addAll(subscriptionGraph.getSortedSuperprojectAndSubmoduleBranches());
    branches.addAll(subscriptionGraph.getUpdatedBranches());
    return ImmutableSet.copyOf(branches);
  }

  boolean hasSubscription(BranchNameKey branch) {
    return subscriptionGraph.hasSubscription(branch);
  }

  void addBranchTip(BranchNameKey branch, CodeReviewCommit tip) {
    branchTips.put(branch, tip);
  }

  void addOp(BatchUpdate bu, BranchNameKey branch) {
    bu.addRepoOnlyOp(new GitlinkOp(branch, branchTips));
  }
}
