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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ChangeCommentUpdate {
  public interface Factory {
    ChangeCommentUpdate create(Project.NameKey projectKey, Change changeId);
  }

  private final Provider<CurrentUser> currentUserProvider;
  private final GitRepositoryManager repoManager;
  private final ChangeNoteUtil noteUtil;
  private final AbstractChangeNotes.Args notesArgs;

  private Repository repo;
  private Change change;
  private Change.Id changeId;
  private String metaRefStr;

  @AssistedInject
  public ChangeCommentUpdate(Provider<CurrentUser> currentUserProvider,
      GitRepositoryManager repositoryManager,
      ChangeNoteUtil changeNoteUtil,
      AbstractChangeNotes.Args changeNotesArgs,
      @Assisted Project.NameKey projectKey,
      @Assisted Change change) throws IOException {
    this.currentUserProvider = currentUserProvider;
    this.repoManager = repositoryManager;
    this.noteUtil = changeNoteUtil;
    this.notesArgs = changeNotesArgs;

    this.repo = repoManager.openRepository(checkNotNull(projectKey));
    this.change = checkNotNull(change);
    this.changeId = checkNotNull(change.getId());
    this.metaRefStr = RefNames.changeMetaRef(changeId);
  }

  public void updateComment(Comment target, String newMsg,
      Boolean removeAllData) throws IOException, ConfigInvalidException,
      ResourceNotFoundException {
    Ref metaRef = repo.exactRef(metaRefStr);
    checkNotNull(metaRef);

    // get the commits from the tip to the parent of the commit
    // with the target comment.
    List<ChangeNotesCommitData> commitDataList =
        getCommitDataList(target.key.uuid, metaRef.getObjectId());

    // redo commits
    ObjectId newHeadId = redoCommitList(commitDataList, target,
        newMsg, removeAllData);

    // update the meta ref head
    if (!newHeadId.equals(ObjectId.zeroId())
        && !newHeadId.equals(metaRef.getObjectId())) {
      updateMetaRefHeadToTargetCommit(newHeadId, metaRef.getObjectId());

      NoteDbChangeState.Delta delta = NoteDbChangeState.Delta.create(
          changeId, Optional.of(newHeadId), null);
      NoteDbChangeState.applyDelta(change, delta);
    }

    repo.close();
  }

  private List<ChangeNotesCommitData> getCommitDataList(String targetUUID,
      ObjectId tipObjId) throws IOException, ConfigInvalidException {
    List<ChangeNotesCommitData> commitDataList = new ArrayList<>();

    Boolean found = false;
    try (ChangeNotesRevWalk walk = ChangeNotesCommit.newRevWalk(repo)) {
      walk.markStart(walk.parseCommit(tipObjId));
      ChangeNotesCommit commit;
      while ((commit = walk.next()) != null) {
        Map<String, Comment> commentsMap = getCommitPublishedComments(
            repo, changeId, noteUtil, notesArgs, commit.getId());
        commitDataList.add(0, new ChangeNotesCommitData(commit, commentsMap));
        if (!commentsMap.containsKey(targetUUID)) {
          // break at the parent commit of the target comment
          found = true;
          break;
        }
      }
    }

    return found ? commitDataList : new ArrayList<>();
  }

  private Map<String, Comment> getCommitPublishedComments(
      Repository repository, Change.Id id, ChangeNoteUtil util,
      AbstractChangeNotes.Args changeNotesArgs, ObjectId commitId)
      throws IOException, ConfigInvalidException {
    try (ChangeNotesRevWalk rw = ChangeNotesCommit.newRevWalk(repository)) {
      ChangeNotesParser notesParser = new ChangeNotesParser(
          id, commitId, rw, util, changeNotesArgs.metrics);

      ImmutableListMultimap<RevId, Comment> commentMap =
          notesParser.parseAll().publishedComments();

      return commentMap.entries().stream().collect(
          Collectors.toMap(e -> e.getValue().key.uuid, e -> e.getValue()));
    }
  }

  private ObjectId redoCommitList(List<ChangeNotesCommitData> commitDataList,
      Comment target, String newMsg, Boolean removeAllData) throws IOException,
      ConfigInvalidException, ResourceNotFoundException {
    if (commitDataList.size() <= 1) {
      throw new ResourceNotFoundException("comment not found: " + target);
    }

    ObjectId parentId = commitDataList.get(0).commit.getId();
    ObjectId curHeadId = parentId;
    ObjectInserter inserter = repo.newObjectInserter();
    for (int i = 1; i < commitDataList.size(); ++i) {
      List<Comment> commentList = getCommentList(
          commitDataList.get(i - 1).commentsMap,
          commitDataList.get(i).commentsMap, target.key.uuid,
          newMsg, removeAllData);

      if (commentList != null) {
        curHeadId = redoCommit(commitDataList.get(i).commit,
            parentId, inserter, commentList);
        parentId = curHeadId;
      }
    }

    inserter.close();
    return curHeadId;
  }

  private ObjectId redoCommit(ChangeNotesCommit commit, ObjectId parentId,
      ObjectInserter objectInserter, List<Comment> putCommentList)
      throws IOException, ConfigInvalidException {
    ChangeNotesRevWalk rw = ChangeNotesCommit.newRevWalk(repo);
    ChangeNotesParser notesParser = new ChangeNotesParser(changeId, parentId,
        rw, noteUtil, notesArgs.metrics);
    notesParser.parseAll();
    RevisionNoteMap<ChangeRevisionNote> rnm = notesParser.getRevisionNoteMap();
    rw.close();

    Set<RevId> updatedRevs =
        Sets.newHashSetWithExpectedSize(rnm.revisionNotes.size());
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

    cb.setCommitter(commit.getCommitterIdent());
    cb.setAuthor(commit.getAuthorIdent());
    // comments number doesn't change, no update for message
    cb.setMessage(commit.getFullMessage());
    cb.setEncoding(commit.getEncoding());

    ObjectId currentCommitId = objectInserter.insert(cb);
    objectInserter.flush();

    return currentCommitId;
  }

  private List<Comment> getCommentList(Map<String, Comment> preMap,
      Map<String, Comment> curMap, String targetUUID, String newMsg,
      Boolean removeAllData) {
    List<Comment> commentList = new ArrayList<>();
    Boolean found = false;
    for (String key : curMap.keySet()) {
      if (!preMap.containsKey(key)) {
        Comment comment = curMap.get(key);
        if (key.equals(targetUUID)) {
          found = true;
          if (removeAllData) {
            continue;
          }
          comment.message = newMsg;
        }
        commentList.add(comment);
      }
    }

    // this commit only contains the target comment, no need to redo
    if (found && commentList.size() == 0) {
      return null;
    }
    return commentList;
  }

  private void updateMetaRefHeadToTargetCommit(
      ObjectId targetCommitObjId, ObjectId tip) throws IOException {
    RefUpdate refUpdate = repo.getRefDatabase().newUpdate(metaRefStr, true);
    refUpdate.setExpectedOldObjectId(tip);
    refUpdate.setNewObjectId(targetCommitObjId);
    refUpdate.setRefLogIdent(currentUserProvider.get()
        .asIdentifiedUser().newRefLogIdent());
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

  private class ChangeNotesCommitData {
    private ChangeNotesCommit commit;
    private Map<String, Comment> commentsMap;

    public ChangeNotesCommitData(ChangeNotesCommit commit,
        Map<String, Comment> commentsMap) {
      this.commit = checkNotNull(commit);
      this.commentsMap = checkNotNull(commentsMap);
    }
  }
}
