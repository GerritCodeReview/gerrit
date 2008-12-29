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

package com.google.gerrit.server.patch;

import com.google.gerrit.client.data.AccountInfoCacheFactory;
import com.google.gerrit.client.data.LineWithComments;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchContent;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.CorruptEntityException;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.client.rpc.RpcUtil;
import com.google.gerrit.client.rpc.BaseServiceImplementation.Action;
import com.google.gerrit.client.rpc.BaseServiceImplementation.Failure;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;

import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.lib.AnyObjectId;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ObjectLoader;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.patch.CombinedFileHeader;
import org.spearce.jgit.patch.FileHeader;
import org.spearce.jgit.patch.FormatError;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


abstract class PatchDetailAction<T> implements Action<T> {
  protected static final byte[] EMPTY_FILE = {};

  protected final Patch.Key key;
  protected Patch patch;
  protected FileHeader file;
  protected int fileCnt;
  protected AccountInfoCacheFactory accountInfo;
  protected Account.Id me;

  protected HashMap<Integer, List<PatchLineComment>> published[];
  protected HashMap<Integer, List<PatchLineComment>> drafted[];

  PatchDetailAction(final Patch.Key key) {
    this.key = key;
  }

  protected void init(final ReviewDb db) throws OrmException, Failure {
    patch = db.patches().get(key);
    if (patch == null) {
      throw new Failure(new NoSuchEntityException());
    }

    file = parse(patch, read(db, patch));
    if (file instanceof CombinedFileHeader) {
      fileCnt = ((CombinedFileHeader) file).getParentCount() + 1;
    } else {
      fileCnt = 2;
    }

    accountInfo = new AccountInfoCacheFactory(db);
    me = RpcUtil.getAccountId();

    published = new HashMap[fileCnt];
    for (int n = 0; n < fileCnt; n++) {
      published[n] = new HashMap<Integer, List<PatchLineComment>>();
    }
    indexComments(published, db.patchComments().published(key));

    if (me != null) {
      drafted = new HashMap[fileCnt];
      for (int n = 0; n < fileCnt; n++) {
        drafted[n] = new HashMap<Integer, List<PatchLineComment>>();
      }
      indexComments(drafted, db.patchComments().draft(key, me));
    }
  }

  protected static void indexComments(
      final HashMap<Integer, List<PatchLineComment>>[] out,
      final ResultSet<PatchLineComment> comments) {
    for (final PatchLineComment c : comments) {
      if (0 <= c.getSide() && c.getSide() < out.length) {
        final HashMap<Integer, List<PatchLineComment>> m = out[c.getSide()];
        List<PatchLineComment> l = m.get(c.getLine());
        if (l == null) {
          l = new ArrayList<PatchLineComment>(4);
          m.put(c.getLine(), l);
        }
        l.add(c);
      }
    }
  }

  protected void addComments(final LineWithComments pLine,
      final HashMap<Integer, List<PatchLineComment>>[] cache, final int side,
      final int line) {
    List<PatchLineComment> l = cache[side].get(line);
    if (l != null) {
      for (final PatchLineComment c : l) {
        pLine.addComment(c);
        accountInfo.want(c.getAuthor());
      }
    }
  }

  protected byte[] read(final Repository repo, final AnyObjectId id)
      throws Failure {
    if (id == null || ObjectId.zeroId().equals(id)) {
      return EMPTY_FILE;
    }
    try {
      final ObjectLoader ldr = repo.openObject(id);
      if (ldr == null) {
        throw new Failure(new CorruptEntityException(patch.getKey()));
      }
      final byte[] content = ldr.getCachedBytes();
      if (ldr.getType() != Constants.OBJ_BLOB) {
        throw new Failure(new IncorrectObjectTypeException(id.toObjectId(),
            Constants.TYPE_BLOB));
      }
      return content;
    } catch (IOException err) {
      throw new Failure(err);
    }
  }

  protected static String read(final ReviewDb db, final Patch patch)
      throws Failure, OrmException {
    final PatchContent.Key key = patch.getContent();
    if (key == null) {
      throw new Failure(new CorruptEntityException(patch.getKey()));
    }

    final PatchContent pc = db.patchContents().get(key);
    if (pc == null || pc.getContent() == null) {
      throw new Failure(new CorruptEntityException(patch.getKey()));
    }

    return pc.getContent();
  }

  protected static FileHeader parse(final Patch patch, final String content)
      throws Failure {
    final byte[] buf = Constants.encode(content);
    final org.spearce.jgit.patch.Patch p = new org.spearce.jgit.patch.Patch();
    p.parse(buf, 0, buf.length);
    for (final FormatError err : p.getErrors()) {
      if (err.getSeverity() == FormatError.Severity.ERROR) {
        throw new Failure(new CorruptEntityException(patch.getKey()));
      }
    }
    if (p.getFiles().size() != 1) {
      throw new Failure(new CorruptEntityException(patch.getKey()));
    }
    return p.getFiles().get(0);
  }
}
