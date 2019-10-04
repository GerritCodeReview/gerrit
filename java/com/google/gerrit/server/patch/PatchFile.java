// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.entities.Patch;
import com.google.gerrit.exceptions.NoSuchEntityException;
import java.io.IOException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/** State supporting processing of a single {@link Patch} instance. */
public class PatchFile {
  private final Repository repo;
  private final PatchListEntry entry;
  private final RevTree aTree;
  private final RevTree bTree;

  // Full text of both sides of the file. For standard files, these are not directly reconstructable
  // from the PatchListEntry, which comes from the PatchListCache and only contains the diff between
  // the two blobs. This is intentional, to avoid storing entire large blobs in the cache. For
  // regular files, the full text is initialized from the repo lazily only when necessary, e.g. in
  // getLine. Although it's a safe assumption that any caller constructing a PatchSet will want to
  // read some content, we don't know in advance which side they are interested in.
  //
  // For special files like COMMIT_MSG, the full text is loaded eagerly during the constructor.
  // TODO(dborowitz): I see why the logic is different, but I don't see why it needs to be eager.
  private Text a;
  private Text b;

  public PatchFile(Repository repo, PatchList patchList, String fileName)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    this.repo = repo;
    this.entry = patchList.get(fileName);

    try (ObjectReader reader = repo.newObjectReader();
        RevWalk rw = new RevWalk(reader)) {
      final RevCommit bCommit = rw.parseCommit(patchList.getNewId());

      if (Patch.COMMIT_MSG.equals(fileName)) {
        if (patchList.getComparisonType().isAgainstParentOrAutoMerge()) {
          a = Text.EMPTY;
        } else {
          // For the initial commit, we have an empty tree on Side A
          RevObject object = rw.parseAny(patchList.getOldId());
          a = object instanceof RevCommit ? Text.forCommit(reader, object) : Text.EMPTY;
        }
        b = Text.forCommit(reader, bCommit);

        aTree = null;
        bTree = null;
      } else if (Patch.MERGE_LIST.equals(fileName)) {
        // For the initial commit, we have an empty tree on Side A
        RevObject object = rw.parseAny(patchList.getOldId());
        a =
            object instanceof RevCommit
                ? Text.forMergeList(patchList.getComparisonType(), reader, object)
                : Text.EMPTY;
        b = Text.forMergeList(patchList.getComparisonType(), reader, bCommit);

        aTree = null;
        bTree = null;
      } else {
        if (patchList.getOldId() != null) {
          aTree = rw.parseTree(patchList.getOldId());
        } else {
          final RevCommit p = bCommit.getParent(0);
          rw.parseHeaders(p);
          aTree = p.getTree();
        }
        bTree = bCommit.getTree();
      }
    }
  }

  private String getOldName() {
    String name = entry.getOldName();
    if (name != null) {
      return name;
    }
    return entry.getNewName();
  }

  /**
   * Extract a line from the file, as a string.
   *
   * @param file the file index to extract.
   * @param line the line number to extract (1 based; 1 is the first line).
   * @return the string version of the file line.
   * @throws IOException the patch or complete file content cannot be read.
   * @throws NoSuchEntityException
   */
  public String getLine(int file, int line) throws IOException, NoSuchEntityException {
    switch (file) {
      case 0:
        if (a == null) {
          a = load(aTree, getOldName());
        }
        return a.getString(line - 1);

      case 1:
        if (b == null) {
          b = load(bTree, entry.getNewName());
        }
        return b.getString(line - 1);

      default:
        throw new NoSuchEntityException();
    }
  }

  private Text load(ObjectId tree, String path)
      throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException,
          IOException {
    if (path == null) {
      return Text.EMPTY;
    }
    final TreeWalk tw = TreeWalk.forPath(repo, path, tree);
    if (tw == null) {
      return Text.EMPTY;
    }
    if (tw.getFileMode(0).getObjectType() == Constants.OBJ_BLOB) {
      return new Text(repo.open(tw.getObjectId(0), Constants.OBJ_BLOB));
    } else if (tw.getFileMode(0).getObjectType() == Constants.OBJ_COMMIT) {
      String str = "Subproject commit " + ObjectId.toString(tw.getObjectId(0));
      return new Text(str.getBytes(UTF_8));
    } else {
      return Text.EMPTY;
    }
  }
}
