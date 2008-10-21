// Copyright 2008 Google Inc.
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

package com.google.githacks;

import com.google.codereview.util.GitMetaUtil;

import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.LockFile;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.PackWriter;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.TextProgressMonitor;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevObject;
import org.spearce.jgit.revwalk.RevWalk;
import org.spearce.jgit.treewalk.TreeWalk;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Creates a broken shallow repository with a populated assumed base.
 * <p>
 * Broken shallow repositories contain a handful of commits (the assumed base)
 * and the trees referenced by those commits, but not the blobs or the commit
 * ancestors. These repositories are meant to only be used as a source to fetch
 * an overlay from, where the native Git protocol negotiated the assumed base as
 * the common ancestor.
 * <p>
 * The created repository is not marked as shallow and does not have any grafts
 * in it. Clients (or applications) which attempt to walk back through history
 * beyond the assumed base will encounter missing objects and crash. Not
 * configuring the shallow or grafts file is a "data integrity" feature to
 * ensure that clients fetching or cloning from this shallow repository will not
 * be able to succeed, as they do not (and would not receive) the needed but
 * missing objects.
 */
public class BrokenShallowRepositoryCreator {
  /**
   * Create (or update) broken shallow repositories, recursively.
   * 
   * @param srcTop the root of the source tree.
   * @param dstTop the root of the destination tree.
   * @throws IOException a repository failed to be converted.
   */
  public static void createRecursive(final File srcTop, final File dstTop)
      throws IOException {
    final File[] srcEntries = srcTop.listFiles();
    if (srcEntries == null) {
      return;
    }

    for (final File srcEnt : srcEntries) {
      final String srcName = srcEnt.getName();
      if (srcName.equals(".") || srcName.equals("..")) {
        continue;
      } else if (!srcEnt.isDirectory()) {
        continue;
      } else if (GitMetaUtil.isGitRepository(srcEnt)) {
        create(srcEnt, new File(dstTop, srcEnt.getName()));
      } else {
        createRecursive(srcEnt, new File(dstTop, srcEnt.getName()));
      }
    }
  }

  /**
   * Create (or update) a broken shallow repository.
   * 
   * @param srcGitDir the source repository, where commits and trees can be
   *        copied from.
   * @param dstGitDir the destination repository, where the tree data (but not
   *        blob data) will be packed into. If this directory does not exist it
   *        will be automatically created.
   * @throws IOException there was an error reading from the source or writing
   *         to the destination repository.
   */
  public static void create(final File srcGitDir, final File dstGitDir)
      throws IOException {
    final Repository srcdb = new Repository(srcGitDir);

    final RevWalk srcwalk = new RevWalk(srcdb);
    final List<ObjectId> assumed = readAssumedBase(srcdb);
    final List<RevObject> toCopy = new ArrayList<RevObject>(assumed.size() * 2);
    final TreeWalk tw = new TreeWalk(srcdb);
    for (final ObjectId id : assumed) {
      final RevCommit c = srcwalk.parseCommit(id);
      toCopy.add(c);
      toCopy.add(c.getTree());

      tw.reset();
      tw.addTree(c.getTree());

      while (tw.next()) {
        switch (tw.getFileMode(0).getObjectType()) {
          case Constants.OBJ_TREE:
            toCopy.add(srcwalk.lookupTree(tw.getObjectId(0)));
            tw.enterSubtree();
            break;
          case Constants.OBJ_BLOB:
            break;
          default:
            break;
        }
      }
    }

    final Repository destdb = new Repository(dstGitDir);
    if (!destdb.getDirectory().exists()) {
      destdb.create();
    }

    final List<ObjectId> destAssumed = readAssumedBase(destdb);
    destAssumed.addAll(assumed);
    writeAssumedBase(destdb, destAssumed);

    if (destAssumed.isEmpty()) {
      // Nothing assumed, it doesn't need special processing from us.
      //
      return;
    }

    System.out.println("Packing " + destdb.getDirectory());

    // Prepare pack of the assumed base. Clients wouldn't need to
    // fetch this pack, as they already have its contents.
    //
    PackWriter packer = new PackWriter(srcdb, new TextProgressMonitor());
    packer.preparePack(toCopy.iterator());
    storePack(destdb, packer);

    // Prepare a pack of everything else not in the assumed base. This
    // would need to be fetched. We build it second so it has a newer
    // timestamp when it goes into the list of packs, and will therefore
    // be searched first by clients.
    //
    final Map<String, Ref> srcrefs = srcdb.getAllRefs();
    final List<ObjectId> need = new ArrayList<ObjectId>();
    for (final Ref r : srcrefs.values()) {
      need.add(r.getObjectId());
    }
    packer = new PackWriter(srcdb, new TextProgressMonitor());
    packer.preparePack(need, destAssumed, false, false);
    storePack(destdb, packer);

    // Force all of the refs in destdb to match srcdb. We want full
    // mirroring style semantics now that the objects are in place.
    //
    final RevWalk dstwalk = new RevWalk(destdb);
    destdb.writeSymref(Constants.HEAD, srcdb.getFullBranch());
    for (final Ref r : destdb.getAllRefs().values()) {
      if (!srcrefs.containsKey(r.getName())) {
        final RefUpdate u = destdb.updateRef(r.getName());
        u.setForceUpdate(true);
        u.delete(dstwalk);
      }
    }

    for (final Ref r : srcrefs.values()) {
      final RefUpdate u = destdb.updateRef(r.getName());
      if (u.getOldObjectId() == null
          || !u.getOldObjectId().equals(r.getObjectId())) {
        u.setNewObjectId(r.getObjectId());
        u.setForceUpdate(true);
        u.update(dstwalk);
      }
    }

    srcdb.close();
    destdb.close();
  }

  private static void storePack(final Repository destdb, PackWriter packer)
      throws FileNotFoundException, IOException {
    final String packName = "pack-" + packer.computeName().name();
    final File packDir = new File(destdb.getObjectsDirectory(), "pack");
    final File packPath = new File(packDir, packName + ".pack");
    final File idxPath = new File(packDir, packName + ".idx");

    if (!packPath.exists() && !idxPath.exists()) {
      {
        final OutputStream os = new FileOutputStream(packPath);
        try {
          packer.writePack(os);
        } finally {
          os.close();
        }
        packPath.setReadOnly();
      }
      {
        final OutputStream os = new FileOutputStream(idxPath);
        try {
          packer.writeIndex(os);
        } finally {
          os.close();
        }
        idxPath.setReadOnly();
      }
    }
  }

  private static List<ObjectId> readAssumedBase(final Repository db)
      throws IOException {
    final List<ObjectId> list = new ArrayList<ObjectId>();
    try {
      final BufferedReader br;

      br = new BufferedReader(new FileReader(infoAssumedBase(db)));
      try {
        String line;
        while ((line = br.readLine()) != null) {
          list.add(ObjectId.fromString(line));
        }
      } finally {
        br.close();
      }
    } catch (FileNotFoundException noList) {
      // Ignore it. We'll return an empty list to the caller.
    }
    return list;
  }

  private static void writeAssumedBase(final Repository db,
      final List<ObjectId> newList) throws IOException {
    if (newList == null || newList.isEmpty()) {
      infoAssumedBase(db).delete();
      return;
    }

    final LockFile lf = new LockFile(infoAssumedBase(db));
    if (!lf.lock()) {
      throw new IOException("Cannot lock " + infoAssumedBase(db));
    }

    final OutputStream ow = lf.getOutputStream();
    for (final ObjectId id : newList) {
      id.copyTo(ow);
    }
    ow.close();
    if (!lf.commit()) {
      throw new IOException("Cannot commit " + infoAssumedBase(db));
    }
  }

  private static File infoAssumedBase(final Repository db) {
    return new File(db.getDirectory(), "info/assumed-base");
  }
}
