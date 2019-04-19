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
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RobotComment;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.update.ChangeContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** Utility functions to manipulate Comments. */
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
    return PatchSet.id(changeId, comment.key.patchSetId);
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
  private final String serverId;

  @Inject
  CommentsUtil(
      GitRepositoryManager repoManager, AllUsersName allUsers, @GerritServerId String serverId) {
    this.repoManager = repoManager;
    this.allUsers = allUsers;
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
      throws UnprocessableEntityException {
    if (unresolved == null) {
      if (parentUuid == null) {
        // Default to false if comment is not descended from another.
        unresolved = false;
      } else {
        // Inherit unresolved value from inReplyTo comment if not specified.
        Comment.Key key = new Comment.Key(parentUuid, path, psId.get());
        Optional<Comment> parent = getPublished(ctx.getNotes(), key);
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

  public Optional<Comment> getPublished(ChangeNotes notes, Comment.Key key) {
    return publishedByChange(notes).stream().filter(c -> key.equals(c.key)).findFirst();
  }

  public Optional<Comment> getDraft(ChangeNotes notes, IdentifiedUser user, Comment.Key key) {
    return draftByChangeAuthor(notes, user.getAccountId()).stream()
        .filter(c -> key.equals(c.key))
        .findFirst();
  }

  public List<Comment> publishedByChange(ChangeNotes notes) {
    notes.load();
    return sort(Lists.newArrayList(notes.getComments().values()));
  }

  public List<RobotComment> robotCommentsByChange(ChangeNotes notes) {
    notes.load();
    return sort(Lists.newArrayList(notes.getRobotComments().values()));
  }

  public List<Comment> draftByChange(ChangeNotes notes) {
    List<Comment> comments = new ArrayList<>();
    for (Ref ref : getDraftRefs(notes.getChangeId())) {
      Account.Id account = Account.Id.fromRefSuffix(ref.getName());
      if (account != null) {
        comments.addAll(draftByChangeAuthor(notes, account));
      }
    }
    return sort(comments);
  }

  public List<Comment> byPatchSet(ChangeNotes notes, PatchSet.Id psId) {
    List<Comment> comments = new ArrayList<>();
    comments.addAll(publishedByPatchSet(notes, psId));

    for (Ref ref : getDraftRefs(notes.getChangeId())) {
      Account.Id account = Account.Id.fromRefSuffix(ref.getName());
      if (account != null) {
        comments.addAll(draftByPatchSetAuthor(psId, account, notes));
      }
    }
    return sort(comments);
  }

  public List<Comment> publishedByChangeFile(ChangeNotes notes, String file) {
    return commentsOnFile(notes.load().getComments().values(), file);
  }

  public List<Comment> publishedByPatchSet(ChangeNotes notes, PatchSet.Id psId) {
    return removeCommentsOnAncestorOfCommitMessage(
        commentsOnPatchSet(notes.load().getComments().values(), psId));
  }

  public List<RobotComment> robotCommentsByPatchSet(ChangeNotes notes, PatchSet.Id psId) {
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
      PatchSet.Id psId, Account.Id author, ChangeNotes notes) {
    return commentsOnPatchSet(notes.load().getDraftComments(author).values(), psId);
  }

  public List<Comment> draftByChangeFileAuthor(ChangeNotes notes, String file, Account.Id author) {
    return commentsOnFile(notes.load().getDraftComments(author).values(), file);
  }

  public List<Comment> draftByChangeAuthor(ChangeNotes notes, Account.Id author) {
    List<Comment> comments = new ArrayList<>();
    comments.addAll(notes.getDraftComments(author).values());
    return sort(comments);
  }

  public void putComments(
      ChangeUpdate update, PatchLineComment.Status status, Iterable<Comment> comments) {
    for (Comment c : comments) {
      update.putComment(status, c);
    }
  }

  public void putRobotComments(ChangeUpdate update, Iterable<RobotComment> comments) {
    for (RobotComment c : comments) {
      update.putRobotComment(c);
    }
  }

  public void deleteComments(ChangeUpdate update, Iterable<Comment> comments) {
    for (Comment c : comments) {
      update.deleteComment(c);
    }
  }

  public void deleteCommentByRewritingHistory(
      ChangeUpdate update, Comment.Key commentKey, String newMessage) {
    update.deleteCommentByRewritingHistory(commentKey.uuid, newMessage);
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
      throws PatchListNotAvailableException {
    checkArgument(
        c.key.patchSetId == ps.getId().get(),
        "cannot set RevId for patch set %s on comment %s",
        ps.getId(),
        c);
    if (c.revId == null) {
      if (Side.fromShort(c.side) == Side.PARENT) {
        if (c.side < 0) {
          c.revId = ObjectId.toString(cache.getOldId(change, ps, -c.side));
        } else {
          c.revId = ObjectId.toString(cache.getOldId(change, ps, null));
        }
      } else {
        c.revId = ps.getRevision().get();
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
  public Collection<Ref> getDraftRefs(Change.Id changeId) {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return getDraftRefs(repo, changeId);
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  private Collection<Ref> getDraftRefs(Repository repo, Change.Id changeId) throws IOException {
    return repo.getRefDatabase().getRefsByPrefix(RefNames.refsDraftCommentsPrefix(changeId));
  }

  private static <T extends Comment> List<T> sort(List<T> comments) {
    comments.sort(COMMENT_ORDER);
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
}
