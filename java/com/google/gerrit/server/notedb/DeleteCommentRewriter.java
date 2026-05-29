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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.RefNames;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Deletes a published comment from NoteDb by rewriting the commit history. Instead of deleting the
 * whole comment, it just replaces the comment's message with a new message.
 */
public class DeleteCommentRewriter implements NoteDbRewriter {

  public interface Factory {
    /**
     * Creates a DeleteCommentRewriter instance.
     *
     * @param id the id of the change which contains the target comment.
     * @param uuid the uuid of the target comment.
     * @param newMessage the message used to replace the old message of the target comment.
     * @return the DeleteCommentRewriter instance
     */
    DeleteCommentRewriter create(
        Change.Id id, @Assisted("uuid") String uuid, @Assisted("newMessage") String newMessage);
  }

  private final ChangeNoteUtil noteUtil;
  private final Change.Id changeId;
  private final String uuid;
  private final String newMessage;

  @Inject
  DeleteCommentRewriter(
      ChangeNoteUtil noteUtil,
      @Assisted Change.Id changeId,
      @Assisted("uuid") String uuid,
      @Assisted("newMessage") String newMessage) {
    this.noteUtil = noteUtil;
    this.changeId = changeId;
    this.uuid = uuid;
    this.newMessage = newMessage;
  }

  @Override
  public String getRefName() {
    return RefNames.changeMetaRef(changeId);
  }

  @Override
  public ObjectId rewriteCommitHistory(RevWalk revWalk, ObjectInserter inserter, ObjectId currTip)
      throws IOException, ConfigInvalidException {
    checkArgument(!currTip.equals(ObjectId.zeroId()));

    // Walk from the first commit of the branch.
    revWalk.reset();
    revWalk.markStart(revWalk.parseCommit(currTip));
    revWalk.sort(RevSort.REVERSE);

    ObjectReader reader = revWalk.getObjectReader();
    RevCommit newTipCommit = revWalk.next(); // The first commit will not be rewritten.
    Map<String, HumanComment> parentComments =
        getPublishedComments(noteUtil, reader, NoteMap.read(reader, newTipCommit));

    boolean rewrite = false;
    RevCommit originalCommit;
    while ((originalCommit = revWalk.next()) != null) {
      NoteMap noteMap = NoteMap.read(reader, originalCommit);
      Map<String, HumanComment> currComments = getPublishedComments(noteUtil, reader, noteMap);

      if (!rewrite && currComments.containsKey(uuid)) {
        rewrite = true;
      }

      if (!rewrite) {
        parentComments = currComments;
        newTipCommit = originalCommit;
        continue;
      }

      List<HumanComment> putInComments = getPutInComments(parentComments, currComments);
      List<HumanComment> deletedComments = getDeletedComments(parentComments, currComments);
      newTipCommit =
          revWalk.parseCommit(
              rewriteCommit(
                  originalCommit, newTipCommit, inserter, reader, putInComments, deletedComments));
      parentComments = currComments;
    }

    return newTipCommit;
  }

  /**
   * Gets all the comments which are presented at a commit. Note they include the comments put in by
   * the previous commits.
   */
  @VisibleForTesting
  public static Map<String, HumanComment> getPublishedComments(
      ChangeNoteJson changeNoteJson, ObjectReader reader, NoteMap noteMap)
      throws IOException, ConfigInvalidException {
    return RevisionNoteMap.parse(changeNoteJson, reader, noteMap, HumanComment.Status.PUBLISHED)
        .revisionNotes
        .values()
        .stream()
        .flatMap(n -> n.getEntities().stream())
        .collect(toMap(c -> c.key.uuid, Function.identity()));
  }

  public static Map<String, HumanComment> getPublishedComments(
      ChangeNoteUtil noteUtil, ObjectReader reader, NoteMap noteMap)
      throws IOException, ConfigInvalidException {
    return getPublishedComments(noteUtil.getChangeNoteJson(), reader, noteMap);
  }

  /**
   * Gets the comments put in by the current commit. The message of the target comment will be
   * replaced by the new message.
   *
   * @param parMap the comment map of the parent commit.
   * @param curMap the comment map of the current commit.
   * @return The comments put in by the current commit.
   */
  private List<HumanComment> getPutInComments(
      Map<String, HumanComment> parMap, Map<String, HumanComment> curMap) {
    List<HumanComment> comments = new ArrayList<>();
    for (String key : curMap.keySet()) {
      if (!parMap.containsKey(key)) {
        HumanComment comment = curMap.get(key);
        if (key.equals(uuid)) {
          comment.message = newMessage;
          comment.unresolved = false;
        }
        comments.add(comment);
      }
    }
    return comments;
  }

  /**
   * Gets the comments deleted by the current commit.
   *
   * @param parMap the comment map of the parent commit.
   * @param curMap the comment map of the current commit.
   * @return The comments deleted by the current commit.
   */
  private List<HumanComment> getDeletedComments(
      Map<String, HumanComment> parMap, Map<String, HumanComment> curMap) {
    return parMap.entrySet().stream()
        .filter(c -> !curMap.containsKey(c.getKey()))
        .map(Map.Entry::getValue)
        .collect(toList());
  }

  /**
   * Rewrites one commit.
   *
   * @param originalCommit the original commit to be rewritten.
   * @param parentCommit the parent of the new commit.
   * @param inserter the {@code ObjectInserter} for the rewrite process.
   * @param reader the {@code ObjectReader} for the rewrite process.
   * @param putInComments the comments put in by this commit.
   * @param deletedComments the comments deleted by this commit.
   * @return the {@code objectId} of the new commit.
   */
  private ObjectId rewriteCommit(
      RevCommit originalCommit,
      RevCommit parentCommit,
      ObjectInserter inserter,
      ObjectReader reader,
      List<HumanComment> putInComments,
      List<HumanComment> deletedComments)
      throws IOException, ConfigInvalidException {
    RevisionNoteMap<ChangeRevisionNote> revNotesMap =
        RevisionNoteMap.parse(
            noteUtil.getChangeNoteJson(),
            reader,
            NoteMap.read(reader, parentCommit),
            HumanComment.Status.PUBLISHED);
    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(revNotesMap);

    for (HumanComment c : putInComments) {
      cache.get(c.getCommitId()).putComment(c);
    }

    for (HumanComment c : deletedComments) {
      cache.get(c.getCommitId()).deleteComment(c.key);
    }

    Map<ObjectId, RevisionNoteBuilder> builders = cache.getBuilders();
    for (Map.Entry<ObjectId, RevisionNoteBuilder> entry : builders.entrySet()) {
      ObjectId objectId = entry.getKey();
      byte[] data = entry.getValue().build(noteUtil.getChangeNoteJson());
      if (data.length == 0) {
        revNotesMap.noteMap.remove(objectId);
      } else {
        revNotesMap.noteMap.set(objectId, inserter.insert(OBJ_BLOB, data));
      }
    }

    CommitBuilder cb = new CommitBuilder();
    cb.setParentId(parentCommit);
    cb.setTreeId(revNotesMap.noteMap.writeTree(inserter));
    cb.setMessage(originalCommit.getFullMessage());
    cb.setCommitter(originalCommit.getCommitterIdent());
    cb.setAuthor(originalCommit.getAuthorIdent());
    cb.setEncoding(originalCommit.getEncoding());

    return inserter.insert(cb);
  }
}
