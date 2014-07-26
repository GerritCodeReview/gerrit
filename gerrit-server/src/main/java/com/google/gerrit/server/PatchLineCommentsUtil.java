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
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.DraftCommentNotes;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
  private final DraftCommentNotes.Factory draftFactory;
  private final NotesMigration migration;

  @VisibleForTesting
  @Inject
  public PatchLineCommentsUtil(GitRepositoryManager repoManager,
      AllUsersNameProvider allUsersProvider,
      DraftCommentNotes.Factory draftFactory,
      NotesMigration migration) {
    this.repoManager = repoManager;
    this.allUsers = allUsersProvider.get();
    this.draftFactory = draftFactory;
    this.migration = migration;
  }

  public PatchLineComment get(ReviewDb db, ChangeNotes notes,
      PatchLineComment.Key key) throws OrmException {
    if (!migration.readComments()) {
      return db.patchComments().get(key);
    }
    for (PatchLineComment c : publishedByChange(db, notes)) {
      if (key.equals(c.getKey())) {
        return c;
      }
    }
    return null;
  }

  private List<PatchLineComment> byChangeStatusReviewDb(ReviewDb db,
      ChangeNotes notes, final PatchLineComment.Status status)
      throws OrmException {
    Iterable<PatchLineComment> comments =
        Iterables.filter(db.patchComments().byChange(notes.getChangeId()),
          new Predicate<PatchLineComment>() {
            @Override
            public boolean apply(PatchLineComment input) {
              return (input.getStatus() == status);
            }
          }
        );
    return Lists.newArrayList(comments);
  }

  public List<PatchLineComment> publishedByChange(ReviewDb db,
      ChangeNotes notes) throws OrmException {
    if (!migration.readPublishedComments()) {
      return byChangeStatusReviewDb(db, notes, Status.PUBLISHED);
    }

    notes.load();
    List<PatchLineComment> comments = Lists.newArrayList();
    comments.addAll(notes.getBaseComments().values());
    comments.addAll(notes.getPatchSetComments().values());
    return comments;
  }

  public List<PatchLineComment> draftByChange(ReviewDb db,
      ChangeNotes notes) throws OrmException {
    if (!migration.readDraftComments()) {
      return byChangeStatusReviewDb(db, notes, Status.DRAFT);
    }

    List<PatchLineComment> comments = Lists.newArrayList();
    Set<String> refNames = getRefNamesAllUsers(RefNames.REFS_USER);
    for (String refName : refNames) {
      String suffix = RefNames.REFS_DRAFT_PREFIX +
          Integer.toString(notes.getChangeId().get());
      if (!refName.endsWith(suffix)) {
        continue;
      }
      Account.Id account = Account.Id.fromRefPart(refName);
      comments.addAll(draftByChangeAuthor(db, notes, account));
    }
    return comments;
  }

  public List<PatchLineComment> byPatchSet(ReviewDb db,
      ChangeNotes notes, PatchSet.Id psId) throws OrmException {
    if (!migration.readComments()) {
      return db.patchComments().byPatchSet(psId).toList();
    }
    List<PatchLineComment> comments = Lists.newArrayList();
    comments.addAll(publishedByPatchSet(db, notes, psId));

    Set<String> refNames = getRefNamesAllUsers(RefNames.REFS_USER);
    for (String refName : refNames) {
      if (!refName.endsWith(RefNames.REFS_DRAFT_PREFIX + notes.getChangeId())) {
        continue;
      }
      Account.Id account = Account.Id.fromRefPart(refName);
      comments.addAll(draftByPatchSetAuthor(db, psId, account, notes));
    }
    return comments;
  }

  public List<PatchLineComment> publishedByChangeFile(ReviewDb db,
      ChangeNotes notes, Change.Id changeId, String file) throws OrmException {
    if (!migration.readPublishedComments()) {
      return db.patchComments().publishedByChangeFile(changeId, file).toList();
    }
    notes.load();
    List<PatchLineComment> comments = Lists.newArrayList();

    addCommentsOnFile(comments, notes.getBaseComments().values(), file);
    addCommentsOnFile(comments, notes.getPatchSetComments().values(),
        file);
    Collections.sort(comments, ChangeNotes.PatchLineCommentComparator);
    return comments;
  }

  public List<PatchLineComment> publishedByPatchSet(ReviewDb db,
      ChangeNotes notes, PatchSet.Id psId) throws OrmException {
    if (!migration.readPublishedComments()) {
      return db.patchComments().publishedByPatchSet(psId).toList();
    }
    notes.load();
    List<PatchLineComment> comments = new ArrayList<PatchLineComment>();
    comments.addAll(notes.getPatchSetComments().get(psId));
    comments.addAll(notes.getBaseComments().get(psId));
    return comments;
  }

  public List<PatchLineComment> draftByPatchSetAuthor(ReviewDb db,
      PatchSet.Id psId, Account.Id author, ChangeNotes notes)
      throws OrmException {
    if (!migration.readDraftComments()) {
      return db.patchComments().draftByPatchSetAuthor(psId, author).toList();
    }
    List<PatchLineComment> comments = Lists.newArrayList();
    comments.addAll(notes.getDraftBaseComments(author).row(psId).values());
    comments.addAll(notes.getDraftPsComments(author).row(psId).values());
    Collections.sort(comments, ChangeNotes.PatchLineCommentComparator);
    return comments;
  }

  public List<PatchLineComment> draftByChangeFileAuthor(ReviewDb db,
      ChangeNotes notes, String file, Account.Id author)
      throws OrmException {
    if (!migration.readDraftComments()) {
      return db.patchComments()
          .draftByChangeFileAuthor(notes.getChangeId(), file, author)
          .toList();
    }
    List<PatchLineComment> comments = Lists.newArrayList();
    addCommentsOnFile(comments, notes.getDraftBaseComments(author).values(),
        file);
    addCommentsOnFile(comments, notes.getDraftPsComments(author).values(),
        file);
    Collections.sort(comments, ChangeNotes.PatchLineCommentComparator);
    return comments;
  }

  public List<PatchLineComment> draftByChangeAuthor(ReviewDb db,
      ChangeNotes notes, Account.Id author)
      throws OrmException {
    if (!migration.readDraftComments()) {
      return db.patchComments().byChange(notes.getChangeId()).toList();
    }
    List<PatchLineComment> comments = Lists.newArrayList();
    comments.addAll(notes.getDraftBaseComments(author).values());
    comments.addAll(notes.getDraftPsComments(author).values());
    return comments;
  }

  public List<PatchLineComment> draftByAuthor(ReviewDb db,
      Account.Id author) throws OrmException {
    if (!migration.readDraftComments()) {
      return db.patchComments().draftByAuthor(author).toList();
    }
    Set<String> refNames = getRefNamesAllUsers(RefNames.refsUsers(author));

    List<PatchLineComment> comments = Lists.newArrayList();
    for (String refName : refNames) {
      if (refName.startsWith(RefNames.REFS_DRAFT_PREFIX)) {
        String changeIdString =
            refName.substring(RefNames.REFS_DRAFT_PREFIX.length());
        Change.Id changeId = Change.Id.parse(changeIdString);
        DraftCommentNotes draftNotes =
            draftFactory.create(changeId, author).load();
        comments.addAll(draftNotes.getDraftBaseComments().values());
        comments.addAll(draftNotes.getDraftPsComments().values());
      }
    }
    return comments;
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

  private Set<String> getRefNamesAllUsers(String prefix) throws OrmException {
    Map<String, Ref> matchingRefs;
    try {
      RefDatabase refDb = repoManager.openRepository(allUsers).getRefDatabase();
      matchingRefs = refDb.getRefs(prefix);
    } catch (IOException e) {
      throw new OrmException(e);
    }
    return matchingRefs.keySet();
  }
}
