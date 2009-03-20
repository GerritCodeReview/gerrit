// Copyright (C) 2008 The Android Open Source Project
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


abstract class PatchDetailAction<T> implements Action<T> {
  protected final Patch.Key patchKey;
  protected final List<PatchSet.Id> requestedVersions;
  private final boolean direct;
  private final RepositoryCache repoCache;
  protected Change change;
  protected Patch patch;
  protected PatchFile file;
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

    try {
      file = new PatchFile(repoCache, change, db, patch);
      if (!direct) {
        file.setRequestedVersions(requestedVersions);
      }
    } catch (InvalidRepositoryException e) {
      throw new Failure(e);
    }

    accountInfo = new AccountInfoCacheFactory(db);
    me = Common.getAccountId();

    final int fileCnt;
    try {
      fileCnt = file.getFileCount();
    } catch (CorruptEntityException e) {
      throw new Failure(e);
    } catch (NoSuchEntityException e) {
      throw new Failure(e);
    } catch (IOException e) {
      throw new Failure(e);
    } catch (NoDifferencesException e) {
      throw new Failure(e);
    }

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
}
