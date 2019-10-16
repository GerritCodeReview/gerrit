// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.CommonConverters;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.change.ChangeMessages;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.ArrayList;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.ChangeIdUtil;

/** Static utilities for working with {@link RevCommit}s. */
@Singleton
public class CommitUtil {
  private final GitRepositoryManager repoManager;
  private final Provider<PersonIdent> serverIdent;

  @Inject
  CommitUtil(
      GitRepositoryManager repoManager, @GerritPersonIdent Provider<PersonIdent> serverIdent) {
    this.repoManager = repoManager;
    this.serverIdent = serverIdent;
  }

  public static CommitInfo toCommitInfo(RevCommit commit) throws IOException {
    return toCommitInfo(commit, null);
  }

  public static CommitInfo toCommitInfo(RevCommit commit, @Nullable RevWalk walk)
      throws IOException {
    CommitInfo info = new CommitInfo();
    info.commit = commit.getName();
    info.author = CommonConverters.toGitPerson(commit.getAuthorIdent());
    info.committer = CommonConverters.toGitPerson(commit.getCommitterIdent());
    info.subject = commit.getShortMessage();
    info.message = commit.getFullMessage();
    info.parents = new ArrayList<>(commit.getParentCount());
    for (int i = 0; i < commit.getParentCount(); i++) {
      RevCommit p = walk == null ? commit.getParent(i) : walk.parseCommit(commit.getParent(i));
      CommitInfo parentInfo = new CommitInfo();
      parentInfo.commit = p.getName();
      parentInfo.subject = p.getShortMessage();
      info.parents.add(parentInfo);
    }
    return info;
  }

  /**
   * Allows creating a revert commit.
   *
   * @param message Commit message for the revert commit.
   * @param notes ChangeNotes of the change being reverted.
   * @param user Current User performing the revert.
   * @return ObjectId that represents the newly created commit.
   * @throws ResourceConflictException Can't revert the initial commit.
   * @throws IOException Thrown in case of I/O errors.
   */
  public ObjectId createRevertCommit(String message, ChangeNotes notes, CurrentUser user)
      throws ResourceConflictException, IOException {
    message = Strings.emptyToNull(message);

    Project.NameKey project = notes.getProjectName();
    try (Repository git = repoManager.openRepository(project);
        ObjectInserter oi = git.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk revWalk = new RevWalk(reader)) {
      return createRevertCommit(message, notes, user, null, TimeUtil.nowTs(), oi, revWalk);
    }
  }

  /**
   * @param message Commit message for the revert commit.
   * @param notes ChangeNotes of the change being reverted.
   * @param user Current User performing the revert.
   * @param generatedChangeId The changeId for the commit message, can be null since it is not
   *     needed for commits, only for changes.
   * @param ts Timestamp of creation for the commit.
   * @param oi ObjectInserter for inserting the newly created commit.
   * @param revWalk Used for parsing the original commit.
   * @return ObjectId that represents the newly created commit.
   * @throws ResourceConflictException Can't revert the initial commit.
   * @throws IOException Thrown in case of I/O errors.
   */
  public ObjectId createRevertCommit(
      String message,
      ChangeNotes notes,
      CurrentUser user,
      @Nullable ObjectId generatedChangeId,
      Timestamp ts,
      ObjectInserter oi,
      RevWalk revWalk)
      throws ResourceConflictException, IOException {

    PatchSet patch = notes.getCurrentPatchSet();
    RevCommit commitToRevert = revWalk.parseCommit(patch.commitId());
    if (commitToRevert.getParentCount() == 0) {
      throw new ResourceConflictException("Cannot revert initial commit");
    }

    PersonIdent committerIdent = serverIdent.get();
    PersonIdent authorIdent =
        user.asIdentifiedUser().newCommitterIdent(ts, committerIdent.getTimeZone());

    RevCommit parentToCommitToRevert = commitToRevert.getParent(0);
    revWalk.parseHeaders(parentToCommitToRevert);

    CommitBuilder revertCommitBuilder = new CommitBuilder();
    revertCommitBuilder.addParentId(commitToRevert);
    revertCommitBuilder.setTreeId(parentToCommitToRevert.getTree());
    revertCommitBuilder.setAuthor(authorIdent);
    revertCommitBuilder.setCommitter(authorIdent);

    Change changeToRevert = notes.getChange();
    if (message == null) {
      message =
          MessageFormat.format(
              ChangeMessages.get().revertChangeDefaultMessage,
              changeToRevert.getSubject(),
              patch.commitId().name());
    }
    if (generatedChangeId != null) {
      revertCommitBuilder.setMessage(ChangeIdUtil.insertId(message, generatedChangeId, true));
    }
    ObjectId id = oi.insert(revertCommitBuilder);
    oi.flush();
    return id;
  }
}
