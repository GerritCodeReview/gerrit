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

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.SubmoduleSubscription;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.VerboseSuperprojectUpdate;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.submit.MergeOpRepoManager.OpenRepo;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
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

/** Create commit or amend existing one updating gitlinks. */
class SubmoduleCommits {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PersonIdent myIdent;
  private final VerboseSuperprojectUpdate verboseSuperProject;
  private final MergeOpRepoManager orm;
  private final long maxCombinedCommitMessageSize;
  private final long maxCommitMessages;
  private final BranchTips branchTips = new BranchTips();

  @Singleton
  public static class Factory {
    private final Provider<PersonIdent> serverIdent;
    private final Config cfg;

    @Inject
    Factory(@GerritPersonIdent Provider<PersonIdent> serverIdent, @GerritServerConfig Config cfg) {
      this.serverIdent = serverIdent;
      this.cfg = cfg;
    }

    public SubmoduleCommits create(MergeOpRepoManager orm) {
      return new SubmoduleCommits(orm, serverIdent.get(), cfg);
    }
  }

  SubmoduleCommits(MergeOpRepoManager orm, PersonIdent myIdent, Config cfg) {
    this.orm = orm;
    this.myIdent = myIdent;
    this.verboseSuperProject =
        cfg.getEnum("submodule", null, "verboseSuperprojectUpdate", VerboseSuperprojectUpdate.TRUE);
    this.maxCombinedCommitMessageSize =
        cfg.getLong("submodule", "maxCombinedCommitMessageSize", 256 << 10);
    this.maxCommitMessages = cfg.getLong("submodule", "maxCommitMessages", 1000);
  }

  /**
   * Use the commit as tip of the branch
   *
   * <p>This keeps track of the tip of the branch as the submission progresses.
   */
  void addBranchTip(BranchNameKey branch, CodeReviewCommit tip) {
    branchTips.put(branch, tip);
  }

  /**
   * Create a separate gitlink commit
   *
   * @param subscriber superproject (and branch)
   * @param subscriptions subprojects the superproject is subscribed to
   * @return a new commit on top of subscriber with gitlinks update to the tips of the subprojects;
   *     empty if nothing has changed. Subproject tips are read from the cached branched tips
   *     (defaulting to the mergeOpRepoManager).
   */
  Optional<CodeReviewCommit> composeGitlinksCommit(
      BranchNameKey subscriber, Collection<SubmoduleSubscription> subscriptions)
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
    DirCache dc = readTree(or.getCodeReviewRevWalk(), currentCommit);
    DirCacheEditor ed = dc.editor();
    int count = 0;

    for (SubmoduleSubscription s : sortByPath(subscriptions)) {
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
      return Optional.empty();
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
    return Optional.of(or.getCodeReviewRevWalk().parseCommit(id));
  }

  /** Amend an existing commit with gitlink updates */
  CodeReviewCommit amendGitlinksCommit(
      BranchNameKey subscriber,
      CodeReviewCommit currentCommit,
      Collection<SubmoduleSubscription> subscriptions)
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
    for (SubmoduleSubscription s : sortByPath(subscriptions)) {
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
    CodeReviewCommit newCommit = or.getCodeReviewRevWalk().parseCommit(id);
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
        oldCommit = subOr.getCodeReviewRevWalk().parseCommit(dce.getObjectId());
      } catch (IOException e) {
        // Broken gitlink; sanity check failed. Warn and continue so the submit operation can
        // proceed, it will just skip this gitlink update.
        logger.atSevere().withCause(e).log("Failed to read commit %s", dce.getObjectId().name());
        return null;
      }
    }

    Optional<CodeReviewCommit> maybeNewCommit = branchTips.getTip(s.getSubmodule(), subOr);
    if (!maybeNewCommit.isPresent()) {
      // For whatever reason, this submodule was not updated as part of this submit batch, but the
      // superproject is still subscribed to this branch. Re-read the ref to see if anything has
      // changed since the last time the gitlink was updated, and roll that update into the same
      // commit as all other submodule updates.
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
    subOr.getCodeReviewRevWalk().parseBody(newCommit);
    return newCommit;
  }

  private void createSubmoduleCommitMsg(
      StringBuilder msgbuf,
      SubmoduleSubscription s,
      OpenRepo subOr,
      RevCommit newCommit,
      RevCommit oldCommit) {
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

  private static List<SubmoduleSubscription> sortByPath(
      Collection<SubmoduleSubscription> subscriptions) {
    return subscriptions.stream()
        .sorted(comparing(SubmoduleSubscription::getPath))
        .collect(toList());
  }
}
