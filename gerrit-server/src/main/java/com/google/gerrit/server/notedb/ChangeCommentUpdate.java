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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * Delete a published comment from NoteDb by rewriting the commit history.
 * It allows to rewrite the comment's message or delete the whole comment
 * permanently.
 *
 * Note that this implementation is based on two assumptions.
 * The first assumption is that the first commit will not contain any comment.
 * The second assumption is that each commit will only put in new comments
 * and will never delete existing comments. If these assumptions are not true
 * any more, this implementation should be updated to support.
 */
public class ChangeCommentUpdate {
  public interface Factory {
    ChangeCommentUpdate create(CurrentUser user, Project.NameKey projectKey,
        Change changeId);
  }

  private final GitRepositoryManager repoManager;
  private final ChangeNoteUtil noteUtil;
  private final AbstractChangeNotes.Args notesArgs;

  private final CurrentUser user;
  private final Change change;
  private final Project.NameKey projectKey;

  @AssistedInject
  public ChangeCommentUpdate(
      GitRepositoryManager repositoryManager,
      ChangeNoteUtil changeNoteUtil,
      AbstractChangeNotes.Args changeNotesArgs,
      @Assisted CurrentUser user,
      @Assisted Project.NameKey projectKey,
      @Assisted Change change) throws IOException {
    this.repoManager = repositoryManager;
    this.noteUtil = changeNoteUtil;
    this.notesArgs = changeNotesArgs;

    this.user = user;
    this.change = checkNotNull(change);
    this.projectKey = checkNotNull(projectKey);
  }

  public NoteDbChangeState updateComment(Comment target, String newMsg,
      boolean removeAllData) throws IOException, ConfigInvalidException,
      ResourceNotFoundException {
    try (Repository repo = repoManager.openRepository(projectKey)) {
      String metaRefStr = RefNames.changeMetaRef(change.getId());
      Ref metaRef = repo.exactRef(metaRefStr);
      if (metaRef == null) {
        throw new ResourceNotFoundException("branch not found: " + metaRefStr);
      }

      List<ChangeNotesCommitData> commitDataList = getCommitDataList(
          repo, change.getId(), target.key.uuid, metaRef.getObjectId());

      if (commitDataList.size() == 0) {
        throw new IllegalStateException(String.format("unexpected for "
            + "comment %s to be contained in the first commit", target.key));
      } else if (commitDataList.size() == 1) {
        // the tip commit doesn't contain the target comment, which means
        // the target comment can't be found in previous commits, either.
        throw new ResourceNotFoundException("comment not found: " + target.key);
      }

      // Redo commits.
      ObjectId newHeadId = redoCommitList(repo, commitDataList.get(0),
          commitDataList.subList(1, commitDataList.size()), target.key.uuid,
          newMsg, removeAllData);

      NoteDbChangeState state = null;
      // Update the meta ref head.
      if (!newHeadId.equals(ObjectId.zeroId())
          && !newHeadId.equals(metaRef.getObjectId())) {
        updateMetaRefHeadToTargetCommit(repo, metaRefStr, newHeadId,
            metaRef.getObjectId());

        NoteDbChangeState.Delta delta = NoteDbChangeState.Delta.create(
            change.getId(), Optional.of(newHeadId), null);
        state = NoteDbChangeState.applyDelta(change, delta);
      }

      return state;
    }
  }

  /**
   * Get the comment at the commit. Note these comments include the comments
   * put in by the previous commits.
   *
   * @throws IOException
   * @throws ConfigInvalidException
   */
  @VisibleForTesting
  public static Map<String, Comment> getCommitPublishedComments(
      Change.Id id, ObjectId commitId, ChangeNotesRevWalk revWalk,
      ChangeNoteUtil util, AbstractChangeNotes.Args changeNotesArgs)
      throws IOException, ConfigInvalidException {
    ChangeNotesParser notesParser = new ChangeNotesParser(
        id, commitId, revWalk, util, changeNotesArgs.metrics);

    ImmutableListMultimap<RevId, Comment> commentMap =
        notesParser.parseAll().publishedComments();

    return commentMap.entries().stream().collect(
        Collectors.toMap(e -> e.getValue().key.uuid, e -> e.getValue()));
  }

  /**
   * Get the commit list starting with the parent of the commit which puts in
   * the target comment and ending with the tip commit.
   *
   * @return An empty list will be returned if the target comment can't be
   * found in any commit.
   * @throws ResourceNotFoundException
   * @throws IOException
   * @throws ConfigInvalidException
   */
  private List<ChangeNotesCommitData> getCommitDataList(Repository repo,
      Change.Id changeId, String targetUUID, ObjectId tipObjId)
      throws ResourceNotFoundException, IOException, ConfigInvalidException {
    try (ChangeNotesRevWalk walk = ChangeNotesCommit.newRevWalk(repo);
        ChangeNotesRevWalk walk2 = ChangeNotesCommit.newRevWalk(repo)) {
      walk.markStart(walk.parseCommit(tipObjId));

      List<ChangeNotesCommitData> commitDataList = new ArrayList<>();
      ChangeNotesCommit commit;
      while ((commit = walk.next()) != null) {
        walk2.reset();
        Map<String, Comment> commentsMap = getCommitPublishedComments(
            changeId, commit.getId(), walk2, noteUtil, notesArgs);
        commitDataList.add(0,
            new ChangeNotesCommitData(commit, commentsMap));
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
   * @param head the parent for the first redo commit.
   * @param redoList the commits will be redone from the first to the last.
   * @param targetUUID the uuid of the target comment.
   * @param newMsg the new message for the target comment.
   * @param removeAllData If true, the whole comment will be removed. If false,
   * only the comment's message will be overwritten.
   * @throws IOException
   * @throws ConfigInvalidException
   * @throws ResourceNotFoundException
   */
  private ObjectId redoCommitList(Repository repo, ChangeNotesCommitData head,
      List<ChangeNotesCommitData> redoList, String targetUUID, String newMsg,
      boolean removeAllData) throws IOException, ConfigInvalidException,
      ResourceNotFoundException {
    ObjectId parentId = head.commit.getId();
    ObjectId curHeadId = parentId;

    try (ObjectInserter inserter = repo.newObjectInserter()) {
      for (int i = 0; i < redoList.size(); ++i) {
        Map<String, Comment> preMap = (i == 0) ?
            head.commentsMap : redoList.get(i - 1).commentsMap;
        List<Comment> commentList = getUpdateCommentList(preMap,
            redoList.get(i).commentsMap, targetUUID, newMsg, removeAllData);

        // the committer of the first commit, which puts in the target comment,
        // will be updated.
        PersonIdent committer = (i == 0)
            ? user.asIdentifiedUser().newCommitterIdent(TimeUtil.nowTs(),
            TimeZone.getDefault())
            : redoList.get(i).commit.getCommitterIdent();

        // A lazy way to update the commit's message when the number of
        // comments change. The short message will contain "(0 comment)"
        // if there is no comment.
        String message = redoList.get(i).commit.getFullMessage();
        if (i == 0 && removeAllData) {
          int num = commentList.size();
          message = message.replace("(" + (num + 1) + " comment)",
              "(" + num + " comment)");
        }

        curHeadId = redoCommit(repo, redoList.get(i).commit, parentId,
            inserter, committer, message, commentList);
        parentId = curHeadId;
      }
    }

    return curHeadId;
  }

  /**
   * Redo one commit.
   *
   * @param commit the commit to be redone.
   * @param parentId the objectId of the parent commit.
   * @param committer the committer of the commit.
   * @param message the message of the commit.
   * @param putCommentList the comments put in by this commit.
   * @return the objectId of the new commit.
   * @throws IOException
   * @throws ConfigInvalidException
   */
  private ObjectId redoCommit(Repository repo, ChangeNotesCommit commit,
      ObjectId parentId, ObjectInserter objectInserter, PersonIdent committer,
      String message, List<Comment> putCommentList)
      throws IOException, ConfigInvalidException {
    RevisionNoteMap<ChangeRevisionNote> rnm;
    try (ChangeNotesRevWalk rw = ChangeNotesCommit.newRevWalk(repo)) {
      NoteMap noteMap;
      if (!parentId.equals(ObjectId.zeroId())) {
        noteMap = NoteMap.read(rw.getObjectReader(), rw.parseCommit(parentId));
      } else {
        noteMap = NoteMap.newEmptyMap();
      }
      rnm = RevisionNoteMap.parse(noteUtil, change.getId(),
          rw.getObjectReader(), noteMap, PatchLineComment.Status.PUBLISHED);
    }

    Set<RevId> updatedRevs = new HashSet<>(rnm.revisionNotes.size());
    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(rnm);

    for (Comment c : putCommentList) {
      cache.get(new RevId(c.revId)).putComment(c);
    }

    Map<RevId, RevisionNoteBuilder> builders = cache.getBuilders();
    for (Map.Entry<RevId, RevisionNoteBuilder> e : builders.entrySet()) {
      updatedRevs.add(e.getKey());
      ObjectId id = ObjectId.fromString(e.getKey().get());
      byte[] data = e.getValue().build(noteUtil, noteUtil.getWriteJson());
      if (data.length == 0) {
        rnm.noteMap.remove(id);
      }
      rnm.noteMap.set(id, objectInserter.insert(OBJ_BLOB, data));
    }

    CommitBuilder cb = new CommitBuilder();
    cb.setParentId(parentId);
    cb.setTreeId(rnm.noteMap.writeTree(objectInserter));

    cb.setCommitter(committer);
    cb.setMessage(message);
    cb.setAuthor(commit.getAuthorIdent());
    cb.setEncoding(commit.getEncoding());

    ObjectId currentCommitId = objectInserter.insert(cb);
    objectInserter.flush();
    return currentCommitId;
  }

  /**
   * Get the comments put in by the current commit.
   *
   * @param preMap the comment map of the parent commit.
   * @param curMap the comment map of the current commit.
   * @param targetUUID the uuid of the target comment.
   * @param newMsg the new message of the target comment. It will be used to
   * overwrite the old message when removeAllData is false.
   * @param removeAllData If true, the target comment will not be included in
   * the comment list. If false, the target comment message will be replaced
   * by newMsg.
   * @return The comment list put in by this commit.
   */
  private List<Comment> getUpdateCommentList(Map<String, Comment> preMap,
      Map<String, Comment> curMap, String targetUUID, String newMsg,
      boolean removeAllData) {
    List<Comment> commentList = new ArrayList<>();
    for (String key : curMap.keySet()) {
      if (!preMap.containsKey(key)) {
        Comment comment = curMap.get(key);
        if (key.equals(targetUUID)) {
          if (removeAllData) {
            continue;
          }
          comment.message = newMsg;
        }
        commentList.add(comment);
      }
    }

    // Check whether there are some comments deleted by this commit.
    for (String key : preMap.keySet()) {
      if (!curMap.containsKey(key)) {
        throw new IllegalStateException(String.format("unexpected for "
            + "comment %s to be deleted by a commit; "
            + "expected that a commit will never delete any comment", key));
      }
    }

    return commentList;
  }

  private void updateMetaRefHeadToTargetCommit(Repository repo,
      String metaRefStr, ObjectId targetCommitObjId, ObjectId tip)
      throws IOException {
    RefUpdate refUpdate = repo.getRefDatabase().newUpdate(metaRefStr, true);
    refUpdate.setExpectedOldObjectId(tip);
    refUpdate.setNewObjectId(targetCommitObjId);
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
        throw new IOException(String.format("Failed to update ref %s: %s",
            metaRefStr, result));
    }
  }

  public static class ChangeNotesCommitData {
    public ChangeNotesCommit commit;
    public Map<String, Comment> commentsMap;

    public ChangeNotesCommitData(ChangeNotesCommit commit,
        Map<String, Comment> commentsMap) {
      this.commit = checkNotNull(commit);
      this.commentsMap = checkNotNull(commentsMap);
    }
  }
}
