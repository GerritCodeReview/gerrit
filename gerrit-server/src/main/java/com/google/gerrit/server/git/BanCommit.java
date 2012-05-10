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
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

public class BanCommit {
  public interface Factory {
    BanCommit create();
  }

  private final Provider<CurrentUser> currentUser;
  private final GitRepositoryManager repoManager;
  private final AccountCache accountCache;
  private NotesBranchUtil.Factory notesBranchUtilFactory;

  @Inject
  BanCommit(final Provider<CurrentUser> currentUser,
      final GitRepositoryManager repoManager, final AccountCache accountCache,
      final NotesBranchUtil.Factory notesBranchUtilFactory) {
    this.currentUser = currentUser;
    this.repoManager = repoManager;
    this.accountCache = accountCache;
    this.notesBranchUtilFactory = notesBranchUtilFactory;
  }

  public BanCommitResult ban(final ProjectControl projectControl,
      final List<ObjectId> commitsToBan, final String reason)
      throws PermissionDeniedException, IOException,
      IncompleteUserInfoException, InterruptedException, MergeException,
      ConcurrentRefUpdateException {
    if (!projectControl.isOwner()) {
      throw new PermissionDeniedException(
          "No project owner: not permitted to ban commits");
    }

    final BanCommitResult result = new BanCommitResult();
    NoteMap banCommitNotes = NoteMap.newEmptyMap();
    // add a note for each banned commit to notes
    final Repository repo =
        repoManager.openRepository(projectControl.getProject().getNameKey());
    final RevWalk revWalk = new RevWalk(repo);
    final ObjectInserter inserter = repo.newObjectInserter();
    try {
      for (final ObjectId commitToBan : commitsToBan) {
        try {
          revWalk.parseCommit(commitToBan);
        } catch (MissingObjectException e) {
          // ignore exception, also not existing commits can be banned
        } catch (IncorrectObjectTypeException e) {
          result.notACommit(commitToBan, e.getMessage());
          continue;
        }
        banCommitNotes.set(commitToBan, createNoteContent(reason, inserter));
      }
      NotesBranchUtil notesBranchUtil = notesBranchUtilFactory.create(repo);
      NoteMap newlyCreated =
          notesBranchUtil.commitNewNotes(banCommitNotes, REF_REJECT_COMMITS,
              createPersonIdent(), buildCommitMessage(commitsToBan, reason));

      for (Note n : banCommitNotes) {
        if (newlyCreated.contains(n)) {
          result.commitBanned(n);
        } else {
          result.commitAlreadyBanned(n);
        }
      }
      return result;
    } finally {
      revWalk.release();
      inserter.release();
    }
  }

  private ObjectId createNoteContent(String reason, ObjectInserter inserter)
      throws UnsupportedEncodingException, IOException {
    String noteContent = reason != null ? reason : "";
    if (! noteContent.endsWith("\n")) {
      noteContent = noteContent + "\n";
    }
    return inserter.insert(Constants.OBJ_BLOB, noteContent.getBytes("UTF-8"));
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
}
