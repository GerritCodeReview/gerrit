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
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchContent;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.CorruptEntityException;
import com.google.gerrit.client.rpc.NoDifferencesException;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.client.rpc.BaseServiceImplementation.Action;
import com.google.gerrit.client.rpc.BaseServiceImplementation.Failure;
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gerrit.git.RepositoryCache;
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
import java.util.Map;


abstract class PatchDetailAction<T> implements Action<T> {
  protected static final byte[] EMPTY_FILE = {};

  protected final Patch.Key patchKey;
  protected final List<PatchSet.Id> requestedVersions;
  protected final boolean direct;
  private final RepositoryCache repoCache;
  private Repository repo;
  protected Change change;
  protected Patch patch;
  protected FileHeader file;
  protected int fileCnt;
  protected AccountInfoCacheFactory accountInfo;
  protected Account.Id me;

  protected HashMap<Integer, List<PatchLineComment>> published[];
  protected HashMap<Integer, List<PatchLineComment>> drafted[];

  PatchDetailAction(final RepositoryCache rc, final Patch.Key key,
      final List<PatchSet.Id> fileVersions) {
    this.repoCache = rc;
    this.patchKey = key;
    this.requestedVersions = fileVersions;
    this.direct = isBaseAndPatchOnly(key, fileVersions);
  }

  private static boolean isBaseAndPatchOnly(final Patch.Key key,
      final List<PatchSet.Id> v) {
    if (v == null || v.size() < 2) {
      return true;
    }
    for (int i = 0; i < v.size() - 1; i++) {
      if (!PatchSet.BASE.equals(v.get(i))) {
        return false;
      }
    }
    return v.equals(key.getParentKey());
  }

  protected void init(final ReviewDb db) throws OrmException, Failure {
    patch = db.patches().get(patchKey);
    if (patch == null) {
      throw new Failure(new NoSuchEntityException());
    }
    change = db.changes().get(patch.getKey().getParentKey().getParentKey());
    BaseServiceImplementation.assertCanRead(change);

    file = readFileHeader(db);
    if (file instanceof CombinedFileHeader) {
      fileCnt = ((CombinedFileHeader) file).getParentCount() + 1;
    } else {
      fileCnt = 2;
    }

    accountInfo = new AccountInfoCacheFactory(db);
    me = Common.getAccountId();

    published = new HashMap[fileCnt];
    for (int n = 0; n < fileCnt; n++) {
      published[n] = new HashMap<Integer, List<PatchLineComment>>();
    }
    indexComments(published, direct ? db.patchComments().published(patchKey)
        : db.patchComments().published(change.getId(), patchKey.get()));

    if (me != null) {
      drafted = new HashMap[fileCnt];
      for (int n = 0; n < fileCnt; n++) {
        drafted[n] = new HashMap<Integer, List<PatchLineComment>>();
      }
      indexComments(drafted, direct ? db.patchComments().draft(patchKey, me)
          : db.patchComments().draft(change.getId(), patchKey.get(), me));
    }
  }

  protected FileHeader readFileHeader(final ReviewDb db) throws Failure,
      OrmException {
    if (direct) {
      return readCachedFileHeader(db);
    }

    // TODO Fix this gross hack so we aren't running git diff-tree for
    // an interdiff file side by side view.
    //
    final Map<PatchSet.Id, PatchSet> psMap =
        db.patchSets().toMap(db.patchSets().byChange(change.getId()));
    final Repository repo = openRepository();
    final List<String> args = new ArrayList<String>();
    args.add("git");
    args.add("--git-dir=.");
    args.add("diff-tree");
    args.add("--full-index");
    if (requestedVersions.size() > 2) {
      args.add("--cc");
    } else {
      args.add("--unified=5");
    }
    for (int i = 0; i < requestedVersions.size(); i++) {
      final PatchSet.Id psi = requestedVersions.get(i);
      if (psi == null) {
        throw new Failure(new NoSuchEntityException());

      } else if (psi.equals(PatchSet.BASE)) {
        final PatchSet p = psMap.get(patchKey.getParentKey());
        if (p == null || p.getRevision() == null
            || p.getRevision().get() == null) {
          throw new Failure(new NoSuchEntityException());
        }
        args.add(p.getRevision().get() + "^" + (i + 1));

      } else {
        final PatchSet p = psMap.get(psi);
        if (p == null || p.getRevision() == null
            || p.getRevision().get() == null) {
          throw new Failure(new NoSuchEntityException());
        }
        args.add(p.getRevision().get());
      }
    }
    args.add("--");
    args.add(patch.getFileName());

    try {
      final Process proc =
          Runtime.getRuntime().exec(args.toArray(new String[args.size()]),
              null, repo.getDirectory());
      try {
        final org.spearce.jgit.patch.Patch p =
            new org.spearce.jgit.patch.Patch();
        proc.getOutputStream().close();
        proc.getErrorStream().close();
        p.parse(proc.getInputStream());
        proc.getInputStream().close();
        if (p.getFiles().isEmpty())
          throw new Failure(new NoDifferencesException());
        if (p.getFiles().size() != 1)
          throw new IOException("unexpected file count back");
        return p.getFiles().get(0);
      } finally {
        try {
          if (proc.waitFor() != 0) {
            throw new IOException("git diff-tree exited abnormally");
          }
        } catch (InterruptedException ie) {
        }
      }
    } catch (IOException e) {
      throw new Failure(e);
    }
  }

  protected FileHeader readCachedFileHeader(final ReviewDb db) throws Failure,
      OrmException {
    return parse(patch, read(db, patch));
  }

  protected List<Patch> history(final ReviewDb db) throws OrmException {
    return db.patches().history(change.getId(), patch.getFileName()).toList();
  }

  protected void indexComments(
      final HashMap<Integer, List<PatchLineComment>>[] out,
      final ResultSet<PatchLineComment> comments) {
    for (final PatchLineComment c : comments) {
      short fileId;
      if (direct) {
        fileId = c.getSide();
      } else {
        for (fileId = 0; fileId < requestedVersions.size(); fileId++) {
          final PatchSet.Id i = requestedVersions.get(fileId);
          if (PatchSet.BASE.equals(i) && c.getSide() == fileId
              && patchKey.equals(c.getKey().getParentKey())) {
            break;
          }
          if (c.getSide() == requestedVersions.size() - 1
              && i.equals(c.getKey().getParentKey().getParentKey())) {
            break;
          }
        }
      }

      if (0 <= fileId && fileId < out.length) {
        final HashMap<Integer, List<PatchLineComment>> m = out[fileId];
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
    List<PatchLineComment> l = cache[side].remove(line);
    if (l != null) {
      for (final PatchLineComment c : l) {
        pLine.addComment(c);
        accountInfo.want(c.getAuthor());
      }
    }
  }

  protected Repository openRepository() throws Failure {
    if (repo == null) {
      if (change.getDest() == null) {
        throw new Failure(new CorruptEntityException(change.getId()));
      }
      try {
        repo = repoCache.get(change.getDest().getParentKey().get());
      } catch (InvalidRepositoryException err) {
        throw new Failure(err);
      }
    }
    return repo;
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
