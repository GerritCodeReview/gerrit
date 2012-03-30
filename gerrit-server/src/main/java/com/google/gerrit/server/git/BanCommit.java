// Copyright (C) 2012 The Android Open Source Project
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

import static com.google.gerrit.server.git.GitRepositoryManager.REF_REJECT_COMMITS;

import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.notes.NoteMapMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.List;

public class BanCommit {

  private static final int MAX_LOCK_FAILURE_CALLS = 10;
  private static final int SLEEP_ON_LOCK_FAILURE_MS = 25;

  public interface Factory {
    BanCommit create();
  }

  private final Provider<CurrentUser> currentUser;
  private final GitRepositoryManager repoManager;
  private final AccountCache accountCache;
  private final PersonIdent gerritIdent;

  @Inject
  BanCommit(final Provider<CurrentUser> currentUser,
      final GitRepositoryManager repoManager, final AccountCache accountCache,
      @GerritPersonIdent final PersonIdent gerritIdent) {
    this.currentUser = currentUser;
    this.repoManager = repoManager;
    this.accountCache = accountCache;
    this.gerritIdent = gerritIdent;
  }

  public BanCommitResult ban(final ProjectControl projectControl,
      final List<ObjectId> commitsToBan, final String reason)
      throws PermissionDeniedException, IOException,
      IncompleteUserInfoException, InterruptedException, MergeException {
    if (!projectControl.isOwner()) {
      throw new PermissionDeniedException(
          "No project owner: not permitted to ban commits");
    }

    final BanCommitResult result = new BanCommitResult();

    final PersonIdent currentUserIdent = createPersonIdent();
    final Repository repo =
        repoManager.openRepository(projectControl.getProject().getNameKey());
    try {
      final RevWalk revWalk = new RevWalk(repo);
      final ObjectInserter inserter = repo.newObjectInserter();
      try {
        NoteMap baseNoteMap = null;
        RevCommit baseCommit = null;
        final Ref notesBranch = repo.getRef(REF_REJECT_COMMITS);
        if (notesBranch != null) {
          baseCommit = revWalk.parseCommit(notesBranch.getObjectId());
          baseNoteMap = NoteMap.read(revWalk.getObjectReader(), baseCommit);
        }

        final NoteMap ourNoteMap;
        if (baseCommit != null) {
          ourNoteMap = NoteMap.read(repo.newObjectReader(), baseCommit);
        } else {
          ourNoteMap = NoteMap.newEmptyMap();
        }

        for (final ObjectId commitToBan : commitsToBan) {
          try {
            revWalk.parseCommit(commitToBan);
          } catch (MissingObjectException e) {
            // ignore exception, also not existing commits can be banned
          } catch (IncorrectObjectTypeException e) {
            result.notACommit(commitToBan, e.getMessage());
            continue;
          }

          final Note note = ourNoteMap.getNote(commitToBan);
          if (note != null) {
            result.commitAlreadyBanned(commitToBan);
            continue;
          }

          final String noteContent = reason != null ? reason : "";
          final ObjectId noteContentId =
              inserter
                  .insert(Constants.OBJ_BLOB, noteContent.getBytes("UTF-8"));
          ourNoteMap.set(commitToBan, noteContentId);
          result.commitBanned(commitToBan);
        }

        if (result.getNewlyBannedCommits().isEmpty()) {
          return result;
        }

        final ObjectId ourCommit =
            commit(ourNoteMap, inserter, currentUserIdent, baseCommit, result,
                reason);

        updateRef(repo, revWalk, inserter, ourNoteMap, ourCommit, baseNoteMap,
            baseCommit);
      } finally {
        revWalk.release();
        inserter.release();
      }
    } finally {
      repo.close();
    }

    return result;
  }

  private PersonIdent createPersonIdent() throws IncompleteUserInfoException {
    final String userName = currentUser.get().getUserName();
    final Account account = accountCache.getByUsername(userName).getAccount();
    if (account.getFullName() == null) {
      throw new IncompleteUserInfoException(userName, "full name");
    }
    if (account.getPreferredEmail() == null) {
      throw new IncompleteUserInfoException(userName, "preferred email");
    }
    return new PersonIdent(account.getFullName(), account.getPreferredEmail());
  }

  private static ObjectId commit(final NoteMap noteMap,
      final ObjectInserter inserter, final PersonIdent personIdent,
      final ObjectId baseCommit, final BanCommitResult result,
      final String reason) throws IOException {
    final String commitMsg =
        buildCommitMessage(result.getNewlyBannedCommits(), reason);
    if (baseCommit != null) {
      return createCommit(noteMap, inserter, personIdent, commitMsg, baseCommit);
    } else {
      return createCommit(noteMap, inserter, personIdent, commitMsg);
    }
  }

  private static ObjectId createCommit(final NoteMap noteMap,
      final ObjectInserter inserter, final PersonIdent personIdent,
      final String message, final ObjectId... parents) throws IOException {
    final CommitBuilder b = new CommitBuilder();
    b.setTreeId(noteMap.writeTree(inserter));
    b.setAuthor(personIdent);
    b.setCommitter(personIdent);
    if (parents.length > 0) {
      b.setParentIds(parents);
    }
    b.setMessage(message);
    final ObjectId commitId = inserter.insert(b);
    inserter.flush();
    return commitId;
  }

  private static String buildCommitMessage(final List<ObjectId> bannedCommits,
      final String reason) {
    final StringBuilder commitMsg = new StringBuilder();
    commitMsg.append("Banning ");
    commitMsg.append(bannedCommits.size());
    commitMsg.append(" ");
    commitMsg.append(bannedCommits.size() == 1 ? "commit" : "commits");
    commitMsg.append("\n\n");
    if (reason != null) {
      commitMsg.append("Reason: ");
      commitMsg.append(reason);
      commitMsg.append("\n\n");
    }
    commitMsg.append("The following commits are banned:\n");
    final StringBuilder commitList = new StringBuilder();
    for (final ObjectId c : bannedCommits) {
      if (commitList.length() > 0) {
        commitList.append(",\n");
      }
      commitList.append(c.getName());
    }
    commitMsg.append(commitList);
    return commitMsg.toString();
  }

  public void updateRef(final Repository repo, final RevWalk revWalk,
      final ObjectInserter inserter, final NoteMap ourNoteMap,
      final ObjectId oursCommit, final NoteMap baseNoteMap,
      final ObjectId baseCommit) throws IOException, InterruptedException,
      MissingObjectException, IncorrectObjectTypeException,
      CorruptObjectException, MergeException {

    int remainingLockFailureCalls = MAX_LOCK_FAILURE_CALLS;
    RefUpdate refUpdate = createRefUpdate(repo, oursCommit, baseCommit);

    for (;;) {
      final Result result = refUpdate.update();

      if (result == Result.LOCK_FAILURE) {
        if (--remainingLockFailureCalls > 0) {
          Thread.sleep(SLEEP_ON_LOCK_FAILURE_MS);
        } else {
          throw new MergeException("Failed to lock the ref: "
              + REF_REJECT_COMMITS);
        }

      } else if (result == Result.REJECTED) {
        final RevCommit theirsCommit =
            revWalk.parseCommit(refUpdate.getOldObjectId());
        final NoteMap theirNoteMap =
            NoteMap.read(revWalk.getObjectReader(), theirsCommit);
        final NoteMapMerger merger = new NoteMapMerger(repo);
        final NoteMap merged =
            merger.merge(baseNoteMap, ourNoteMap, theirNoteMap);
        final ObjectId mergeCommit =
            createCommit(merged, inserter, gerritIdent,
                "Merged note commits\n", oursCommit, theirsCommit);
        refUpdate = createRefUpdate(repo, mergeCommit, theirsCommit);
        remainingLockFailureCalls = MAX_LOCK_FAILURE_CALLS;

      } else if (result == Result.IO_FAILURE) {
        throw new IOException(
            "Couldn't create commit reject notes because of IO_FAILURE");
      } else {
        break;
      }
    }
  }

  private static RefUpdate createRefUpdate(final Repository repo,
      final ObjectId newObjectId, final ObjectId expectedOldObjectId)
      throws IOException {
    RefUpdate refUpdate = repo.updateRef(REF_REJECT_COMMITS);
    refUpdate.setNewObjectId(newObjectId);
    if (expectedOldObjectId == null) {
      refUpdate.setExpectedOldObjectId(ObjectId.zeroId());
    } else {
      refUpdate.setExpectedOldObjectId(expectedOldObjectId);
    }
    return refUpdate;
  }
}
