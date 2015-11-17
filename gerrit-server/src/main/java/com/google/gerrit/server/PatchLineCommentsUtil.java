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

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
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
import com.google.gerrit.server.notedb.DraftCommentNotes;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
  public static final Ordering<PatchLineComment> PLC_ORDER =
      new Ordering<PatchLineComment>() {
    @Override
    public int compare(PatchLineComment c1, PatchLineComment c2) {
      String filename1 = c1.getKey().getParentKey().get();
      String filename2 = c2.getKey().getParentKey().get();
      return ComparisonChain.start()
          .compare(filename1, filename2)
          .compare(getCommentPsId(c1).get(), getCommentPsId(c2).get())
          .compare(c1.getSide(), c2.getSide())
          .compare(c1.getLine(), c2.getLine())
          .compare(c1.getWrittenOn(), c2.getWrittenOn())
          .result();
    }
  };

  public static final Ordering<CommentInfo> COMMENT_INFO_ORDER =
      new Ordering<CommentInfo>() {
        @Override
        public int compare(CommentInfo a, CommentInfo b) {
          return ComparisonChain.start()
              .compare(a.path, b.path, NULLS_FIRST)
              .compare(a.patchSet, b.patchSet, NULLS_FIRST)
              .compare(side(a), side(b))
              .compare(a.line, b.line, NULLS_FIRST)
              .compare(a.id, b.id)
              .result();
        }

        private int side(CommentInfo c) {
          return firstNonNull(c.side, Side.REVISION).ordinal();
        }
      };

  public static PatchSet.Id getCommentPsId(PatchLineComment plc) {
    return plc.getKey().getParentKey().getParentKey();
  }

  private static final Ordering<Comparable<?>> NULLS_FIRST =
      Ordering.natural().nullsFirst();

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
    for (PatchLineComment c : draftByChange(db, notes)) {
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
    comments.addAll(notes.getComments().values());
    return sort(comments);
  }

  public List<PatchLineComment> draftByChange(ReviewDb db,
      ChangeNotes notes) throws OrmException {
    if (!migration.readChanges()) {
      return sort(byCommentStatus(
          db.patchComments().byChange(notes.getChangeId()), Status.DRAFT));
    }

    List<PatchLineComment> comments = Lists.newArrayList();
    Iterable<String> filtered = getDraftRefs(notes.getChangeId());
    for (String refName : filtered) {
      Account.Id account = Account.Id.fromRefPart(refName);
      if (account != null) {
        comments.addAll(draftByChangeAuthor(db, notes, account));
      }
    }
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
    return commentsOnFile(notes.load().getComments().values(), file);
  }

  public List<PatchLineComment> publishedByPatchSet(ReviewDb db,
      ChangeNotes notes, PatchSet.Id psId) throws OrmException {
    if (!migration.readChanges()) {
      return sort(
          db.patchComments().publishedByPatchSet(psId).toList());
    }
    return commentsOnPatchSet(notes.load().getComments().values(), psId);
  }

  public List<PatchLineComment> draftByPatchSetAuthor(ReviewDb db,
      PatchSet.Id psId, Account.Id author, ChangeNotes notes)
      throws OrmException {
    if (!migration.readChanges()) {
      return sort(
          db.patchComments().draftByPatchSetAuthor(psId, author).toList());
    }
    return commentsOnPatchSet(
        notes.load().getDraftComments(author).values(), psId);
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
    return commentsOnFile(
        notes.load().getDraftComments(author).values(), file);
  }

  public List<PatchLineComment> draftByChangeAuthor(ReviewDb db,
      ChangeNotes notes, Account.Id author)
      throws OrmException {
    if (!migration.readChanges()) {
      final Change.Id matchId = notes.getChangeId();
      return FluentIterable
          .from(db.patchComments().draftByAuthor(author))
          .filter(new Predicate<PatchLineComment>() {
            @Override
            public boolean apply(PatchLineComment in) {
              Change.Id changeId =
                  in.getKey().getParentKey().getParentKey().getParentKey();
              return changeId.equals(matchId);
            }
          }).toSortedList(PLC_ORDER);
    }
    List<PatchLineComment> comments = Lists.newArrayList();
    comments.addAll(notes.getDraftComments(author).values());
    return sort(comments);
  }

  public List<PatchLineComment> draftByAuthor(ReviewDb db,
      Account.Id author) throws OrmException {
    if (!migration.readChanges()) {
      return sort(db.patchComments().draftByAuthor(author).toList());
    }

    Set<String> refNames =
        getRefNamesAllUsers(RefNames.refsDraftCommentsPrefix(author));
    List<PatchLineComment> comments = Lists.newArrayList();
    for (String refName : refNames) {
      Change.Id changeId = Change.Id.parse(refName);
      comments.addAll(
          draftFactory.create(changeId, author).load().getComments().values());
    }
    return sort(comments);
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

  private static List<PatchLineComment> commentsOnFile(
      Collection<PatchLineComment> allComments,
      String file) {
    List<PatchLineComment> result = new ArrayList<>(allComments.size());
    for (PatchLineComment c : allComments) {
      String currentFilename = c.getKey().getParentKey().getFileName();
      if (currentFilename.equals(file)) {
        result.add(c);
      }
    }
    return sort(result);
  }

  private static List<PatchLineComment> commentsOnPatchSet(
      Collection<PatchLineComment> allComments,
      PatchSet.Id psId) {
    List<PatchLineComment> result = new ArrayList<>(allComments.size());
    for (PatchLineComment c : allComments) {
      if (getCommentPsId(c).equals(psId)) {
        result.add(c);
      }
    }
    return sort(result);
  }

  public static RevId setCommentRevId(PatchLineComment c,
      PatchListCache cache, Change change, PatchSet ps) throws OrmException {
    if (c.getRevId() == null) {
      try {
        // TODO(dborowitz): Bypass cache if side is REVISION.
        PatchList patchList = cache.get(change, ps);
        c.setRevId((c.getSide() == (short) 0)
          ? new RevId(ObjectId.toString(patchList.getOldId()))
          : new RevId(ObjectId.toString(patchList.getNewId())));
      } catch (PatchListNotAvailableException e) {
        throw new OrmException(e);
      }
    }
    return c.getRevId();
  }

  private Set<String> getRefNamesAllUsers(String prefix) throws OrmException {
    try (Repository repo = repoManager.openMetadataRepository(allUsers)) {
      RefDatabase refDb = repo.getRefDatabase();
      return refDb.getRefs(prefix).keySet();
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  private Iterable<String> getDraftRefs(final Change.Id changeId)
      throws OrmException {
    Set<String> refNames = getRefNamesAllUsers(RefNames.REFS_DRAFT_COMMENTS);
    final String suffix = "/" + changeId.get();
    return Iterables.filter(refNames, new Predicate<String>() {
      @Override
      public boolean apply(String input) {
        return input.endsWith(suffix);
      }
    });
  }

  private static List<PatchLineComment> sort(List<PatchLineComment> comments) {
    Collections.sort(comments, PLC_ORDER);
    return comments;
  }
}
