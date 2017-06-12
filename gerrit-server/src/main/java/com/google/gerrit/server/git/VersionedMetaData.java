// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.common.base.MoreObjects;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.ChangeIdUtil;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Support for metadata stored within a version controlled branch.
 *
 * <p>Implementors are responsible for supplying implementations of the onLoad and onSave methods to
 * read from the repository, or format an update that can later be written back to the repository.
 */
public abstract class VersionedMetaData {
  /**
   * Path information that does not hold references to any repository data structures, allowing the
   * application to retain this object for long periods of time.
   */
  public static class PathInfo {
    public final FileMode fileMode;
    public final String path;
    public final ObjectId objectId;

    protected PathInfo(TreeWalk tw) {
      fileMode = tw.getFileMode(0);
      path = tw.getPathString();
      objectId = tw.getObjectId(0);
    }
  }

  protected RevCommit revision;
  protected ObjectReader reader;
  protected ObjectInserter inserter;
  protected DirCache newTree;

  /** @return name of the reference storing this configuration. */
  protected abstract String getRefName();

  /** Set up the metadata, parsing any state from the loaded revision. */
  protected abstract void onLoad() throws IOException, ConfigInvalidException;

  /**
   * Save any changes to the metadata in a commit.
   *
   * @return true if the commit should proceed, false to abort.
   * @throws IOException
   * @throws ConfigInvalidException
   */
  protected abstract boolean onSave(CommitBuilder commit)
      throws IOException, ConfigInvalidException;

  /** @return revision of the metadata that was loaded. */
  public ObjectId getRevision() {
    return revision != null ? revision.copy() : null;
  }

  /**
   * Load the current version from the branch.
   *
   * <p>The repository is not held after the call completes, allowing the application to retain this
   * object for long periods of time.
   *
   * @param db repository to access.
   * @throws IOException
   * @throws ConfigInvalidException
   */
  public void load(Repository db) throws IOException, ConfigInvalidException {
    Ref ref = db.getRefDatabase().exactRef(getRefName());
    load(db, ref != null ? ref.getObjectId() : null);
  }

  /**
   * Load a specific version from the repository.
   *
   * <p>This method is primarily useful for applying updates to a specific revision that was shown
   * to an end-user in the user interface. If there are conflicts with another user's concurrent
   * changes, these will be automatically detected at commit time.
   *
   * <p>The repository is not held after the call completes, allowing the application to retain this
   * object for long periods of time.
   *
   * @param db repository to access.
   * @param id revision to load.
   * @throws IOException
   * @throws ConfigInvalidException
   */
  public void load(Repository db, ObjectId id) throws IOException, ConfigInvalidException {
    try (RevWalk walk = new RevWalk(db)) {
      load(walk, id);
    }
  }

  /**
   * Load a specific version from an open walk.
   *
   * <p>This method is primarily useful for applying updates to a specific revision that was shown
   * to an end-user in the user interface. If there are conflicts with another user's concurrent
   * changes, these will be automatically detected at commit time.
   *
   * <p>The caller retains ownership of the walk and is responsible for closing it. However, this
   * instance does not hold a reference to the walk or the repository after the call completes,
   * allowing the application to retain this object for long periods of time.
   *
   * @param walk open walk to access to access.
   * @param id revision to load.
   * @throws IOException
   * @throws ConfigInvalidException
   */
  public void load(RevWalk walk, ObjectId id) throws IOException, ConfigInvalidException {
    this.reader = walk.getObjectReader();
    try {
      revision = id != null ? new RevWalk(reader).parseCommit(id) : null;
      onLoad();
    } finally {
      reader = null;
    }
  }

  public void load(MetaDataUpdate update) throws IOException, ConfigInvalidException {
    load(update.getRepository());
  }

  public void load(MetaDataUpdate update, ObjectId id) throws IOException, ConfigInvalidException {
    load(update.getRepository(), id);
  }

  /**
   * Update this metadata branch, recording a new commit on its reference.
   *
   * @param update helper information to define the update that will occur.
   * @return the commit that was created
   * @throws IOException if there is a storage problem and the update cannot be executed as
   *     requested or if it failed because of a concurrent update to the same reference
   */
  public RevCommit commit(MetaDataUpdate update) throws IOException {
    BatchMetaDataUpdate batch = openUpdate(update);
    try {
      batch.write(update.getCommitBuilder());
      return batch.commit();
    } finally {
      batch.close();
    }
  }

  /**
   * Creates a new commit and a new ref based on this commit.
   *
   * @param update helper information to define the update that will occur.
   * @param refName name of the ref that should be created
   * @return the commit that was created
   * @throws IOException if there is a storage problem and the update cannot be executed as
   *     requested or if it failed because of a concurrent update to the same reference
   */
  public RevCommit commitToNewRef(MetaDataUpdate update, String refName) throws IOException {
    BatchMetaDataUpdate batch = openUpdate(update);
    try {
      batch.write(update.getCommitBuilder());
      return batch.createRef(refName);
    } finally {
      batch.close();
    }
  }

  public interface BatchMetaDataUpdate {
    void write(CommitBuilder commit) throws IOException;

    void write(VersionedMetaData config, CommitBuilder commit) throws IOException;

    RevCommit createRef(String refName) throws IOException;

    void removeRef(String refName) throws IOException;

    RevCommit commit() throws IOException;

    RevCommit commitAt(ObjectId revision) throws IOException;

    void close();
  }

  /**
   * Open a batch of updates to the same metadata ref.
   *
   * <p>This allows making multiple commits to a single metadata ref, at the end of which is a
   * single ref update. For batching together updates to multiple refs (each consisting of one or
   * more commits against their respective refs), create the {@link MetaDataUpdate} with a {@link
   * BatchRefUpdate}.
   *
   * <p>A ref update produced by this {@link BatchMetaDataUpdate} is only committed if there is no
   * associated {@link BatchRefUpdate}. As a result, the configured ref updated event is not fired
   * if there is an associated batch.
   *
   * @param update helper info about the update.
   * @throws IOException if the update failed.
   */
  public BatchMetaDataUpdate openUpdate(final MetaDataUpdate update) throws IOException {
    final Repository db = update.getRepository();

    reader = db.newObjectReader();
    inserter = db.newObjectInserter();
    final RevWalk rw = new RevWalk(reader);
    final RevTree tree = revision != null ? rw.parseTree(revision) : null;
    newTree = readTree(tree);
    return new BatchMetaDataUpdate() {
      AnyObjectId src = revision;
      AnyObjectId srcTree = tree;

      @Override
      public void write(CommitBuilder commit) throws IOException {
        write(VersionedMetaData.this, commit);
      }

      private boolean doSave(VersionedMetaData config, CommitBuilder commit) throws IOException {
        DirCache nt = config.newTree;
        ObjectReader r = config.reader;
        ObjectInserter i = config.inserter;
        try {
          config.newTree = newTree;
          config.reader = reader;
          config.inserter = inserter;
          return config.onSave(commit);
        } catch (ConfigInvalidException e) {
          throw new IOException(
              "Cannot update " + getRefName() + " in " + db.getDirectory() + ": " + e.getMessage(),
              e);
        } finally {
          config.newTree = nt;
          config.reader = r;
          config.inserter = i;
        }
      }

      @Override
      public void write(VersionedMetaData config, CommitBuilder commit) throws IOException {
        if (!doSave(config, commit)) {
          return;
        }

        // Reuse tree from parent commit unless there are contents in newTree or
        // there is no tree for a parent commit.
        ObjectId res =
            newTree.getEntryCount() != 0 || srcTree == null
                ? newTree.writeTree(inserter)
                : srcTree.copy();
        if (res.equals(srcTree) && !update.allowEmpty() && (commit.getTreeId() == null)) {
          // If there are no changes to the content, don't create the commit.
          return;
        }

        // If changes are made to the DirCache and those changes are written as
        // a commit and then the tree ID is set for the CommitBuilder, then
        // those previous DirCache changes will be ignored and the commit's
        // tree will be replaced with the ID in the CommitBuilder. The same is
        // true if you explicitly set tree ID in a commit and then make changes
        // to the DirCache; that tree ID will be ignored and replaced by that of
        // the tree for the updated DirCache.
        if (commit.getTreeId() == null) {
          commit.setTreeId(res);
        } else {
          // In this case, the caller populated the tree without using DirCache.
          res = commit.getTreeId();
        }

        if (src != null) {
          commit.addParentId(src);
        }

        if (update.insertChangeId()) {
          ObjectId id =
              ChangeIdUtil.computeChangeId(
                  res,
                  getRevision(),
                  commit.getAuthor(),
                  commit.getCommitter(),
                  commit.getMessage());
          commit.setMessage(ChangeIdUtil.insertId(commit.getMessage(), id));
        }

        src = inserter.insert(commit);
        srcTree = res;
      }

      @Override
      public RevCommit createRef(String refName) throws IOException {
        if (Objects.equals(src, revision)) {
          return revision;
        }
        return updateRef(ObjectId.zeroId(), src, refName);
      }

      @Override
      public void removeRef(String refName) throws IOException {
        RefUpdate ru = db.updateRef(refName);
        ru.setForceUpdate(true);
        if (revision != null) {
          ru.setExpectedOldObjectId(revision);
        }
        RefUpdate.Result result = ru.delete();
        switch (result) {
          case FORCED:
            update.fireGitRefUpdatedEvent(ru);
            return;
          case FAST_FORWARD:
          case IO_FAILURE:
          case LOCK_FAILURE:
          case NEW:
          case NOT_ATTEMPTED:
          case NO_CHANGE:
          case REJECTED:
          case REJECTED_CURRENT_BRANCH:
          case RENAMED:
          default:
            throw new IOException(
                "Cannot delete "
                    + ru.getName()
                    + " in "
                    + db.getDirectory()
                    + ": "
                    + ru.getResult());
        }
      }

      @Override
      public RevCommit commit() throws IOException {
        return commitAt(revision);
      }

      @Override
      public RevCommit commitAt(ObjectId expected) throws IOException {
        if (Objects.equals(src, expected)) {
          return revision;
        }
        return updateRef(MoreObjects.firstNonNull(expected, ObjectId.zeroId()), src, getRefName());
      }

      @Override
      public void close() {
        newTree = null;

        rw.close();
        if (inserter != null) {
          inserter.close();
          inserter = null;
        }

        if (reader != null) {
          reader.close();
          reader = null;
        }
      }

      private RevCommit updateRef(AnyObjectId oldId, AnyObjectId newId, String refName)
          throws IOException {
        BatchRefUpdate bru = update.getBatch();
        if (bru != null) {
          bru.addCommand(new ReceiveCommand(oldId.toObjectId(), newId.toObjectId(), refName));
          inserter.flush();
          revision = rw.parseCommit(newId);
          return revision;
        }

        RefUpdate ru = db.updateRef(refName);
        ru.setExpectedOldObjectId(oldId);
        ru.setNewObjectId(src);
        ru.setRefLogIdent(update.getCommitBuilder().getAuthor());
        String message = update.getCommitBuilder().getMessage();
        if (message == null) {
          message = "meta data update";
        }
        try (BufferedReader reader = new BufferedReader(new StringReader(message))) {
          // read the subject line and use it as reflog message
          ru.setRefLogMessage("commit: " + reader.readLine(), true);
        }
        inserter.flush();
        RefUpdate.Result result = ru.update();
        switch (result) {
          case NEW:
          case FAST_FORWARD:
            revision = rw.parseCommit(ru.getNewObjectId());
            update.fireGitRefUpdatedEvent(ru);
            return revision;
          case FORCED:
          case IO_FAILURE:
          case LOCK_FAILURE:
          case NOT_ATTEMPTED:
          case NO_CHANGE:
          case REJECTED:
          case REJECTED_CURRENT_BRANCH:
          case RENAMED:
          default:
            throw new IOException(
                "Cannot update "
                    + ru.getName()
                    + " in "
                    + db.getDirectory()
                    + ": "
                    + ru.getResult());
        }
      }
    };
  }

  protected DirCache readTree(RevTree tree)
      throws IOException, MissingObjectException, IncorrectObjectTypeException {
    DirCache dc = DirCache.newInCore();
    if (tree != null) {
      DirCacheBuilder b = dc.builder();
      b.addTree(new byte[0], DirCacheEntry.STAGE_0, reader, tree);
      b.finish();
    }
    return dc;
  }

  protected Config readConfig(String fileName) throws IOException, ConfigInvalidException {
    Config rc = new Config();
    String text = readUTF8(fileName);
    if (!text.isEmpty()) {
      try {
        rc.fromText(text);
      } catch (ConfigInvalidException err) {
        throw new ConfigInvalidException(
            "Invalid config file " + fileName + " in commit " + revision.name(), err);
      }
    }
    return rc;
  }

  protected String readUTF8(String fileName) throws IOException {
    byte[] raw = readFile(fileName);
    return raw.length != 0 ? RawParseUtils.decode(raw) : "";
  }

  protected byte[] readFile(String fileName) throws IOException {
    if (revision == null) {
      return new byte[] {};
    }

    TreeWalk tw = TreeWalk.forPath(reader, fileName, revision.getTree());
    if (tw != null) {
      ObjectLoader obj = reader.open(tw.getObjectId(0), Constants.OBJ_BLOB);
      return obj.getCachedBytes(Integer.MAX_VALUE);
    }
    return new byte[] {};
  }

  protected ObjectId getObjectId(String fileName) throws IOException {
    if (revision == null) {
      return null;
    }

    TreeWalk tw = TreeWalk.forPath(reader, fileName, revision.getTree());
    if (tw != null) {
      return tw.getObjectId(0);
    }

    return null;
  }

  public List<PathInfo> getPathInfos(boolean recursive) throws IOException {
    TreeWalk tw = new TreeWalk(reader);
    tw.addTree(revision.getTree());
    tw.setRecursive(recursive);
    List<PathInfo> paths = new ArrayList<>();
    while (tw.next()) {
      paths.add(new PathInfo(tw));
    }
    return paths;
  }

  protected static void set(
      Config rc, String section, String subsection, String name, String value) {
    if (value != null) {
      rc.setString(section, subsection, name, value);
    } else {
      rc.unset(section, subsection, name);
    }
  }

  protected static void set(
      Config rc, String section, String subsection, String name, boolean value) {
    if (value) {
      rc.setBoolean(section, subsection, name, value);
    } else {
      rc.unset(section, subsection, name);
    }
  }

  protected static <E extends Enum<?>> void set(
      Config rc, String section, String subsection, String name, E value, E defaultValue) {
    if (value != defaultValue) {
      rc.setEnum(section, subsection, name, value);
    } else {
      rc.unset(section, subsection, name);
    }
  }

  protected void saveConfig(String fileName, Config cfg) throws IOException {
    saveUTF8(fileName, cfg.toText());
  }

  protected void saveUTF8(String fileName, String text) throws IOException {
    saveFile(fileName, text != null ? Constants.encode(text) : null);
  }

  protected void saveFile(String fileName, byte[] raw) throws IOException {
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
