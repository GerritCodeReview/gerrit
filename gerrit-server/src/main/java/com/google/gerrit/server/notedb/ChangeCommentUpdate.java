// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;

/**
 * Delete a published comment from NoteDb by rewriting the commit history. Note that we only update
 * the comment's text rather than delete the whole comment permanently.
 *
 * <p>This implementation is based on two assumptions. The first assumption is that the first commit
 * will not contain any comment. The second assumption is that each commit will only put in new
 * comments and will never delete existing comments. If these assumptions are not true any more,
 * this implementation must be updated.
 */
public class ChangeCommentUpdate {
  public interface Factory {
    ChangeCommentUpdate create(CurrentUser user, Change change);
  }

  private final ChangeNoteUtil noteUtil;
  private final AbstractChangeNotes.Args notesArgs;

  private final CurrentUser user;
  private final Change change;

  @AssistedInject
  public ChangeCommentUpdate(
      ChangeNoteUtil changeNoteUtil,
      AbstractChangeNotes.Args changeNotesArgs,
      @Assisted CurrentUser user,
      @Assisted Change change) {
    this.noteUtil = changeNoteUtil;
    this.notesArgs = changeNotesArgs;

    this.user = user;
    this.change = change;
  }

  public NoteDbChangeState updateComment(
      Repository repo, Ref metaRef, Comment target, String newMsg)
      throws IOException, ConfigInvalidException {
    List<ChangeNotesCommitData> commitDataList =
        getCommitDataList(repo, change.getId(), target.key.uuid, metaRef.getObjectId());
    if (commitDataList.size() == 0) {
      throw new IllegalStateException(
          String.format(
              "unexpected for comment %s to be contained in the first commit of the change",
              target.key));
    }

    // Redo commits.
    ObjectId newHeadId =
        redoCommits(
            repo,
            commitDataList.get(0),
            commitDataList.subList(1, commitDataList.size()),
            target.key.uuid,
            newMsg);

    NoteDbChangeState state = null;
    // Update the meta ref head.
    if (!newHeadId.equals(ObjectId.zeroId()) && !newHeadId.equals(metaRef.getObjectId())) {
      updateMetaRefHeadToTargetCommit(repo, metaRef.getName(), newHeadId, metaRef.getObjectId());
      NoteDbChangeState.Delta delta =
          NoteDbChangeState.Delta.create(change.getId(), Optional.of(newHeadId), null);
      state = NoteDbChangeState.applyDelta(change, delta);
    } else {
      throw new IllegalStateException("fail to delete comment");
    }

    return state;
  }

  /**
   * Get the comment at the commit. Note these comments include the comments put in by the previous
   * commits.
   *
   * @throws IOException
   * @throws ConfigInvalidException
   */
  @VisibleForTesting
  public static Map<String, Comment> getCommitPublishedComments(
      Change.Id id,
      ObjectId commitId,
      ChangeNotesRevWalk revWalk,
      ChangeNoteUtil util,
      AbstractChangeNotes.Args changeNotesArgs)
      throws IOException, ConfigInvalidException {
    ChangeNotesParser notesParser =
        new ChangeNotesParser(id, commitId, revWalk, util, changeNotesArgs.metrics);
    ImmutableListMultimap<RevId, Comment> commentMap = notesParser.parseAll().publishedComments();
    return commentMap
        .entries()
        .stream()
        .collect(Collectors.toMap(e -> e.getValue().key.uuid, e -> e.getValue()));
  }

  /**
   * Get the commit list starting with the parent of the commit which puts in the target comment and
   * ending with the tip commit.
   *
   * @return An empty list will be returned if the target comment is found in the first commit of
   *     the change, which violates our assumption that the first commit should not contain any
   *     comment.
   * @throws IOException
   * @throws ConfigInvalidException
   */
  private List<ChangeNotesCommitData> getCommitDataList(
      Repository repo, Change.Id changeId, String targetUUID, ObjectId tipObjId)
      throws IOException, ConfigInvalidException {
    try (ChangeNotesRevWalk walk = ChangeNotesCommit.newRevWalk(repo);
        ChangeNotesRevWalk walk2 = ChangeNotesCommit.newRevWalk(repo)) {
      walk.markStart(walk.parseCommit(tipObjId));

      List<ChangeNotesCommitData> commitDataList = new ArrayList<>();
      ChangeNotesCommit commit;
      while ((commit = walk.next()) != null) {
        walk2.reset();
        Map<String, Comment> commentsMap =
            getCommitPublishedComments(changeId, commit.getId(), walk2, noteUtil, notesArgs);
        commitDataList.add(0, new ChangeNotesCommitData(commit, commentsMap));
        if (!commentsMap.containsKey(targetUUID)) {
          // Return at the parent commit of the target comment.
          return commitDataList;
        }
      }
    }
    return new ArrayList<>();
  }

  /**
   * Redo the commits in the redoList.
   *
   * @param repo the target repository.
   * @param head the parent for the first redo commit.
   * @param redoList the commits will be redone from the first to the last.
   * @param targetUUID the uuid of the target comment.
   * @param newMsg the new message for the target comment.
   * @throws IOException
   * @throws ConfigInvalidException
   */
  private ObjectId redoCommits(
      Repository repo,
      ChangeNotesCommitData head,
      List<ChangeNotesCommitData> redoList,
      String targetUUID,
      String newMsg)
      throws IOException, ConfigInvalidException {
    ObjectId parentId = head.commit.getId();
    ObjectId curHeadId = parentId;

    try (ObjectInserter inserter = repo.newObjectInserter()) {
      for (int i = 0; i < redoList.size(); ++i) {
        Map<String, Comment> preMap = (i == 0) ? head.commentsMap : redoList.get(i - 1).commentsMap;
        List<Comment> commentList =
            getUpdatedCommentList(
                redoList.get(i), preMap, redoList.get(i).commentsMap, targetUUID, newMsg);

        // The committer of the first commit, which puts in the target comment, will be updated.
        PersonIdent committer =
            (i == 0)
                ? user.asIdentifiedUser().newCommitterIdent(TimeUtil.nowTs(), TimeZone.getDefault())
                : redoList.get(i).commit.getCommitterIdent();

        curHeadId =
            redoCommit(repo, redoList.get(i).commit, parentId, inserter, committer, commentList);
        parentId = curHeadId;
      }
    }

    return curHeadId;
  }

  /**
   * Redo one commit.
   *
   * @param repo the target repository.
   * @param commit the commit to be redone.
   * @param parentId the objectId of the parent commit.
   * @param objectInserter the ObjectInserter for the redo process.
   * @param committer the committer of the commit.
   * @param putCommentList the comments put in by this commit.
   * @return the objectId of the new commit.
   * @throws IOException
   * @throws ConfigInvalidException
   */
  private ObjectId redoCommit(
      Repository repo,
      ChangeNotesCommit commit,
      ObjectId parentId,
      ObjectInserter objectInserter,
      PersonIdent committer,
      List<Comment> putCommentList)
      throws IOException, ConfigInvalidException {
    RevisionNoteMap<ChangeRevisionNote> revNotesMap;
    try (ChangeNotesRevWalk walker = ChangeNotesCommit.newRevWalk(repo)) {
      NoteMap noteMap;
      if (!parentId.equals(ObjectId.zeroId())) {
        noteMap = NoteMap.read(walker.getObjectReader(), walker.parseCommit(parentId));
      } else {
        noteMap = NoteMap.newEmptyMap();
      }
      revNotesMap =
          RevisionNoteMap.parse(
              noteUtil,
              change.getId(),
              walker.getObjectReader(),
              noteMap,
              PatchLineComment.Status.PUBLISHED);
    }

    Set<RevId> updatedRevs = new HashSet<>(revNotesMap.revisionNotes.size());
    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(revNotesMap);

    for (Comment c : putCommentList) {
      cache.get(new RevId(c.revId)).putComment(c);
    }

    Map<RevId, RevisionNoteBuilder> builders = cache.getBuilders();
    for (Map.Entry<RevId, RevisionNoteBuilder> e : builders.entrySet()) {
      updatedRevs.add(e.getKey());
      ObjectId id = ObjectId.fromString(e.getKey().get());
      byte[] data = e.getValue().build(noteUtil, noteUtil.getWriteJson());
      if (data.length == 0) {
        revNotesMap.noteMap.remove(id);
      }
      revNotesMap.noteMap.set(id, objectInserter.insert(OBJ_BLOB, data));
    }

    CommitBuilder cb = new CommitBuilder();
    cb.setParentId(parentId);
    cb.setTreeId(revNotesMap.noteMap.writeTree(objectInserter));

    cb.setCommitter(committer);
    cb.setMessage(commit.getFullMessage());
    cb.setAuthor(commit.getAuthorIdent());
    cb.setEncoding(commit.getEncoding());

    ObjectId currentCommitId = objectInserter.insert(cb);
    objectInserter.flush();
    return currentCommitId;
  }

  /**
   * Get the comments put in by the current commit.
   *
   * @param parMap the comment map of the parent commit.
   * @param curMap the comment map of the current commit.
   * @param targetUUID the uuid of the target comment.
   * @param newMsg the new message of the target comment. It will be used to overwrite the old
   *     message when removeAllData is false.
   * @return The comment list put in by this commit.
   */
  private List<Comment> getUpdatedCommentList(
      ChangeNotesCommitData cd,
      Map<String, Comment> parMap,
      Map<String, Comment> curMap,
      String targetUUID,
      String newMsg) {
    List<Comment> commentList = new ArrayList<>();
    for (String key : curMap.keySet()) {
      if (!parMap.containsKey(key)) {
        Comment comment = curMap.get(key);
        if (key.equals(targetUUID)) {
          comment.message = newMsg;
        }
        commentList.add(comment);
      }
    }

    // Check whether there are some comments removed by this commit.
    for (String key : parMap.keySet()) {
      if (!curMap.containsKey(key)) {
        throw new IllegalStateException(
            String.format(
                "unexpected that comment %s was removed by commit %s", key, cd.commit.getId()));
      }
    }

    return commentList;
  }

  private void updateMetaRefHeadToTargetCommit(
      Repository repo, String metaRefStr, ObjectId newTip, ObjectId oldTip) throws IOException {
    RefUpdate refUpdate = repo.getRefDatabase().newUpdate(metaRefStr, true);
    refUpdate.setExpectedOldObjectId(oldTip);
    refUpdate.setNewObjectId(newTip);
    refUpdate.setRefLogIdent(user.asIdentifiedUser().newRefLogIdent());
    refUpdate.setRefLogMessage("Removed a comment", true);

    RefUpdate.Result result = refUpdate.forceUpdate();
    switch (result) {
      case FAST_FORWARD:
      case NEW:
      case NO_CHANGE:
      case FORCED:
        break;
      case IO_FAILURE:
      case LOCK_FAILURE:
      case NOT_ATTEMPTED:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      case RENAMED:
      default:
        throw new IOException(String.format("Failed to update ref %s: %s", metaRefStr, result));
    }
  }

  public static class ChangeNotesCommitData {
    public ChangeNotesCommit commit;
    public Map<String, Comment> commentsMap;

    public ChangeNotesCommitData(ChangeNotesCommit commit, Map<String, Comment> commentsMap) {
      this.commit = checkNotNull(commit);
      this.commentsMap = checkNotNull(commentsMap);
    }
  }
}
