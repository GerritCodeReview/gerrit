// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.DraftCommentsReader;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

@Singleton
class DraftCommentsNotesReader implements DraftCommentsReader {
  private final DraftCommentNotes.Factory draftCommentNotesFactory;
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;

  @Inject
  DraftCommentsNotesReader(
      DraftCommentNotes.Factory draftCommentNotesFactory,
      GitRepositoryManager repoManager,
      AllUsersName allUsers) {
    this.draftCommentNotesFactory = draftCommentNotesFactory;
    this.repoManager = repoManager;
    this.allUsers = allUsers;
  }

  @Override
  public Optional<HumanComment> getDraftComment(
      ChangeNotes notes, IdentifiedUser author, Comment.Key key) {
    return getDraftsByChangeAndDraftAuthor(notes, author.getAccountId()).stream()
        .filter(c -> key.equals(c.key))
        .findFirst();
  }

  @Override
  public List<HumanComment> getDraftsByChangeAndDraftAuthor(ChangeNotes notes, Account.Id author) {
    return sort(new ArrayList<>(notes.getDraftComments(author)));
  }

  @Override
  public List<HumanComment> getDraftsByChangeAndDraftAuthor(Change.Id changeId, Account.Id author) {
    return sort(
        new ArrayList<>(draftCommentNotesFactory.create(changeId, author).load().getComments()));
  }

  @Override
  public List<HumanComment> getDraftsByPatchSetAndDraftAuthor(
      ChangeNotes notes, PatchSet.Id psId, Account.Id author) {
    return sort(
        notes.load().getDraftComments(author).stream()
            .filter(c -> c.key.patchSetId == psId.get())
            .collect(Collectors.toList()));
  }

  @Override
  public List<HumanComment> getDraftsByChangeForAllAuthors(ChangeNotes notes) {
    List<HumanComment> comments = new ArrayList<>();
    for (Ref ref : getDraftRefs(notes)) {
      Account.Id account = Account.Id.fromRefSuffix(ref.getName());
      if (account != null) {
        comments.addAll(getDraftsByChangeAndDraftAuthor(notes, account));
      }
    }
    return sort(comments);
  }

  @Override
  public Set<Account.Id> getUsersWithDrafts(ChangeNotes changeNotes) {
    Set<Account.Id> res = new HashSet<>();
    for (Ref ref : getDraftRefs(changeNotes)) {
      Account.Id account = Account.Id.fromRefSuffix(ref.getName());
      if (account != null
          // Double-check that any drafts exist for this user after
          // filtering out zombies. If some but not all drafts in the ref
          // were zombies, the returned Ref still includes those zombies;
          // this is suboptimal, but is ok for the purposes of
          // draftsByUser(), and easier than trying to rebuild the change at
          // this point.
          && !changeNotes.getDraftComments(account, ref).isEmpty()) {
        res.add(account);
      }
    }
    return res;
  }

  @Override
  public Set<Change.Id> getChangesWithDrafts(Account.Id author) {
    Set<Change.Id> changes = new HashSet<>();
    try (Repository repo = repoManager.openRepository(allUsers)) {
      for (Ref ref : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_DRAFT_COMMENTS)) {
        Integer accountIdFromRef = RefNames.parseRefSuffix(ref.getName());
        if (accountIdFromRef != null && accountIdFromRef == author.get()) {
          Change.Id changeId = Change.Id.fromAllUsersRef(ref.getName());
          if (changeId == null) {
            continue;
          }
          changes.add(changeId);
        }
      }
    } catch (IOException e) {
      throw new StorageException(e);
    }
    return changes;
  }

  private Collection<Ref> getDraftRefs(ChangeNotes notes) {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return repo.getRefDatabase()
          .getRefsByPrefix(RefNames.refsDraftCommentsPrefix(notes.getChangeId()));
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  private List<HumanComment> sort(List<HumanComment> comments) {
    return CommentsUtil.sort(comments);
  }
}
