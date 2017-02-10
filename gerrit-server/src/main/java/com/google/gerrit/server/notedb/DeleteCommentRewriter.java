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

public class DeleteCommentRewriter implements NoteDbRewriter {

  public interface Factory {
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

    revWalk.reset();
    revWalk.markStart(revWalk.parseCommit(currTip));
    revWalk.sort(RevSort.REVERSE);

    ObjectReader reader = revWalk.getObjectReader();
    ObjectId newTip = revWalk.next();
    NoteMap newTipNoteMap = NoteMap.read(reader, revWalk.parseCommit(newTip));

    boolean rewrite = false;
    RevCommit commit;
    Map<String, Comment> parentComments =
        getPublishedComments(noteUtil, changeId, reader, newTipNoteMap);
    while ((commit = revWalk.next()) != null) {
      NoteMap noteMap = NoteMap.read(reader, commit);
      Map<String, Comment> currComments = getPublishedComments(noteUtil, changeId, reader, noteMap);

      if (!rewrite && currComments.containsKey(uuid)) {
        rewrite = true;
      }

      if (!rewrite) {
        parentComments = currComments;
        newTip = commit;
        continue;
      }

      List<Comment> putComments = getUpdatedCommentList(parentComments, currComments);
      newTip = redoCommit(commit, newTipNoteMap, newTip, inserter, reader, putComments);
      newTipNoteMap = NoteMap.read(reader, revWalk.parseCommit(newTip));
      parentComments = currComments;
    }

    return newTip;
  }

  /** Get all the comments which are presented in a commit. */
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
   * Get the comments put in by the current commit.
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
   * Redo one commit.
   *
   * @param parentId the objectId of the parent commit.
   * @param inserter the ObjectInserter for the redo process.
   * @param putCommentList the comments put in by this commit.
   * @return the objectId of the new commit.
   * @throws IOException
   * @throws ConfigInvalidException
   */
  private ObjectId redoCommit(
      RevCommit oldCommit,
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
    for (Map.Entry<RevId, RevisionNoteBuilder> e : builders.entrySet()) {
      ObjectId id = ObjectId.fromString(e.getKey().get());
      byte[] data = e.getValue().build(noteUtil, noteUtil.getWriteJson());
      if (data.length == 0) {
        revNotesMap.noteMap.remove(id);
      }
      revNotesMap.noteMap.set(id, inserter.insert(OBJ_BLOB, data));
    }

    CommitBuilder cb = new CommitBuilder();
    cb.setParentId(parentId);
    cb.setTreeId(revNotesMap.noteMap.writeTree(inserter));
    cb.setMessage(oldCommit.getFullMessage());
    cb.setCommitter(oldCommit.getCommitterIdent());
    cb.setAuthor(oldCommit.getAuthorIdent());
    cb.setEncoding(oldCommit.getEncoding());

    return inserter.insert(cb);
  }
}
