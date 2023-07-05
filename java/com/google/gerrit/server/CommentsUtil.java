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
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RobotComment;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.update.ChangeContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/** Utility functions to manipulate Comments. */
@Singleton
public class CommentsUtil {
  public static final Ordering<Comment> COMMENT_ORDER =
      new Ordering<>() {
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
      new Ordering<>() {
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

  @Nullable
  public static String extractMessageId(@Nullable String tag) {
    if (tag == null || !tag.startsWith("mailMessageId=")) {
      return null;
    }
    return tag.substring("mailMessageId=".length());
  }

  private static final Ordering<Comparable<?>> NULLS_FIRST = Ordering.natural().nullsFirst();

  private final DiffOperations diffOperations;
  private final GitRepositoryManager repoManager;
  private final String serverId;

  @Inject
  CommentsUtil(
      DiffOperations diffOperations,
      GitRepositoryManager repoManager,
      @GerritServerId String serverId) {
    this.diffOperations = diffOperations;
    this.repoManager = repoManager;
    this.serverId = serverId;
  }

  public HumanComment newHumanComment(
      ChangeNotes changeNotes,
      CurrentUser currentUser,
      Instant when,
      String path,
      PatchSet.Id psId,
      short side,
      String message,
      @Nullable Boolean unresolved,
      @Nullable String parentUuid) {
    if (unresolved == null) {
      if (parentUuid == null) {
        // Default to false if comment is not descended from another.
        unresolved = false;
      } else {
        // Inherit unresolved value from inReplyTo comment if not specified.
        Comment.Key key = new Comment.Key(parentUuid, path, psId.get());
        Optional<HumanComment> parent = getPublishedHumanComment(changeNotes, key);

        // If the comment was not found, it is descended from a robot comment, or the UUID is
        // invalid. Either way, we use the default.
        unresolved = parent.map(p -> p.unresolved).orElse(false);
      }
    }
    HumanComment c =
        new HumanComment(
            new Comment.Key(ChangeUtil.messageUuid(), path, psId.get()),
            currentUser.getAccountId(),
            when,
            side,
            message,
            serverId,
            unresolved);
    c.parentUuid = parentUuid;
    currentUser.updateRealAccountId(c::setRealAuthor);
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

  public Optional<HumanComment> getPublishedHumanComment(ChangeNotes notes, Comment.Key key) {
    return publishedHumanCommentsByChange(notes).stream()
        .filter(c -> key.equals(c.key))
        .findFirst();
  }

  public Optional<HumanComment> getPublishedHumanComment(ChangeNotes notes, String uuid) {
    return publishedHumanCommentsByChange(notes).stream()
        .filter(c -> c.key.uuid.equals(uuid))
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

  public Optional<RobotComment> getRobotComment(ChangeNotes notes, String uuid) {
    return robotCommentsByChange(notes).stream().filter(c -> c.key.uuid.equals(uuid)).findFirst();
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
    return c.getUpdated().isAfter(cm.getWrittenOn());
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

  public void putHumanComments(
      ChangeUpdate update, Comment.Status status, Iterable<HumanComment> comments) {
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

  public void setCommentCommitId(Comment c, Change change, PatchSet ps) {
    checkArgument(
        c.key.patchSetId == ps.id().get(),
        "cannot set commit ID for patch set %s on comment %s",
        ps.id(),
        c);
    if (c.getCommitId() == null) {
      // This code is very much down into our stack and shouldn't be used for validation. Hence,
      // don't throw an exception here if we can't find a commit for the indicated side but
      // simply use the all-null ObjectId.
      c.setCommitId(determineCommitId(change, ps, c.side).orElseGet(ObjectId::zeroId));
    }
  }

  /**
   * Determines the SHA-1 of the commit referenced by the (change, patchset, side) triple.
   *
   * @param change the change to which the commit belongs
   * @param patchset the patchset to which the commit belongs
   * @param side the side indicating which commit of the patchset to take. 1 is the patchset commit,
   *     0 the parent commit (or auto-merge for changes representing merge commits); -x the xth
   *     parent commit of a merge commit
   * @return the commit SHA-1 or an empty {@link Optional} if the side isn't available for the given
   *     change/patchset
   * @throws StorageException if the SHA-1 is unavailable for an unknown reason
   */
  public Optional<ObjectId> determineCommitId(Change change, PatchSet patchset, short side) {
    if (Side.fromShort(side) == Side.PARENT) {
      if (side < 0) {
        int parentNumber = Math.abs(side);
        return resolveParentCommit(change.getProject(), patchset, parentNumber);
      }
      return Optional.ofNullable(resolveAutoMergeCommit(change, patchset));
    }
    return Optional.of(patchset.commitId());
  }

  private Optional<ObjectId> resolveParentCommit(
      Project.NameKey project, PatchSet patchset, int parentNumber) {
    try (Repository repository = repoManager.openRepository(project)) {
      RevCommit commit = repository.parseCommit(patchset.commitId());
      if (commit.getParentCount() < parentNumber) {
        return Optional.empty();
      }
      return Optional.of(commit.getParent(parentNumber - 1));
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Nullable
  private ObjectId resolveAutoMergeCommit(Change change, PatchSet patchset) {
    try {
      // TODO(ghareeb): Adjust after the auto-merge code was moved out of the diff caches. Also
      // unignore the test in PortedCommentsIT.
      Map<String, FileDiffOutput> modifiedFiles =
          diffOperations.listModifiedFilesAgainstParent(
              change.getProject(), patchset.commitId(), /* parentNum= */ 0, DiffOptions.DEFAULTS);
      return modifiedFiles.isEmpty()
          ? null
          : modifiedFiles.values().iterator().next().oldCommitId();
    } catch (DiffNotAvailableException e) {
      throw new StorageException(e);
    }
  }

  public static <T extends Comment> List<T> sort(List<T> comments) {
    comments.sort(COMMENT_ORDER);
    return comments;
  }
}
