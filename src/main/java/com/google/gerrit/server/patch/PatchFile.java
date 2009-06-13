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

import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.RevId;
import com.google.gerrit.client.rpc.CorruptEntityException;
import com.google.gerrit.client.rpc.NoSuchEntityException;

import org.spearce.jgit.errors.CorruptObjectException;
import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ObjectLoader;
import org.spearce.jgit.lib.ObjectWriter;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;

/** State supporting processing of a single {@link Patch} instance. */
public class PatchFile {
  private final Repository repo;
  private final Patch patch;
  private final ObjectId aTree;
  private final ObjectId bTree;

  private Text a;
  private Text b;

  public PatchFile(final Repository repo, final RevId id, final Patch patch)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    this.repo = repo;
    this.patch = patch;

    final RevWalk rw = new RevWalk(repo);
    final RevCommit bCommit = rw.parseCommit(ObjectId.fromString(id.get()));
    if (bCommit.getParentCount() > 0) {
      rw.parseHeaders(bCommit.getParent(0));
      aTree = bCommit.getParent(0).getTree();
    } else {
      aTree = emptyTree();
    }
    bTree = bCommit.getTree();
  }

  /**
   * Extract a line from the file, as a string.
   * 
   * @param file the file index to extract.
   * @param line the line number to extract (1 based; 1 is the first line).
   * @return the string version of the file line.
   * @throws CorruptEntityException the patch cannot be read.
   * @throws IOException the patch or complete file content cannot be read.
   * @throws NoSuchEntityException
   * @throws CharacterCodingException the file is not a known character set.
   */
  public String getLine(final int file, final int line)
      throws CorruptEntityException, IOException, NoSuchEntityException {
    switch (file) {
      case 0:
        if (a == null) {
          String p = patch.getSourceFileName();
          if (p == null) {
            p = patch.getFileName();
          }
          a = load(aTree, p);
        }
        return a.getLine(line - 1);

      case 1:
        if (b == null) {
          b = load(bTree, patch.getFileName());
        }
        return b.getLine(line - 1);

      default:
        throw new NoSuchEntityException();
    }
  }

  private Text load(final ObjectId tree, final String path)
      throws MissingObjectException, IncorrectObjectTypeException,
      CorruptObjectException, IOException {
    final ObjectId id = DiffCacheContent.find(repo, tree, path);
    if (id == null) {
      return Text.EMPTY;
    }
    final ObjectLoader ldr = repo.openObject(id);
    if (ldr == null) {
      throw new MissingObjectException(id, Constants.TYPE_BLOB);
    }
    return new Text(ldr.getCachedBytes());
  }

  private ObjectId emptyTree() throws IOException {
    return new ObjectWriter(repo).writeCanonicalTree(new byte[0]);
  }
}
