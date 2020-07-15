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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.RobotComment;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
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

  public HumanComment newHumanComment(
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
        Optional<HumanComment> parent = getPublishedHumanComment(ctx.getNotes(), key);
        if (!parent.isPresent()) {
          throw new UnprocessableEntityException("Invalid parentUuid supplied for comment");
        }
        unresolved = parent.get().unresolved;
      }
    }
    HumanComment c =
        new HumanComment(
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

  public Optional<HumanComment> getPublishedHumanComment(
      ChangeNotes notes, String uuid, String hashedPath, int psId) {
    return publishedHumanCommentsByChange(notes).stream()
        .filter(
            c ->
                uuid.equals(c.key.uuid)
                    && psId == c.key.patchSetId
                    && hashedPath.equals(
                        Hashing.murmur3_128().hashString(c.key.filename, UTF_8).toString()))
        .findFirst();
  }

  public Optional<HumanComment> getPublishedHumanComment(ChangeNotes notes, Comment.Key key) {
    return publishedHumanCommentsByChange(notes).stream()
        .filter(c -> key.equals(c.key))
        .findFirst();
  }

  public Optional<HumanComment> getDraft(ChangeNotes notes, IdentifiedUser user, Comment.Key key) {
    return draftByChangeAuthor(notes, user.getAccountId()).stream()
        .filter(c -> key.equals(c.key))
        .findFirst();
  }

  public List<HumanComment> publishedHumanCommentsByChange(ChangeNotes notes) {
    notes.load();
    return sort(Lists.newArrayList(notes.getHumanComments().values()));
  }

  public List<RobotComment> robotCommentsByChange(ChangeNotes notes) {
    notes.load();
    return sort(Lists.newArrayList(notes.getRobotComments().values()));
  }

  public List<HumanComment> draftByChange(ChangeNotes notes) {
    List<HumanComment> comments = new ArrayList<>();
    for (Ref ref : getDraftRefs(notes.getChangeId())) {
      Account.Id account = Account.Id.fromRefSuffix(ref.getName());
      if (account != null) {
        comments.addAll(draftByChangeAuthor(notes, account));
      }
    }
    return sort(comments);
  }

  public List<HumanComment> byPatchSet(ChangeNotes notes, PatchSet.Id psId) {
    List<HumanComment> comments = new ArrayList<>();
    comments.addAll(publishedByPatchSet(notes, psId));

    for (Ref ref : getDraftRefs(notes.getChangeId())) {
      Account.Id account = Account.Id.fromRefSuffix(ref.getName());
      if (account != null) {
        comments.addAll(draftByPatchSetAuthor(psId, account, notes));
      }
    }
    return sort(comments);
  }

  public List<HumanComment> publishedByChangeFile(ChangeNotes notes, String file) {
    return commentsOnFile(notes.load().getHumanComments().values(), file);
  }

  public List<HumanComment> publishedByPatchSet(ChangeNotes notes, PatchSet.Id psId) {
    return removeCommentsOnAncestorOfCommitMessage(
        commentsOnPatchSet(notes.load().getHumanComments().values(), psId));
  }

  public List<RobotComment> robotCommentsByPatchSet(ChangeNotes notes, PatchSet.Id psId) {
    return commentsOnPatchSet(notes.load().getRobotComments().values(), psId);
  }

  /**
   * This method populates the "changeMessageId" field of the comments parameter based on timestamp
   * matching. The comments objects will be modified.
   *
   * <p>Each comment will be matched to the nearest next change message in timestamp
   *
   * @param comments the list of comments
   * @param changeMessages list of change messages
   */
  public static void linkCommentsToChangeMessages(
      List<? extends CommentInfo> comments,
      List<ChangeMessage> changeMessages,
      boolean skipAutoGeneratedMessages) {
    ArrayList<ChangeMessage> sortedChangeMessages =
        changeMessages.stream()
            .sorted(comparing(ChangeMessage::getWrittenOn))
            .collect(toCollection(ArrayList::new));

    ArrayList<CommentInfo> sortedCommentInfos =
        comments.stream().sorted(comparing(c -> c.updated)).collect(toCollection(ArrayList::new));

    int cmItr = 0;
    for (CommentInfo comment : sortedCommentInfos) {
      // Keep advancing the change message pointer until we associate the comment to the next change
      // message in timestamp
      while (cmItr < sortedChangeMessages.size()) {
        ChangeMessage cm = sortedChangeMessages.get(cmItr);
        if (isAfter(comment, cm) || (skipAutoGeneratedMessages && isAutoGenerated(cm))) {
          cmItr += 1;
        } else {
          break;
        }
      }
      if (cmItr < changeMessages.size()) {
        comment.changeMessageId = sortedChangeMessages.get(cmItr).getKey().uuid();
      }
    }
  }

  private static boolean isAutoGenerated(ChangeMessage cm) {
    // Ignore Gerrit auto-generated messages, allowing to link against human change messages that
    // have an auto-generated tag
    return ChangeMessagesUtil.isAutogeneratedByGerrit(cm.getTag());
  }

  private static boolean isAfter(CommentInfo c, ChangeMessage cm) {
    return c.updated.after(cm.getWrittenOn());
  }

  /**
   * For the commit message the A side in a diff view is always empty when a comparison against an
   * ancestor is done, so there can't be any comments on this ancestor. However earlier we showed
   * the auto-merge commit message on side A when for a merge commit a comparison against the
   * auto-merge was done. From that time there may still be comments on the auto-merge commit
   * message and those we want to filter out.
   */
  private List<HumanComment> removeCommentsOnAncestorOfCommitMessage(List<HumanComment> list) {
    return list.stream()
        .filter(c -> c.side != 0 || !Patch.COMMIT_MSG.equals(c.key.filename))
        .collect(toList());
  }

  public List<HumanComment> draftByPatchSetAuthor(
      PatchSet.Id psId, Account.Id author, ChangeNotes notes) {
    return commentsOnPatchSet(notes.load().getDraftComments(author).values(), psId);
  }

  public List<HumanComment> draftByChangeFileAuthor(
      ChangeNotes notes, String file, Account.Id author) {
    return commentsOnFile(notes.load().getDraftComments(author).values(), file);
  }

  public List<HumanComment> draftByChangeAuthor(ChangeNotes notes, Account.Id author) {
    List<HumanComment> comments = new ArrayList<>();
    comments.addAll(notes.getDraftComments(author).values());
    return sort(comments);
  }

  public void putHumanComments(
      ChangeUpdate update, HumanComment.Status status, Iterable<HumanComment> comments) {
    for (HumanComment c : comments) {
      update.putComment(status, c);
    }
  }

  public void putRobotComments(ChangeUpdate update, Iterable<RobotComment> comments) {
    for (RobotComment c : comments) {
      update.putRobotComment(c);
    }
  }

  public void deleteHumanComments(ChangeUpdate update, Iterable<HumanComment> comments) {
    for (HumanComment c : comments) {
      update.deleteComment(c);
    }
  }

  public void deleteCommentByRewritingHistory(
      ChangeUpdate update, Comment.Key commentKey, String newMessage) {
    update.deleteCommentByRewritingHistory(commentKey.uuid, newMessage);
  }

  private static List<HumanComment> commentsOnFile(
      Collection<HumanComment> allComments, String file) {
    List<HumanComment> result = new ArrayList<>(allComments.size());
    for (HumanComment c : allComments) {
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

  public static void setCommentCommitId(Comment c, PatchListCache cache, Change change, PatchSet ps)
      throws PatchListNotAvailableException {
    checkArgument(
        c.key.patchSetId == ps.id().get(),
        "cannot set commit ID for patch set %s on comment %s",
        ps.id(),
        c);
    if (c.getCommitId() == null) {
      if (Side.fromShort(c.side) == Side.PARENT) {
        if (c.side < 0) {
          c.setCommitId(cache.getOldId(change, ps, -c.side));
        } else {
          c.setCommitId(cache.getOldId(change, ps, null));
        }
      } else {
        c.setCommitId(ps.commitId());
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
}
