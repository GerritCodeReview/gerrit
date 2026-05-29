// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.patch.filediff;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.FileMode;
import com.google.gerrit.exceptions.StorageException;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;

/** Helper class for computing the size of a file in a given git tree. */
class FileSizeEvaluator {
  private final ObjectReader reader;
  private final RevTree tree;

  FileSizeEvaluator(ObjectReader reader, RevTree tree) {
    this.reader = reader;
    this.tree = tree;
  }

  /**
   * Computes the file ObjectId (SHA-1) identified by the {@code path} parameter at the given git
   * tree identified by {@code gitTreeId}.
   */
  ObjectId getFileObjectId(AbbreviatedObjectId gitTreeId, Patch.FileMode mode, String path)
      throws IOException {
    if (!isBlob(mode)) {
      return ObjectId.zeroId();
    }
    return toObjectId(reader, gitTreeId).orElseGet(() -> lookupObjectId(reader, path, tree));
  }

  /**
   * Computes the file size identified by the {@code path} parameter at the given git tree
   * identified by {@code gitTreeId}.
   */
  long compute(ObjectId fileId) throws IOException {
    if (ObjectId.zeroId().equals(fileId)) {
      return 0;
    }
    return reader.getObjectSize(fileId, OBJ_BLOB);
  }

  private static ObjectId lookupObjectId(ObjectReader reader, String path, RevTree tree) {
    // This variant is very expensive.
    try (TreeWalk treeWalk = TreeWalk.forPath(reader, path, tree)) {
      return treeWalk != null ? treeWalk.getObjectId(0) : ObjectId.zeroId();
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  private static Optional<ObjectId> toObjectId(
      ObjectReader reader, @Nullable AbbreviatedObjectId abbreviatedId) throws IOException {
    if (abbreviatedId == null) {
      // In theory, DiffEntry#getOldId or DiffEntry#getNewId can be null for pure renames or pure
      // mode changes (e.g. DiffEntry#modify doesn't set the IDs). However, the method we call for
      // diffs (DiffFormatter#scan) seems to always produce DiffEntries with set IDs, even for pure
      // renames.
      return Optional.empty();
    }
    if (abbreviatedId.isComplete()) {
      // With the current JGit version and the method we call for diffs (DiffFormatter#scan),
      // this
      // is the only code path taken right now.
      return Optional.ofNullable(abbreviatedId.toObjectId());
    }
    Collection<ObjectId> objectIds = reader.resolve(abbreviatedId);
    // It seems very unlikely that an ObjectId which was just abbreviated by the diff
    // computation
    // now can't be resolved to exactly one ObjectId. The API allows this possibility, though.
    return objectIds.size() == 1
        ? Optional.of(Iterables.getOnlyElement(objectIds))
        : Optional.empty();
  }

  private static boolean isBlob(Patch.FileMode mode) {
    return mode.equals(FileMode.REGULAR_FILE)
        || mode.equals(FileMode.EXECUTABLE_FILE)
        || mode.equals(FileMode.SYMLINK);
  }
}
