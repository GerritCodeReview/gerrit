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
import static com.google.gerrit.reviewdb.client.PatchLineComment.Status.PUBLISHED;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
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
      throws IOException, ConfigInvalidException, StorageException {
    checkArgument(!currTip.equals(ObjectId.zeroId()));

    // Walk from the first commit of the branch.
    revWalk.reset();
    revWalk.markStart(revWalk.parseCommit(currTip));
    revWalk.sort(RevSort.REVERSE);

    ObjectReader reader = revWalk.getObjectReader();
    RevCommit newTipCommit = revWalk.next(); // The first commit will not be rewritten.
    Map<String, Comment> parentComments =
        getPublishedComments(noteUtil, changeId, reader, NoteMap.read(reader, newTipCommit));

    boolean rewrite = false;
    RevCommit originalCommit;
    while ((originalCommit = revWalk.next()) != null) {
      NoteMap noteMap = NoteMap.read(reader, originalCommit);
      Map<String, Comment> currComments = getPublishedComments(noteUtil, changeId, reader, noteMap);

      if (!rewrite && currComments.containsKey(uuid)) {
        rewrite = true;
      }

      if (!rewrite) {
        parentComments = currComments;
        newTipCommit = originalCommit;
        continue;
      }

      List<Comment> putInComments = getPutInComments(parentComments, currComments);
      List<Comment> deletedComments = getDeletedComments(parentComments, currComments);
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
  public static Map<String, Comment> getPublishedComments(
      ChangeNoteJson changeNoteJson,
      LegacyChangeNoteRead legacyChangeNoteRead,
      Change.Id changeId,
      ObjectReader reader,
      NoteMap noteMap)
      throws IOException, ConfigInvalidException {
    return RevisionNoteMap.parse(
            changeNoteJson, legacyChangeNoteRead, changeId, reader, noteMap, PUBLISHED)
        .revisionNotes.values().stream()
        .flatMap(n -> n.getEntities().stream())
        .collect(toMap(c -> c.key.uuid, Function.identity()));
  }

  public static Map<String, Comment> getPublishedComments(
      ChangeNoteUtil noteUtil, Change.Id changeId, ObjectReader reader, NoteMap noteMap)
      throws IOException, ConfigInvalidException {
    return getPublishedComments(
        noteUtil.getChangeNoteJson(),
        noteUtil.getLegacyChangeNoteRead(),
        changeId,
        reader,
        noteMap);
  }
  /**
   * Gets the comments put in by the current commit. The message of the target comment will be
   * replaced by the new message.
   *
   * @param parMap the comment map of the parent commit.
   * @param curMap the comment map of the current commit.
   * @return The comments put in by the current commit.
   */
  private List<Comment> getPutInComments(Map<String, Comment> parMap, Map<String, Comment> curMap) {
    List<Comment> comments = new ArrayList<>();
    for (String key : curMap.keySet()) {
      if (!parMap.containsKey(key)) {
        Comment comment = curMap.get(key);
        if (key.equals(uuid)) {
          comment.message = newMessage;
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
  private List<Comment> getDeletedComments(
      Map<String, Comment> parMap, Map<String, Comment> curMap) {
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
   * @throws IOException
   * @throws ConfigInvalidException
   */
  private ObjectId rewriteCommit(
      RevCommit originalCommit,
      RevCommit parentCommit,
      ObjectInserter inserter,
      ObjectReader reader,
      List<Comment> putInComments,
      List<Comment> deletedComments)
      throws IOException, ConfigInvalidException {
    RevisionNoteMap<ChangeRevisionNote> revNotesMap =
        RevisionNoteMap.parse(
            noteUtil.getChangeNoteJson(),
            noteUtil.getLegacyChangeNoteRead(),
            changeId,
            reader,
            NoteMap.read(reader, parentCommit),
            PUBLISHED);
    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(revNotesMap);

    for (Comment c : putInComments) {
      cache.get(new RevId(c.revId)).putComment(c);
    }

    for (Comment c : deletedComments) {
      cache.get(new RevId(c.revId)).deleteComment(c.key);
    }

    Map<RevId, RevisionNoteBuilder> builders = cache.getBuilders();
    for (Map.Entry<RevId, RevisionNoteBuilder> entry : builders.entrySet()) {
      ObjectId objectId = ObjectId.fromString(entry.getKey().get());
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
