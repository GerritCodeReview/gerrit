package com.google.gerrit.server.account;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.git.meta.VersionedMetaDataRewriter;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;

public class DeleteSshKeyRewriter implements VersionedMetaDataRewriter {
  private final Account.Id accountId;
  private final int seq;

  public interface Factory {
    /**
     * Creates a DeleteSshKeyRewriter instance.
     *
     * @param accountId account id to which the key belongs to
     * @param seq sequence number of the key to delete
     * @return the DeleteSshKeyRewriter instance
     */
    DeleteSshKeyRewriter create(Account.Id accountId, int seq);
  }

  @Inject
  public DeleteSshKeyRewriter(@Assisted Account.Id accountId, @Assisted int seq) {
    this.accountId = accountId;
    this.seq = seq;
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

    RevCommit newTipCommit = revWalk.next(); // The first key doesn't contain a ssh key.
    boolean rewrite = false;
    RevCommit originalCommit;
    while ((originalCommit = revWalk.next()) != null) {
      List<Optional<AccountSshKey>> keys = loadKeys(reader, originalCommit, accountId);

      if (!rewrite && containsKey(keys, accountId, seq)) {
        rewrite = true;
      }

      if (!rewrite) {
        newTipCommit = originalCommit;
        continue;
      }

      deleteKey(keys, seq - 1);
      newTipCommit =
          revWalk.parseCommit(rewriteCommit(originalCommit, newTipCommit, inserter, reader, keys));
    }

    return newTipCommit;
  }

  private AnyObjectId rewriteCommit(
      RevCommit originalCommit,
      RevCommit parentCommit,
      ObjectInserter inserter,
      ObjectReader reader,
      List<Optional<AccountSshKey>> keys)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    DirCache newTree = readTree(originalCommit.getTree(), reader);
    saveUTF8(inserter, newTree, AuthorizedKeys.FILE_NAME, AuthorizedKeys.serialize(keys));

    CommitBuilder cb = new CommitBuilder();
    cb.setParentId(parentCommit);
    cb.setTreeId(newTree.writeTree(inserter));
    cb.setMessage(originalCommit.getFullMessage());
    cb.setCommitter(originalCommit.getCommitterIdent());
    cb.setAuthor(originalCommit.getAuthorIdent());
    cb.setEncoding(originalCommit.getEncoding());

    return inserter.insert(cb);
  }

  private DirCache readTree(RevTree tree, ObjectReader reader)
      throws IOException, MissingObjectException, IncorrectObjectTypeException {
    DirCache dc = DirCache.newInCore();
    if (tree != null) {
      DirCacheBuilder b = dc.builder();
      b.addTree(new byte[0], DirCacheEntry.STAGE_0, reader, tree);
      b.finish();
    }
    return dc;
  }

  /**
   * The deleted key get's overwritten with an empty Optional so the sequence number of the
   * following keys doesn't change.
   */
  private void deleteKey(List<Optional<AccountSshKey>> keys, int seq) {
    keys.set(seq, Optional.empty());
  }

  private boolean containsKey(List<Optional<AccountSshKey>> keys, Account.Id accountId, int seq) {
    if (keys.size() >= seq) {
      Optional<AccountSshKey> keyOptional = keys.get(seq - 1);
      return keyOptional.isPresent() && keyOptional.get().accountId().equals(accountId);
    }
    return false;
  }

  /**
   * Loads the list of keys found in the passed commit.
   *
   * @param reader Reader to read the file from which the keys are parsed
   * @param commit RevCommit to load the keys from
   * @param accountId account id that gets used to initialize the keys
   * @return loaded keys
   */
  @VisibleForTesting
  public static List<Optional<AccountSshKey>> loadKeys(
      ObjectReader reader, RevCommit commit, Account.Id accountId) throws IOException {
    return AuthorizedKeys.parse(accountId, readUTF8(reader, commit, AuthorizedKeys.FILE_NAME));
  }

  private static String readUTF8(ObjectReader reader, RevCommit commit, String fileName)
      throws IOException {
    byte[] raw = readFile(reader, commit, fileName);
    return raw.length != 0 ? RawParseUtils.decode(raw) : "";
  }

  private static byte[] readFile(ObjectReader reader, RevCommit commit, String fileName)
      throws IOException {
    if (commit == null) {
      return new byte[] {};
    }

    try (TreeWalk tw = TreeWalk.forPath(reader, fileName, commit.getTree())) {
      if (tw != null) {
        ObjectLoader obj = reader.open(tw.getObjectId(0), Constants.OBJ_BLOB);
        return obj.getCachedBytes(Integer.MAX_VALUE);
      }
      return new byte[] {};
    }
  }

  private void saveUTF8(ObjectInserter inserter, DirCache newTree, String fileName, String text)
      throws IOException {
    saveFile(inserter, newTree, fileName, text != null ? Constants.encode(text) : null);
  }

  private void saveFile(ObjectInserter inserter, DirCache newTree, String fileName, byte[] raw)
      throws IOException {
    DirCacheEditor editor = newTree.editor();
    if (raw != null && 0 < raw.length) {
      final ObjectId blobId = inserter.insert(Constants.OBJ_BLOB, raw);
      editor.add(
          new PathEdit(fileName) {
            @Override
            public void apply(DirCacheEntry ent) {
              ent.setFileMode(FileMode.REGULAR_FILE);
              ent.setObjectId(blobId);
            }
          });
    } else {
      editor.add(new DeletePath(fileName));
    }
    editor.finish();
  }
}
