package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.reviewdb.client.PatchLineComment.Status.PUBLISHED;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
 * Delete a published comment from NoteDb by rewriting the commit history. Instead of deleting the
 * whole comment, it just replaces the comment's message with a new message.
 *
 * <p>This implementation assume that each commit will only put in new comments and will never
 * delete existing comments.
 */
public class DeleteCommentRewriter implements NoteDbRewriter {

  public interface Factory {
    /**
     * Creates a DeleteCommentRewriter instance.
     *
     * @param id the id of the change which contains the target comment.
     * @param uuid the uuid of the target comment.
     * @param newMessage the message used to replace the old message of the target comment.
     * @return
     */
    DeleteCommentRewriter create(
        Change.Id id, @Assisted("uuid") String uuid, @Assisted("newMessage") String newMessage);
  }

  private final ChangeNoteUtil noteUtil;
  private final Change.Id changeId;
  private final String uuid;
  private final String newMessage;

  @AssistedInject
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
      throws IOException, ConfigInvalidException, OrmException {
    checkArgument(!currTip.equals(ObjectId.zeroId()));

    // Walk from the first commit of the branch.
    revWalk.reset();
    revWalk.markStart(revWalk.parseCommit(currTip));
    revWalk.sort(RevSort.REVERSE);

    ObjectReader reader = revWalk.getObjectReader();
    ObjectId newTip = revWalk.next(); // The first commit will not be rewrote.
    NoteMap newTipNoteMap = NoteMap.read(reader, revWalk.parseCommit(newTip));

    boolean rewrite = false;
    RevCommit originalCommit;
    Map<String, Comment> parentComments =
        getPublishedComments(noteUtil, changeId, reader, newTipNoteMap);
    while ((originalCommit = revWalk.next()) != null) {
      NoteMap noteMap = NoteMap.read(reader, originalCommit);
      Map<String, Comment> currComments = getPublishedComments(noteUtil, changeId, reader, noteMap);

      if (!rewrite && currComments.containsKey(uuid)) {
        rewrite = true;
      }

      if (!rewrite) {
        parentComments = currComments;
        newTip = originalCommit;
        continue;
      }

      List<Comment> putComments = getUpdatedCommentList(parentComments, currComments);
      newTip = rewriteCommit(originalCommit, newTipNoteMap, newTip, inserter, reader, putComments);
      newTipNoteMap = NoteMap.read(reader, revWalk.parseCommit(newTip));
      parentComments = currComments;
    }

    return newTip;
  }

  /**
   * Get all the comments which are presented at a commit. Note they include the comments put in by
   * the previous commits.
   */
  @VisibleForTesting
  public static Map<String, Comment> getPublishedComments(
      ChangeNoteUtil noteUtil, Change.Id changeId, ObjectReader reader, NoteMap noteMap)
      throws IOException, ConfigInvalidException {
    RevisionNoteMap<ChangeRevisionNote> revNotesMap =
        RevisionNoteMap.parse(noteUtil, changeId, reader, noteMap, PUBLISHED);

    List<Comment> comments = new ArrayList<>();
    for (ChangeRevisionNote revNote : revNotesMap.revisionNotes.values()) {
      comments.addAll(revNote.getComments());
    }

    return comments.stream().collect(Collectors.toMap(c -> c.key.uuid, c -> c));
  }

  /**
   * Get the comments put in by the current commit. The message of the target comment will be
   * updated.
   *
   * @param parMap the comment map of the parent commit.
   * @param curMap the comment map of the current commit.
   * @return The comment list put in by this commit.
   */
  private List<Comment> getUpdatedCommentList(
      Map<String, Comment> parMap, Map<String, Comment> curMap) throws OrmException {
    List<Comment> commentList = new ArrayList<>();
    for (String key : curMap.keySet()) {
      if (!parMap.containsKey(key)) {
        Comment comment = curMap.get(key);
        if (key.equals(uuid)) {
          comment.message = newMessage;
        }
        commentList.add(comment);
      }
    }

    // Check whether there are some comments removed by this commit.
    for (String key : parMap.keySet()) {
      if (!curMap.containsKey(key)) {
        throw new OrmException(String.format("unexpected that comment %s was removed", key));
      }
    }

    return commentList;
  }

  /**
   * Rewrite one commit.
   *
   * @param originalCommit the original commit to be rewrote.
   * @param parentNoteMap the {@code NoteMap} of the new commit's parent.
   * @param parentId the objectId of the new commit's parent.
   * @param inserter the ObjectInserter for the rewrite process.
   * @param reader the {@code ObjectReader} for the rewrite process.
   * @param putCommentList the comments put in by this commit.
   * @return the objectId of the new commit.
   * @throws IOException
   * @throws ConfigInvalidException
   */
  private ObjectId rewriteCommit(
      RevCommit originalCommit,
      NoteMap parentNoteMap,
      ObjectId parentId,
      ObjectInserter inserter,
      ObjectReader reader,
      List<Comment> putCommentList)
      throws IOException, ConfigInvalidException {
    RevisionNoteMap<ChangeRevisionNote> revNotesMap =
        RevisionNoteMap.parse(noteUtil, changeId, reader, parentNoteMap, PUBLISHED);

    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(revNotesMap);
    for (Comment c : putCommentList) {
      cache.get(new RevId(c.revId)).putComment(c);
    }

    Map<RevId, RevisionNoteBuilder> builders = cache.getBuilders();
    for (Map.Entry<RevId, RevisionNoteBuilder> entry : builders.entrySet()) {
      ObjectId objectId = ObjectId.fromString(entry.getKey().get());
      byte[] data = entry.getValue().build(noteUtil, noteUtil.getWriteJson());
      if (data.length == 0) {
        revNotesMap.noteMap.remove(objectId);
      }
      revNotesMap.noteMap.set(objectId, inserter.insert(OBJ_BLOB, data));
    }

    CommitBuilder cb = new CommitBuilder();
    cb.setParentId(parentId);
    cb.setTreeId(revNotesMap.noteMap.writeTree(inserter));
    cb.setMessage(originalCommit.getFullMessage());
    cb.setCommitter(originalCommit.getCommitterIdent());
    cb.setAuthor(originalCommit.getAuthorIdent());
    cb.setEncoding(originalCommit.getEncoding());

    return inserter.insert(cb);
  }
}
