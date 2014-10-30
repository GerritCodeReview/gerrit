// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility functions to manipulate PatchLineComments.
 * <p>
 * These methods either query for and update PatchLineComments in the NoteDb or
 * ReviewDb, depending on the state of the NotesMigration.
 */
@Singleton
public class PatchLineCommentsUtil {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final NotesMigration migration;

  @VisibleForTesting
  @Inject
  public PatchLineCommentsUtil(GitRepositoryManager repoManager,
      AllUsersNameProvider allUsersProvider,
      NotesMigration migration) {
    this.repoManager = repoManager;
    this.allUsers = allUsersProvider.get();
    this.migration = migration;
  }

  public Optional<PatchLineComment> get(ReviewDb db, ChangeNotes notes,
      PatchLineComment.Key key) throws OrmException {
    if (!migration.readChanges()) {
      return Optional.fromNullable(db.patchComments().get(key));
    }
    for (PatchLineComment c : publishedByChange(db, notes)) {
      if (key.equals(c.getKey())) {
        return Optional.of(c);
      }
    }
    return Optional.absent();
  }

  public List<PatchLineComment> publishedByChange(ReviewDb db,
      ChangeNotes notes) throws OrmException {
    if (!migration.readChanges()) {
      return sort(byCommentStatus(
          db.patchComments().byChange(notes.getChangeId()), Status.PUBLISHED));
    }

    notes.load();
    List<PatchLineComment> comments = Lists.newArrayList();
    comments.addAll(notes.getBaseComments().values());
    comments.addAll(notes.getPatchSetComments().values());
    return sort(comments);
  }

  private static List<PatchLineComment> byCommentStatus(
      ResultSet<PatchLineComment> comments,
      final PatchLineComment.Status status) {
    return Lists.newArrayList(
      Iterables.filter(comments, new Predicate<PatchLineComment>() {
        @Override
        public boolean apply(PatchLineComment input) {
          return (input.getStatus() == status);
        }
      })
    );
  }

  public List<PatchLineComment> byPatchSet(ReviewDb db,
      ChangeNotes notes, PatchSet.Id psId) throws OrmException {
    if (!migration.readChanges()) {
      return sort(db.patchComments().byPatchSet(psId).toList());
    }
    List<PatchLineComment> comments = Lists.newArrayList();
    comments.addAll(publishedByPatchSet(db, notes, psId));

    Iterable<String> filtered = getDraftRefs(notes.getChangeId());
    for (String refName : filtered) {
      Account.Id account = Account.Id.fromRefPart(refName);
      if (account != null) {
        comments.addAll(draftByPatchSetAuthor(db, psId, account, notes));
      }
    }
    return sort(comments);
  }

  public List<PatchLineComment> publishedByChangeFile(ReviewDb db,
      ChangeNotes notes, Change.Id changeId, String file) throws OrmException {
    if (!migration.readChanges()) {
      return sort(
          db.patchComments().publishedByChangeFile(changeId, file).toList());
    }
    notes.load();
    List<PatchLineComment> comments = Lists.newArrayList();

    addCommentsOnFile(comments, notes.getBaseComments().values(), file);
    addCommentsOnFile(comments, notes.getPatchSetComments().values(),
        file);
    return sort(comments);
  }

  public List<PatchLineComment> publishedByPatchSet(ReviewDb db,
      ChangeNotes notes, PatchSet.Id psId) throws OrmException {
    if (!migration.readChanges()) {
      return sort(
          db.patchComments().publishedByPatchSet(psId).toList());
    }
    notes.load();
    List<PatchLineComment> comments = new ArrayList<>();
    comments.addAll(notes.getPatchSetComments().get(psId));
    comments.addAll(notes.getBaseComments().get(psId));
    return sort(comments);
  }

  public List<PatchLineComment> draftByPatchSetAuthor(ReviewDb db,
      PatchSet.Id psId, Account.Id author, ChangeNotes notes)
      throws OrmException {
    if (!migration.readChanges()) {
      return sort(
          db.patchComments().draftByPatchSetAuthor(psId, author).toList());
    }

    List<PatchLineComment> comments = Lists.newArrayList();
    comments.addAll(notes.getDraftBaseComments(author).row(psId).values());
    comments.addAll(notes.getDraftPsComments(author).row(psId).values());
    return sort(comments);
  }

  public List<PatchLineComment> draftByChangeFileAuthor(ReviewDb db,
      ChangeNotes notes, String file, Account.Id author)
      throws OrmException {
    if (!migration.readChanges()) {
      return sort(
          db.patchComments()
            .draftByChangeFileAuthor(notes.getChangeId(), file, author)
            .toList());
    }
    List<PatchLineComment> comments = Lists.newArrayList();
    addCommentsOnFile(comments, notes.getDraftBaseComments(author).values(),
        file);
    addCommentsOnFile(comments, notes.getDraftPsComments(author).values(),
        file);
    return sort(comments);
  }

  public Set<Change.Id> changesWithDrafts(ReviewDb db, Account.Id author)
      throws OrmException {
    if (!migration.readChanges()) {
      Set<Change.Id> ids = new HashSet<>();
      for (PatchLineComment c : db.patchComments().draftByAuthor(author)) {
        ids.add(c.getKey().getParentKey().getParentKey().getParentKey());
      }
      return ids;
    }

    Repository git;
    try {
      git = repoManager.openRepository(allUsers);
    } catch (IOException e) {
      throw new OrmException(e);
    }
    try {
      Set<Change.Id> ids = new HashSet<>();
      for (Ref r : scanDraftComments(git, author)) {
        if (author.equals(Account.Id.fromRefPart(r.getName()))) {
          ids.add(RefNames.changeIdFromDraftComments(r.getName()));
        }
      }
      return ids;
    } catch (IOException e) {
      throw new OrmException(e);
    } finally {
      git.close();
    }
  }

  private Iterable<Ref> scanDraftComments(Repository git, Account.Id author)
      throws IOException {
    String prefix = RefNames.refsDraftCommentsScanPrefix(author);
    return git.getRefDatabase().getRefs(prefix).values();
  }

  public void insertComments(ReviewDb db, ChangeUpdate update,
      Iterable<PatchLineComment> comments) throws OrmException {
    for (PatchLineComment c : comments) {
      update.insertComment(c);
    }
    db.patchComments().insert(comments);
  }

  public void upsertComments(ReviewDb db, ChangeUpdate update,
      Iterable<PatchLineComment> comments) throws OrmException {
    for (PatchLineComment c : comments) {
      update.upsertComment(c);
    }
    db.patchComments().upsert(comments);
  }

  public void updateComments(ReviewDb db, ChangeUpdate update,
      Iterable<PatchLineComment> comments) throws OrmException {
    for (PatchLineComment c : comments) {
      update.updateComment(c);
    }
    db.patchComments().update(comments);
  }

  public void deleteComments(ReviewDb db, ChangeUpdate update,
      Iterable<PatchLineComment> comments) throws OrmException {
    for (PatchLineComment c : comments) {
      update.deleteComment(c);
    }
    db.patchComments().delete(comments);
  }

  private static Collection<PatchLineComment> addCommentsOnFile(
      Collection<PatchLineComment> commentsOnFile,
      Collection<PatchLineComment> allComments,
      String file) {
    for (PatchLineComment c : allComments) {
      String currentFilename = c.getKey().getParentKey().getFileName();
      if (currentFilename.equals(file)) {
        commentsOnFile.add(c);
      }
    }
    return commentsOnFile;
  }

  public static void setCommentRevId(PatchLineComment c,
      PatchListCache cache, Change change, PatchSet ps) throws OrmException {
    if (c.getRevId() != null) {
      return;
    }
    PatchList patchList;
    try {
      patchList = cache.get(change, ps);
    } catch (PatchListNotAvailableException e) {
      throw new OrmException(e);
    }
    c.setRevId((c.getSide() == (short) 0)
      ? new RevId(ObjectId.toString(patchList.getOldId()))
      : new RevId(ObjectId.toString(patchList.getNewId())));
  }

  private Set<String> getRefNamesAllUsers(String prefix) throws OrmException {
    Repository repo;
    try {
      repo = repoManager.openRepository(allUsers);
    } catch (IOException e) {
      throw new OrmException(e);
    }
    try {
      RefDatabase refDb = repo.getRefDatabase();
      return refDb.getRefs(prefix).keySet();
    } catch (IOException e) {
      throw new OrmException(e);
    } finally {
      repo.close();
    }
  }

  private Iterable<String> getDraftRefs(final Change.Id changeId)
      throws OrmException {
    Set<String> refNames = getRefNamesAllUsers(RefNames.REFS_DRAFT_COMMENTS);
    final String suffix = "-" + changeId.get();
    return Iterables.filter(refNames, new Predicate<String>() {
      @Override
      public boolean apply(String input) {
        return input.endsWith(suffix);
      }
    });
  }

  private static List<PatchLineComment> sort(List<PatchLineComment> comments) {
    Collections.sort(comments, ChangeNotes.PatchLineCommentComparator);
    return comments;
  }
}
