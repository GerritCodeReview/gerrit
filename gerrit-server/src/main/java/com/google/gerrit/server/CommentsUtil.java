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
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.reviewdb.client.PatchLineComment.Status.PUBLISHED;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Streams;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RobotComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.update.BatchUpdateReviewDb;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Utility functions to manipulate Comments.
 *
 * <p>These methods either query for and update Comments in the NoteDb or ReviewDb, depending on the
 * state of the NotesMigration.
 */
@Singleton
public class CommentsUtil {
  public static final Ordering<Comment> COMMENT_ORDER =
      new Ordering<Comment>() {
        @Override
        public int compare(Comment c1, Comment c2) {
          return ComparisonChain.start()
              .compare(c1.key.filename, c2.key.filename)
              .compare(c1.key.patchSetId, c2.key.patchSetId)
              .compare(c1.side, c2.side)
              .compare(c1.lineNbr, c2.lineNbr)
              .compare(c1.writtenOn, c2.writtenOn)
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
              .compare(a.inReplyTo, b.inReplyTo, NULLS_FIRST)
              .compare(a.message, b.message)
              .compare(a.id, b.id)
              .result();
        }

        private int side(CommentInfo c) {
          return firstNonNull(c.side, Side.REVISION).ordinal();
        }
      };

  public static PatchSet.Id getCommentPsId(Change.Id changeId, Comment comment) {
    return new PatchSet.Id(changeId, comment.key.patchSetId);
  }

  public static String extractMessageId(@Nullable String tag) {
    if (tag == null || !tag.startsWith("mailMessageId=")) {
      return null;
    }
    return tag.substring("mailMessageId=".length());
  }

  private static final Ordering<Comparable<?>> NULLS_FIRST = Ordering.natural().nullsFirst();

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final NotesMigration migration;
  private final PatchListCache patchListCache;
  private final PatchSetUtil psUtil;
  private final String serverId;

  @Inject
  CommentsUtil(
      GitRepositoryManager repoManager,
      AllUsersName allUsers,
      NotesMigration migration,
      PatchListCache patchListCache,
      PatchSetUtil psUtil,
      @GerritServerId String serverId) {
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.migration = migration;
    this.patchListCache = patchListCache;
    this.psUtil = psUtil;
    this.serverId = serverId;
  }

  public Comment newComment(
      ChangeContext ctx,
      String path,
      PatchSet.Id psId,
      short side,
      String message,
      @Nullable Boolean unresolved,
      @Nullable String parentUuid)
      throws OrmException, UnprocessableEntityException {
    if (unresolved == null) {
      if (parentUuid == null) {
        // Default to false if comment is not descended from another.
        unresolved = false;
      } else {
        // Inherit unresolved value from inReplyTo comment if not specified.
        Comment.Key key = new Comment.Key(parentUuid, path, psId.patchSetId);
        Optional<Comment> parent = get(ctx.getDb(), ctx.getNotes(), key);
        if (!parent.isPresent()) {
          throw new UnprocessableEntityException("Invalid parentUuid supplied for comment");
        }
        unresolved = parent.get().unresolved;
      }
    }
    Comment c =
        new Comment(
            new Comment.Key(ChangeUtil.messageUuid(), path, psId.get()),
            ctx.getUser().getAccountId(),
            ctx.getWhen(),
            side,
            message,
            serverId,
            unresolved);
    c.parentUuid = parentUuid;
    ctx.getUser().updateRealAccountId(c::setRealAuthor);
    return c;
  }

  public RobotComment newRobotComment(
      ChangeContext ctx,
      String path,
      PatchSet.Id psId,
      short side,
      String message,
      String robotId,
      String robotRunId) {
    RobotComment c =
        new RobotComment(
            new Comment.Key(ChangeUtil.messageUuid(), path, psId.get()),
            ctx.getUser().getAccountId(),
            ctx.getWhen(),
            side,
            message,
            serverId,
            robotId,
            robotRunId);
    ctx.getUser().updateRealAccountId(c::setRealAuthor);
    return c;
  }

  public Optional<Comment> get(ReviewDb db, ChangeNotes notes, Comment.Key key)
      throws OrmException {
    if (!migration.readChanges()) {
      return Optional.ofNullable(
              db.patchComments().get(PatchLineComment.Key.from(notes.getChangeId(), key)))
          .map(plc -> plc.asComment(serverId));
    }
    Predicate<Comment> p = c -> key.equals(c.key);
    Optional<Comment> c = publishedByChange(db, notes).stream().filter(p).findFirst();
    if (c.isPresent()) {
      return c;
    }
    return draftByChange(db, notes).stream().filter(p).findFirst();
  }

  public List<Comment> publishedByChange(ReviewDb db, ChangeNotes notes) throws OrmException {
    if (!migration.readChanges()) {
      return sort(byCommentStatus(db.patchComments().byChange(notes.getChangeId()), PUBLISHED));
    }

    notes.load();
    return sort(Lists.newArrayList(notes.getComments().values()));
  }

  public List<RobotComment> robotCommentsByChange(ChangeNotes notes) throws OrmException {
    if (!migration.readChanges()) {
      return ImmutableList.of();
    }

    notes.load();
    return sort(Lists.newArrayList(notes.getRobotComments().values()));
  }

  public List<Comment> draftByChange(ReviewDb db, ChangeNotes notes) throws OrmException {
    if (!migration.readChanges()) {
      return sort(byCommentStatus(db.patchComments().byChange(notes.getChangeId()), Status.DRAFT));
    }

    List<Comment> comments = new ArrayList<>();
    for (Ref ref : getDraftRefs(notes.getChangeId())) {
      Account.Id account = Account.Id.fromRefSuffix(ref.getName());
      if (account != null) {
        comments.addAll(draftByChangeAuthor(db, notes, account));
      }
    }
    return sort(comments);
  }

  private List<Comment> byCommentStatus(
      ResultSet<PatchLineComment> comments, PatchLineComment.Status status) {
    return toComments(
        serverId, Lists.newArrayList(Iterables.filter(comments, c -> c.getStatus() == status)));
  }

  public List<Comment> byPatchSet(ReviewDb db, ChangeNotes notes, PatchSet.Id psId)
      throws OrmException {
    if (!migration.readChanges()) {
      return sort(toComments(serverId, db.patchComments().byPatchSet(psId).toList()));
    }
    List<Comment> comments = new ArrayList<>();
    comments.addAll(publishedByPatchSet(db, notes, psId));

    for (Ref ref : getDraftRefs(notes.getChangeId())) {
      Account.Id account = Account.Id.fromRefSuffix(ref.getName());
      if (account != null) {
        comments.addAll(draftByPatchSetAuthor(db, psId, account, notes));
      }
    }
    return sort(comments);
  }

  public List<Comment> publishedByChangeFile(
      ReviewDb db, ChangeNotes notes, Change.Id changeId, String file) throws OrmException {
    if (!migration.readChanges()) {
      return sort(
          toComments(serverId, db.patchComments().publishedByChangeFile(changeId, file).toList()));
    }
    return commentsOnFile(notes.load().getComments().values(), file);
  }

  public List<Comment> publishedByPatchSet(ReviewDb db, ChangeNotes notes, PatchSet.Id psId)
      throws OrmException {
    if (!migration.readChanges()) {
      return removeCommentsOnAncestorOfCommitMessage(
          sort(toComments(serverId, db.patchComments().publishedByPatchSet(psId).toList())));
    }
    return removeCommentsOnAncestorOfCommitMessage(
        commentsOnPatchSet(notes.load().getComments().values(), psId));
  }

  public List<RobotComment> robotCommentsByPatchSet(ChangeNotes notes, PatchSet.Id psId)
      throws OrmException {
    if (!migration.readChanges()) {
      return ImmutableList.of();
    }
    return commentsOnPatchSet(notes.load().getRobotComments().values(), psId);
  }

  /**
   * For the commit message the A side in a diff view is always empty when a comparison against an
   * ancestor is done, so there can't be any comments on this ancestor. However earlier we showed
   * the auto-merge commit message on side A when for a merge commit a comparison against the
   * auto-merge was done. From that time there may still be comments on the auto-merge commit
   * message and those we want to filter out.
   */
  private List<Comment> removeCommentsOnAncestorOfCommitMessage(List<Comment> list) {
    return list.stream()
        .filter(c -> c.side != 0 || !Patch.COMMIT_MSG.equals(c.key.filename))
        .collect(toList());
  }

  public List<Comment> draftByPatchSetAuthor(
      ReviewDb db, PatchSet.Id psId, Account.Id author, ChangeNotes notes) throws OrmException {
    if (!migration.readChanges()) {
      return sort(
          toComments(serverId, db.patchComments().draftByPatchSetAuthor(psId, author).toList()));
    }
    return commentsOnPatchSet(notes.load().getDraftComments(author).values(), psId);
  }

  public List<Comment> draftByChangeFileAuthor(
      ReviewDb db, ChangeNotes notes, String file, Account.Id author) throws OrmException {
    if (!migration.readChanges()) {
      return sort(
          toComments(
              serverId,
              db.patchComments()
                  .draftByChangeFileAuthor(notes.getChangeId(), file, author)
                  .toList()));
    }
    return commentsOnFile(notes.load().getDraftComments(author).values(), file);
  }

  public List<Comment> draftByChangeAuthor(ReviewDb db, ChangeNotes notes, Account.Id author)
      throws OrmException {
    if (!migration.readChanges()) {
      return Streams.stream(db.patchComments().draftByAuthor(author))
          .filter(c -> c.getPatchSetId().getParentKey().equals(notes.getChangeId()))
          .map(plc -> plc.asComment(serverId))
          .sorted(COMMENT_ORDER)
          .collect(toList());
    }
    List<Comment> comments = new ArrayList<>();
    comments.addAll(notes.getDraftComments(author).values());
    return sort(comments);
  }

  @Deprecated // To be used only by HasDraftByLegacyPredicate.
  public List<Change.Id> changesWithDraftsByAuthor(ReviewDb db, Account.Id author)
      throws OrmException {
    if (!migration.readChanges()) {
      return FluentIterable.from(db.patchComments().draftByAuthor(author))
          .transform(plc -> plc.getPatchSetId().getParentKey())
          .toList();
    }

    List<Change.Id> changes = new ArrayList<>();
    try (Repository repo = repoManager.openRepository(allUsers)) {
      for (String refName : repo.getRefDatabase().getRefs(RefNames.REFS_DRAFT_COMMENTS).keySet()) {
        Account.Id accountId = Account.Id.fromRefSuffix(refName);
        Change.Id changeId = Change.Id.fromRefPart(refName);
        if (accountId == null || changeId == null) {
          continue;
        }
        changes.add(changeId);
      }
    } catch (IOException e) {
      throw new OrmException(e);
    }
    return changes;
  }

  public void putComments(
      ReviewDb db, ChangeUpdate update, PatchLineComment.Status status, Iterable<Comment> comments)
      throws OrmException {
    for (Comment c : comments) {
      update.putComment(status, c);
    }
    db.patchComments().upsert(toPatchLineComments(update.getId(), status, comments));
  }

  public void putRobotComments(ChangeUpdate update, Iterable<RobotComment> comments) {
    for (RobotComment c : comments) {
      update.putRobotComment(c);
    }
  }

  public void deleteComments(ReviewDb db, ChangeUpdate update, Iterable<Comment> comments)
      throws OrmException {
    for (Comment c : comments) {
      update.deleteComment(c);
    }
    db.patchComments()
        .delete(toPatchLineComments(update.getId(), PatchLineComment.Status.DRAFT, comments));
  }

  public void deleteCommentByRewritingHistory(
      ReviewDb db, ChangeUpdate update, Comment.Key commentKey, PatchSet.Id psId, String newMessage)
      throws OrmException {
    if (PrimaryStorage.of(update.getChange()).equals(PrimaryStorage.REVIEW_DB)) {
      PatchLineComment.Key key =
          new PatchLineComment.Key(new Patch.Key(psId, commentKey.filename), commentKey.uuid);

      if (db instanceof BatchUpdateReviewDb) {
        db = ((BatchUpdateReviewDb) db).unsafeGetDelegate();
      }
      db = ReviewDbUtil.unwrapDb(db);

      PatchLineComment patchLineComment = db.patchComments().get(key);

      if (!patchLineComment.getStatus().equals(PUBLISHED)) {
        throw new OrmException(String.format("comment %s is not published", key));
      }

      patchLineComment.setMessage(newMessage);
      db.patchComments().upsert(Collections.singleton(patchLineComment));
    }

    update.deleteCommentByRewritingHistory(commentKey.uuid, newMessage);
  }

  public void deleteAllDraftsFromAllUsers(Change.Id changeId) throws IOException {
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
      for (Ref ref : getDraftRefs(repo, changeId)) {
        bru.addCommand(new ReceiveCommand(ref.getObjectId(), ObjectId.zeroId(), ref.getName()));
      }
      bru.setRefLogMessage("Delete drafts from NoteDb", false);
      bru.execute(rw, NullProgressMonitor.INSTANCE);
      for (ReceiveCommand cmd : bru.getCommands()) {
        if (cmd.getResult() != ReceiveCommand.Result.OK) {
          throw new IOException(
              String.format(
                  "Failed to delete draft comment ref %s at %s: %s (%s)",
                  cmd.getRefName(), cmd.getOldId(), cmd.getResult(), cmd.getMessage()));
        }
      }
    }
  }

  private static List<Comment> commentsOnFile(Collection<Comment> allComments, String file) {
    List<Comment> result = new ArrayList<>(allComments.size());
    for (Comment c : allComments) {
      String currentFilename = c.key.filename;
      if (currentFilename.equals(file)) {
        result.add(c);
      }
    }
    return sort(result);
  }

  private static <T extends Comment> List<T> commentsOnPatchSet(
      Collection<T> allComments, PatchSet.Id psId) {
    List<T> result = new ArrayList<>(allComments.size());
    for (T c : allComments) {
      if (c.key.patchSetId == psId.get()) {
        result.add(c);
      }
    }
    return sort(result);
  }

  public static void setCommentRevId(Comment c, PatchListCache cache, Change change, PatchSet ps)
      throws OrmException {
    checkArgument(
        c.key.patchSetId == ps.getId().get(),
        "cannot set RevId for patch set %s on comment %s",
        ps.getId(),
        c);
    if (c.revId == null) {
      try {
        if (Side.fromShort(c.side) == Side.PARENT) {
          if (c.side < 0) {
            c.revId = ObjectId.toString(cache.getOldId(change, ps, -c.side));
          } else {
            c.revId = ObjectId.toString(cache.getOldId(change, ps, null));
          }
        } else {
          c.revId = ps.getRevision().get();
        }
      } catch (PatchListNotAvailableException e) {
        throw new OrmException(e);
      }
    }
  }

  /**
   * Get NoteDb draft refs for a change.
   *
   * <p>Works if NoteDb is not enabled, but the results are not meaningful.
   *
   * <p>This is just a simple ref scan, so the results may potentially include refs for zombie draft
   * comments. A zombie draft is one which has been published but the write to delete the draft ref
   * from All-Users failed.
   *
   * @param changeId change ID.
   * @return raw refs from All-Users repo.
   */
  public Collection<Ref> getDraftRefs(Change.Id changeId) throws OrmException {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return getDraftRefs(repo, changeId);
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  private Collection<Ref> getDraftRefs(Repository repo, Change.Id changeId) throws IOException {
    return repo.getRefDatabase().getRefs(RefNames.refsDraftCommentsPrefix(changeId)).values();
  }

  private static <T extends Comment> List<T> sort(List<T> comments) {
    Collections.sort(comments, COMMENT_ORDER);
    return comments;
  }

  public static Iterable<PatchLineComment> toPatchLineComments(
      Change.Id changeId, PatchLineComment.Status status, Iterable<Comment> comments) {
    return FluentIterable.from(comments).transform(c -> PatchLineComment.from(changeId, status, c));
  }

  public static List<Comment> toComments(
      final String serverId, Iterable<PatchLineComment> comments) {
    return COMMENT_ORDER.sortedCopy(
        FluentIterable.from(comments).transform(plc -> plc.asComment(serverId)));
  }

  public void publish(
      ChangeContext ctx, PatchSet.Id psId, Collection<Comment> drafts, @Nullable String tag)
      throws OrmException {
    ChangeNotes notes = ctx.getNotes();
    checkArgument(notes != null);
    if (drafts.isEmpty()) {
      return;
    }

    Map<PatchSet.Id, PatchSet> patchSets =
        psUtil.getAsMap(
            ctx.getDb(), notes, drafts.stream().map(d -> psId(notes, d)).collect(toSet()));
    for (Comment d : drafts) {
      PatchSet ps = patchSets.get(psId(notes, d));
      if (ps == null) {
        throw new OrmException("patch set " + ps + " not found");
      }
      d.writtenOn = ctx.getWhen();
      d.tag = tag;
      // Draft may have been created by a different real user; copy the current real user. (Only
      // applies to X-Gerrit-RunAs, since modifying drafts via on_behalf_of is not allowed.)
      ctx.getUser().updateRealAccountId(d::setRealAuthor);
      setCommentRevId(d, patchListCache, notes.getChange(), ps);
    }
    putComments(ctx.getDb(), ctx.getUpdate(psId), PUBLISHED, drafts);
  }

  private static PatchSet.Id psId(ChangeNotes notes, Comment c) {
    return new PatchSet.Id(notes.getChangeId(), c.key.patchSetId);
  }
}
