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

import com.google.common.base.Objects;
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
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;

import java.io.IOException;

/**
 * Support for metadata stored within a version controlled branch.
 * <p>
 * Implementors are responsible for supplying implementations of the onLoad and
 * onSave methods to read from the repository, or format an update that can
 * later be written back to the repository.
 */
public abstract class VersionedMetaData {
  private RevCommit revision;
  private ObjectReader reader;
  private ObjectInserter inserter;
  private DirCache newTree;

  /** @return name of the reference storing this configuration. */
  protected abstract String getRefName();

  protected abstract void onLoad() throws IOException, ConfigInvalidException;

  protected abstract void onSave(CommitBuilder commit) throws IOException,
      ConfigInvalidException;

  /** @return revision of the metadata that was loaded. */
  public ObjectId getRevision() {
    return revision != null ? revision.copy() : null;
  }

  /** Initialize in-memory as though the repository branch doesn't exist. */
  public void createInMemory() {
    try {
      revision = null;
      onLoad();
    } catch (IOException err) {
      throw new RuntimeException("Unexpected IOException", err);
    } catch (ConfigInvalidException err) {
      throw new RuntimeException("Unexpected ConfigInvalidException", err);
    }
  }

  /**
   * Load the current version from the branch.
   * <p>
   * The repository is not held after the call completes, allowing the
   * application to retain this object for long periods of time.
   *
   * @param db repository to access.
   * @throws IOException
   * @throws ConfigInvalidException
   */
  public void load(Repository db) throws IOException, ConfigInvalidException {
    Ref ref = db.getRef(getRefName());
    load(db, ref != null ? ref.getObjectId() : null);
  }

  /**
   * Load a specific version from the repository.
   * <p>
   * This method is primarily useful for applying updates to a specific revision
   * that was shown to an end-user in the user interface. If there are conflicts
   * with another user's concurrent changes, these will be automatically
   * detected at commit time.
   * <p>
   * The repository is not held after the call completes, allowing the
   * application to retain this object for long periods of time.
   *
   * @param db repository to access.
   * @param id revision to load.
   * @throws IOException
   * @throws ConfigInvalidException
   */
  public void load(Repository db, ObjectId id) throws IOException,
      ConfigInvalidException {
    if (id != null) {
      reader = db.newObjectReader();
      try {
        revision = new RevWalk(reader).parseCommit(id);
        onLoad();
      } finally {
        reader.release();
        reader = null;
      }
    } else {
      // The branch does not yet exist.
      revision = null;
      onLoad();
    }
  }

  public void load(MetaDataUpdate update) throws IOException,
      ConfigInvalidException {
    load(update.getRepository());
  }

  public void load(MetaDataUpdate update, ObjectId id) throws IOException,
      ConfigInvalidException {
    load(update.getRepository(), id);
  }

  /**
   * Update this metadata branch, recording a new commit on its reference.
   *
   * @param update helper information to define the update that will occur.
   * @return true if the update was successful, false if it failed because of a
   *         concurrent update to the same reference.
   * @throws IOException if there is a storage problem and the update cannot be
   *         executed as requested.
   */
  public boolean commit(MetaDataUpdate update) throws IOException {
    BatchMetaDataUpdate batch = openUpdate(update);
    try {
      batch.write(update.getCommitBuilder());
      return batch.commit();
    } finally {
      batch.close();
    }
  }

  public interface BatchMetaDataUpdate {
    void write(CommitBuilder commit) throws IOException;
    void write(VersionedMetaData config, CommitBuilder commit) throws IOException;
    boolean commit() throws IOException;
    boolean commitAt(ObjectId revision) throws IOException;
    void close();
  }

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

      private void doSave(VersionedMetaData config, CommitBuilder commit) throws IOException {
        DirCache nt = config.newTree;
        ObjectReader r = config.reader;
        ObjectInserter i = config.inserter;
        try {
          config.newTree = newTree;
          config.reader = reader;
          config.inserter = inserter;
          config.onSave(commit);
        } catch (ConfigInvalidException e) {
          throw new IOException("Cannot update " + getRefName() + " in "
              + db.getDirectory() + ": " + e.getMessage(), e);
        } finally {
          config.newTree = nt;
          config.reader = r;
          config.inserter = i;
        }
      }

      @Override
      public void write(VersionedMetaData config, CommitBuilder commit) throws IOException {
        doSave(config, commit);

        final ObjectId res = newTree.writeTree(inserter);
        if (res.equals(srcTree)) {
          // If there are no changes to the content, don't create the commit.
          return;
        }

        commit.setTreeId(res);
        if (src != null) {
          commit.addParentId(src);
        }

        src = inserter.insert(commit);
        srcTree = res;
      }

      @Override
      public boolean commit() throws IOException {
        return commitAt(revision);
      }

      @Override
      public boolean commitAt(ObjectId expected) throws IOException {
        if (Objects.equal(src, expected)) {
          return true;
        }

        RefUpdate ru = db.updateRef(getRefName());
        if (expected != null) {
          ru.setExpectedOldObjectId(expected);
        } else {
          ru.setExpectedOldObjectId(ObjectId.zeroId());
        }
        ru.setNewObjectId(src);
        ru.disableRefLog();
        inserter.flush();

        switch (ru.update(rw)) {
          case NEW:
          case FAST_FORWARD:
            revision = rw.parseCommit(ru.getNewObjectId());
            update.replicate(ru.getName());
            return true;

          case LOCK_FAILURE:
            return false;

          default:
            throw new IOException("Cannot update " + ru.getName() + " in "
                + db.getDirectory() + ": " + ru.getResult());
        }
      }

      @Override
      public void close() {
        newTree = null;

        if (inserter != null) {
          inserter.release();
          inserter = null;
        }

        if (reader != null) {
          reader.release();
          reader = null;
        }
      }
    };
  }

  private DirCache readTree(RevTree tree) throws IOException,
      MissingObjectException, IncorrectObjectTypeException {
    DirCache dc = DirCache.newInCore();
    if (tree != null) {
      DirCacheBuilder b = dc.builder();
      b.addTree(new byte[0], DirCacheEntry.STAGE_0, reader, tree);
      b.finish();
    }
    return dc;
  }

  protected Config readConfig(String fileName) throws IOException,
      ConfigInvalidException {
    Config rc = new Config();
    String text = readUTF8(fileName);
    if (!text.isEmpty()) {
      try {
        rc.fromText(text);
      } catch (ConfigInvalidException err) {
        throw new ConfigInvalidException("Invalid config file " + fileName
            + " in commit" + revision.name(), err);
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

    } else {
      return new byte[] {};
    }
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

  protected static void set(Config rc, String section, String subsection,
      String name, String value) {
    if (value != null) {
      rc.setString(section, subsection, name, value);
    } else {
      rc.unset(section, subsection, name);
    }
  }

  protected static void set(Config rc, String section, String subsection,
      String name, boolean value) {
    if (value) {
      rc.setBoolean(section, subsection, name, value);
    } else {
      rc.unset(section, subsection, name);
    }
  }

  protected static <E extends Enum<?>> void set(Config rc, String section,
      String subsection, String name, E value, E defaultValue) {
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
      editor.add(new PathEdit(fileName) {
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
