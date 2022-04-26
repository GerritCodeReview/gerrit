package com.google.gerrit.server.account.externalids;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.gerrit.server.git.meta.VersionedMetaDataRewriter;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

public class DeleteExternalIdRewriter implements VersionedMetaDataRewriter {
  private ExternalIdFactory externalIdFactory;
  private Collection<ExternalId> toDelete;
  private boolean isUserNameCaseInsensitiveMigrationMode;

  public interface Factory {
    /**
     * Creates a DeleteExternalIdRewriter instance.
     *
     * @param toDelete Set of ExternalIds to delete
     * @return the DeleteSshKeyRewriter instance
     */
    DeleteExternalIdRewriter create(
        Collection<ExternalId> toDelete, boolean isUserNameCaseInsensitiveMigrationMode);
  }

  @Inject
  public DeleteExternalIdRewriter(
      ExternalIdFactory externalIdFactory,
      @Assisted Collection<ExternalId> toDelete,
      @Assisted boolean isUserNameCaseInsensitiveMigrationMode) {
    this.externalIdFactory = externalIdFactory;
    this.toDelete = toDelete;
    this.isUserNameCaseInsensitiveMigrationMode = isUserNameCaseInsensitiveMigrationMode;
  }

  @Override
  public ObjectId rewriteCommitHistory(
      RevWalk revWalk, ObjectInserter inserter, ObjectId currentTip)
      throws MissingObjectException, IncorrectObjectTypeException, ConfigInvalidException,
          IOException {
    checkArgument(!currentTip.equals(ObjectId.zeroId()));

    // Walk from the first commit of the branch.
    revWalk.reset();
    revWalk.markStart(revWalk.parseCommit(currentTip));
    revWalk.sort(RevSort.REVERSE);

    ObjectReader reader = revWalk.getObjectReader();

    RevCommit newTipCommit = revWalk.next(); // The first commit doesn't contain an external id.
    boolean rewrite = false;
    RevCommit originalCommit;
    while ((originalCommit = revWalk.next()) != null) {
      NoteMap noteMap = NoteMap.read(reader, originalCommit);

      List<ObjectId> containedKeys = noteIdsOfExternalIdsToDelete(revWalk, noteMap, toDelete);
      if (!rewrite && !containedKeys.isEmpty()) {
        rewrite = true;
      }

      if (!rewrite) {
        newTipCommit = originalCommit;
        continue;
      }

      for (ObjectId keyToDelete : containedKeys) {
        noteMap.remove(keyToDelete);
      }
      newTipCommit =
          revWalk.parseCommit(rewriteCommit(originalCommit, newTipCommit, inserter, noteMap));
    }

    return newTipCommit;
  }

  /**
   * @return References to all ExternalIds to delete that are contained in the current NoteMap and
   *     are associated with the expected account id.
   */
  private List<ObjectId> noteIdsOfExternalIdsToDelete(
      RevWalk revWalk, NoteMap noteMap, Collection<ExternalId> toDelete)
      throws IOException, ConfigInvalidException {
    List<ObjectId> containedKeys = new ArrayList<>();
    for (ExternalId externalId : toDelete) {
      ObjectId noteId = getNoteId(noteMap, externalId.key());
      if (noteMap.contains(noteId)) {
        ObjectId noteDataId = noteMap.get(noteId);
        byte[] raw = ExternalIdNotes.readNoteData(revWalk, noteDataId);
        ExternalId actualExtId = externalIdFactory.parse(noteId.name(), raw, noteDataId);

        if (externalId.accountId().equals(actualExtId.accountId())) {
          containedKeys.add(noteId);
        }
      }
    }
    return containedKeys;
  }

  private ObjectId getNoteId(NoteMap noteMap, ExternalId.Key key) throws IOException {
    ObjectId noteId = key.sha1();

    if (!noteMap.contains(noteId) && isUserNameCaseInsensitiveMigrationMode) {
      noteId = key.caseSensitiveSha1();
    }

    return noteId;
  }

  private AnyObjectId rewriteCommit(
      RevCommit originalCommit, RevCommit parentCommit, ObjectInserter inserter, NoteMap noteMap)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    CommitBuilder cb = new CommitBuilder();
    cb.setParentId(parentCommit);
    cb.setTreeId(noteMap.writeTree(inserter));
    cb.setMessage(originalCommit.getFullMessage());
    cb.setCommitter(originalCommit.getCommitterIdent());
    cb.setAuthor(originalCommit.getAuthorIdent());
    cb.setEncoding(originalCommit.getEncoding());

    return inserter.insert(cb);
  }
}
