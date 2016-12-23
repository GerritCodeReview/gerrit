// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class ChangeNotesCommentUpdate {
  public static final String REMOVED_COMMENT_MSG = "comment removed";

  private GitRepositoryManager repoManager;
  private ChangeNoteUtil noteUtil;
  private AbstractChangeNotes.Args notesArgs;

  private Repository repo;
  private Change.Id changeId;
  private String metaRefStr;

  @Inject
  public ChangeNotesCommentUpdate(GitRepositoryManager repositoryManager,
      ChangeNoteUtil changeNoteUtil, AbstractChangeNotes.Args changeNotesArgs) {
    this.repoManager = repositoryManager;
    this.noteUtil = changeNoteUtil;
    this.notesArgs = changeNotesArgs;
  }

  public static ImmutableListMultimap<RevId, Comment>
  getCommitPublishedComments(Repository repository, Change.Id id,
      ChangeNoteUtil util, AbstractChangeNotes.Args changeNotesArgs,
      ObjectId commitId) throws Exception {
    if (commitId == null) {
      ImmutableListMultimap.Builder<RevId, Comment> builder =
          ImmutableListMultimap.builder();
      return builder.build();
    }

    try (ChangeNotesRevWalk rw = ChangeNotesCommit.newRevWalk(repository)) {
      ChangeNotesParser notesParser = new ChangeNotesParser(
          id, commitId, rw, util, changeNotesArgs.metrics);
      return notesParser.parseAll().publishedComments();
    }
  }

  public static Map<String, Comment> toCommentUUIDMap(
      ImmutableListMultimap<RevId, Comment> currentCommentsMap) {
    Map<String, Comment> commentKeyMap = new HashMap<>();
    if(currentCommentsMap == null || currentCommentsMap.size() == 0) {
      return commentKeyMap;
    }

    for (Comment c : currentCommentsMap.values().asList()) {
      commentKeyMap.put(c.key.uuid, c);
    }
    return commentKeyMap;
  }

  public void init(Project.NameKey projectKey, Change.Id changeId)
      throws Exception {
    if (projectKey == null) {
      throw new Exception("Project NameKey is null");
    }

    if(changeId == null) {
      throw new Exception("Change.Id is null");
    }

    this.repo = repoManager.openRepository(projectKey);
    this.changeId = changeId;
    this.metaRefStr = RefNames.changeMetaRef(this.changeId);
  }

  public void deleteComment(Comment targetComment) throws  Exception {
    Ref metaRef = repo.exactRef(metaRefStr);
    if (metaRef == null) {
      throw new Exception("open meta branch fail");
    }

    // get the original commit history
    List<ChangeNotesCommit> commitList = getAllChangeNotesCommits(repo,
        metaRef.getObjectId());
    // get each commit data, including comments
    List<ChangeNotesCommitData> commitDataList = parseCommitData(commitList);
    // find the commit, which first put the target comment
    Tuple commentLoc = findTargetComment(commitDataList, targetComment);
    if (commentLoc.first < 0
        || commentLoc.second >= commitDataList.size() - 1) {
      return; // not found
    }

    // remove target comment message from the commit
    commitDataList.get(commentLoc.first).putCommentList.
        get(commentLoc.second).message = REMOVED_COMMENT_MSG;

    // redo the commits
    ObjectId newHeadId = redoCommitList(commitDataList, commentLoc.first);

    if (newHeadId.equals(ObjectId.zeroId())) {
      return; // fail
    }

    // update the meta ref head
    updateMetaRefHeadToTargetCommit(newHeadId);
  }

  public List<ChangeNotesCommit> getAllChangeNotesCommits(Repository repo,
      ObjectId tipObjId) throws Exception {
    List<ChangeNotesCommit> commitList = new ArrayList<>();
    if (repo == null || tipObjId == null) {
      return commitList;
    }

    try (ChangeNotesRevWalk walk = ChangeNotesCommit.newRevWalk(repo)) {
      walk.markStart(walk.parseCommit(tipObjId));
      ChangeNotesCommit commit = null;
      while ((commit = walk.next()) != null) {
        commitList.add(commit);
      }
      walk.dispose();
    }
    return commitList;
  }

  private ObjectId redoCommitList(List<ChangeNotesCommitData> commitDataList,
      int startIdx) throws Exception {
    if (commitDataList == null || commitDataList.size() == 0
        || startIdx < 0 || startIdx >= commitDataList.size() - 1) {
      return ObjectId.zeroId();
    } else {
      ObjectId parentId = commitDataList.get(startIdx + 1).commit.getId();
      ObjectId curHeadId = parentId;
      ObjectInserter insert = repo.newObjectInserter();

      for (int i = startIdx; i >= 0; --i) {
        if (parentId.equals(ObjectId.zeroId())) {
          return curHeadId;
        }
        curHeadId = redoCommit(commitDataList.get(i), parentId, insert);
        parentId = curHeadId;
      }

      return  curHeadId;
    }
  }

  private ObjectId redoCommit(ChangeNotesCommitData commitData, ObjectId parentId,
      ObjectInserter objectInsert) throws Exception {
    ObjectId currentCommitId = ObjectId.zeroId();
    if (commitData == null || parentId == null || objectInsert == null) {
      return currentCommitId;
    }

    ChangeNotesRevWalk rw = ChangeNotesCommit.newRevWalk(repo);
    ChangeNotesParser notesParser = new ChangeNotesParser(changeId, parentId,
        rw, noteUtil, notesArgs.metrics);
    notesParser.parseAll();
    RevisionNoteMap<ChangeRevisionNote> rnm = notesParser.getRevisionNoteMap();
    Set<RevId> updatedRevs =
        Sets.newHashSetWithExpectedSize(rnm.revisionNotes.size());
    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(rnm);

    for (Comment c : commitData.putCommentList) {
      cache.get(new RevId(c.revId)).putComment(c);
    }

    for (Comment k : commitData.deleteCommentList) {
      cache.get(new RevId(k.revId)).deleteComment(k.key);
    }

    Map<RevId, RevisionNoteBuilder> builders = cache.getBuilders();
    for (Map.Entry<RevId, RevisionNoteBuilder> e : builders.entrySet()) {
      updatedRevs.add(e.getKey());
      ObjectId id = ObjectId.fromString(e.getKey().get());
      byte[] data = e.getValue().build(noteUtil, noteUtil.getWriteJson());
      if (data.length == 0) {
        rnm.noteMap.remove(id);
      } else {
        rnm.noteMap.set(id, objectInsert.insert(OBJ_BLOB, data));
      }
    }

    CommitBuilder cb = new CommitBuilder();
    cb.setParentId(parentId);
    cb.setTreeId(rnm.noteMap.writeTree(objectInsert));

    cb.setCommitter(commitData.commit.getCommitterIdent());
    cb.setAuthor(commitData.commit.getAuthorIdent());
    // comments number doesn't change, no update for message
    cb.setMessage(commitData.commit.getFullMessage());
    cb.setEncoding(commitData.commit.getEncoding());

    currentCommitId = objectInsert.insert(cb);
    objectInsert.flush();
    return currentCommitId;
  }

  /**
   * find from the end
   *
   * @param commitDataList the commit list, start with the head commit of the ref
   */
  private Tuple findTargetComment(
      List<ChangeNotesCommitData> commitDataList, Comment targetComment) {
    if (commitDataList == null || targetComment == null) {
      return new Tuple(-1, -1);
    }

    for (int i = commitDataList.size() - 1; i >= 0; --i) {
      ChangeNotesCommitData commitData = commitDataList.get(i);
      for (int j = 0; j < commitData.putCommentList.size(); ++j) {
        Comment comment = commitData.putCommentList.get(j);
        if (comment.key.uuid.equals(targetComment.key.uuid)) {
          return new Tuple(i, j);
        }
      }
    }

    return new Tuple(-1, -1);
  }

  /**
   * parse the comment data of each commit in the commit list
   *
   * @param commitList the commit list, start with the head commit of the ref
   * @throws Exception
   */
  private List<ChangeNotesCommitData> parseCommitData(
      List<ChangeNotesCommit> commitList) throws Exception {
    if (commitList == null) {
      return new ArrayList<>();
    }

    List<ChangeNotesCommitData> commitDataList = new ArrayList<>();
    Map<String, Comment> preCommentsMap = new HashMap<>();

    for (int i = commitList.size() - 1; i >= 0; --i) {
      Map<String, Comment> currentCommentsMap = toCommentUUIDMap(
          getCommitPublishedComments(repo, changeId, noteUtil,
              notesArgs, commitList.get(i).getId()));
      ChangeNotesCommitData commitData = new ChangeNotesCommitData(
          commitList.get(i), currentCommentsMap, preCommentsMap);
      commitDataList.add(0, commitData);
      preCommentsMap = currentCommentsMap;
    }

    return commitDataList;
  }

  private void updateMetaRefHeadToTargetCommit(ObjectId targetCommitObjId)
      throws IOException {
    if (targetCommitObjId == null) {
      return;
    }

    RefUpdate refUpdate = repo.getRefDatabase().newUpdate(metaRefStr, true);
    refUpdate.setNewObjectId(targetCommitObjId); // can't be null
    refUpdate.forceUpdate();
  }

  private class ChangeNotesCommitData {
    ChangeNotesCommit commit;
    List<Comment> putCommentList; // comments to be inserted by the commit
    List<Comment> deleteCommentList; // comments to be deleted by the commit

    public ChangeNotesCommitData(ChangeNotesCommit commit,
        List<Comment> putCommentList, List<Comment> deleteCommentList) {
      this.commit = (commit != null)
          ? commit : new ChangeNotesCommit(ObjectId.zeroId());
      this.putCommentList = (putCommentList != null)
          ? putCommentList : new ArrayList<>();
      this.deleteCommentList = (deleteCommentList != null)
          ? deleteCommentList : new ArrayList<>();
    }

    public ChangeNotesCommitData(ChangeNotesCommit commit,
        Map<String, Comment> curCommentsMap,
        Map<String, Comment> preCommentsMap) {
      this.commit = (commit != null)
          ? commit : new ChangeNotesCommit(ObjectId.zeroId());
      this.putCommentList = new ArrayList<>();
      this.deleteCommentList = new ArrayList<>();

      if (curCommentsMap == null || preCommentsMap == null) {
        return;
      }

      // get 'put' comment list
      for (Entry<String, Comment> entry : curCommentsMap.entrySet()) {
        if (!preCommentsMap.containsKey(entry.getKey())) {
          putCommentList.add(entry.getValue());
        }
      }

      // get 'delete' comment list
      for (Entry<String, Comment> entry : preCommentsMap.entrySet()) {
        if (!curCommentsMap.containsKey(entry.getKey())) {
          deleteCommentList.add(entry.getValue());
        }
      }
    }
  }

  private class Tuple {
    public int first;
    public int second;

    public Tuple(int first, int second) {
      this.first = first;
      this.second = second;
    }
  }
}
