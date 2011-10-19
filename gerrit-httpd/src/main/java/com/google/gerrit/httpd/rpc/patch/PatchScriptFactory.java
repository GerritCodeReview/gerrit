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

package com.google.gerrit.httpd.rpc.patch;

import com.google.gerrit.common.data.CommentDetail;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountDiffPreference;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.Patch.ChangeType;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;


class PatchScriptFactory extends Handler<PatchScript> {
  interface Factory {
    PatchScriptFactory create(Patch.Key patchKey,
        @Assisted("patchSetA") PatchSet.Id patchSetA,
        @Assisted("patchSetB") PatchSet.Id patchSetB,
        AccountDiffPreference diffPrefs);
  }

  private static final Logger log =
      LoggerFactory.getLogger(PatchScriptFactory.class);

  private final GitRepositoryManager repoManager;
  private final Provider<PatchScriptBuilder> builderFactory;
  private final PatchListCache patchListCache;
  private final ReviewDb db;
  private final ChangeControl.Factory changeControlFactory;
  private final AccountInfoCacheFactory.Factory aicFactory;

  private final Patch.Key patchKey;
  @Nullable
  private final PatchSet.Id psa;
  private final PatchSet.Id psb;
  private final AccountDiffPreference diffPrefs;

  private final PatchSet.Id patchSetId;
  private final Change.Id changeId;

  private Change change;
  private PatchSet patchSet;
  private Project.NameKey projectKey;
  private ChangeControl control;
  private ObjectId aId;
  private ObjectId bId;
  private List<Patch> history;
  private CommentDetail comments;

  @Inject
  PatchScriptFactory(final GitRepositoryManager grm,
      Provider<PatchScriptBuilder> builderFactory,
      final PatchListCache patchListCache, final ReviewDb db,
      final ChangeControl.Factory changeControlFactory,
      final AccountInfoCacheFactory.Factory aicFactory,
      @Assisted final Patch.Key patchKey,
      @Assisted("patchSetA") @Nullable final PatchSet.Id patchSetA,
      @Assisted("patchSetB") final PatchSet.Id patchSetB,
      @Assisted final AccountDiffPreference diffPrefs) {
    this.repoManager = grm;
    this.builderFactory = builderFactory;
    this.patchListCache = patchListCache;
    this.db = db;
    this.changeControlFactory = changeControlFactory;
    this.aicFactory = aicFactory;

    this.patchKey = patchKey;
    this.psa = patchSetA;
    this.psb = patchSetB;
    this.diffPrefs = diffPrefs;

    patchSetId = patchKey.getParentKey();
    changeId = patchSetId.getParentKey();
  }

  @Override
  public PatchScript call() throws OrmException, NoSuchChangeException {
    validatePatchSetId(psa);
    validatePatchSetId(psb);

    control = changeControlFactory.validateFor(changeId);
    change = control.getChange();
    projectKey = change.getProject();
    patchSet = db.patchSets().get(patchSetId);
    if (patchSet == null) {
      throw new NoSuchChangeException(changeId);
    }

    aId = psa != null ? toObjectId(db, psa) : null;
    bId = toObjectId(db, psb);

    final Repository git;
    try {
      git = repoManager.openRepository(projectKey);
    } catch (RepositoryNotFoundException e) {
      log.error("Repository " + projectKey + " not found", e);
      throw new NoSuchChangeException(changeId, e);
    }
    try {
      final PatchList list = listFor(keyFor(diffPrefs.getIgnoreWhitespace()));
      final PatchScriptBuilder b = newBuilder(list, git);
      final PatchListEntry content = list.get(patchKey.getFileName());

      loadCommentsAndHistory(content.getChangeType(), //
          content.getOldName(), //
          content.getNewName());

      try {
        return b.toPatchScript(content, comments, history);
      } catch (IOException e) {
        log.error("File content unavailable", e);
        throw new NoSuchChangeException(changeId, e);
      }
    } finally {
      git.close();
    }
  }

  private PatchListKey keyFor(final Whitespace whitespace) {
    return new PatchListKey(projectKey, aId, bId, whitespace);
  }

  private PatchList listFor(final PatchListKey key) {
    return patchListCache.get(key);
  }

  private PatchScriptBuilder newBuilder(final PatchList list, Repository git) {
    final AccountDiffPreference dp = new AccountDiffPreference(diffPrefs);
    final PatchScriptBuilder b = builderFactory.get();
    b.setRepository(git, projectKey);
    b.setChange(change);
    b.setDiffPrefs(dp);
    b.setTrees(list.isAgainstParent(), list.getOldId(), list.getNewId());
    return b;
  }

  private ObjectId toObjectId(final ReviewDb db, final PatchSet.Id psId)
      throws OrmException, NoSuchChangeException {
    if (!changeId.equals(psId.getParentKey())) {
      throw new NoSuchChangeException(changeId);
    }

    final PatchSet ps = db.patchSets().get(psId);
    if (ps == null || ps.getRevision() == null
        || ps.getRevision().get() == null) {
      throw new NoSuchChangeException(changeId);
    }

    try {
      return ObjectId.fromString(ps.getRevision().get());
    } catch (IllegalArgumentException e) {
      log.error("Patch set " + psId + " has invalid revision");
      throw new NoSuchChangeException(changeId, e);
    }
  }

  private void validatePatchSetId(final PatchSet.Id psId)
      throws NoSuchChangeException {
    if (psId == null) { // OK, means use base;
    } else if (changeId.equals(psId.getParentKey())) { // OK, same change;
    } else {
      throw new NoSuchChangeException(changeId);
    }
  }

  private void loadCommentsAndHistory(final ChangeType changeType,
      final String oldName, final String newName) throws OrmException {
    history = new ArrayList<Patch>();
    comments = new CommentDetail(psa, psb);

    final Map<Patch.Key, Patch> byKey = new HashMap<Patch.Key, Patch>();
    final AccountInfoCacheFactory aic = aicFactory.create();

    // This seems like a cheap trick. It doesn't properly account for a
    // file that gets renamed between patch set 1 and patch set 2. We
    // will wind up packing the wrong Patch object because we didn't do
    // proper rename detection between the patch sets.
    //
    for (final PatchSet ps : db.patchSets().byChange(changeId)) {
      String name = patchKey.get();
      if (psa != null) {
        switch (changeType) {
          case COPIED:
          case RENAMED:
            if (ps.getId().equals(psa)) {
              name = oldName;
            }
            break;
        }
      }

      final Patch p = new Patch(new Patch.Key(ps.getId(), name));
      history.add(p);
      byKey.put(p.getKey(), p);
    }

    switch (changeType) {
      case ADDED:
      case MODIFIED:
        loadPublished(byKey, aic, newName);
        break;

      case DELETED:
        loadPublished(byKey, aic, oldName);
        break;

      case COPIED:
      case RENAMED:
        if (psa != null) {
          loadPublished(byKey, aic, oldName);
        }
        loadPublished(byKey, aic, newName);
        break;
    }

    final CurrentUser user = control.getCurrentUser();
    if (user instanceof IdentifiedUser) {
      final Account.Id me = ((IdentifiedUser) user).getAccountId();
      switch (changeType) {
        case ADDED:
        case MODIFIED:
          loadDrafts(byKey, aic, me, newName);
          break;

        case DELETED:
          loadDrafts(byKey, aic, me, oldName);
          break;

        case COPIED:
        case RENAMED:
          if (psa != null) {
            loadDrafts(byKey, aic, me, oldName);
          }
          loadDrafts(byKey, aic, me, newName);
          break;
      }
    }

    comments.setAccountInfoCache(aic.create());
  }

  private void loadPublished(final Map<Patch.Key, Patch> byKey,
      final AccountInfoCacheFactory aic, final String file) throws OrmException {
    for (PatchLineComment c : db.patchComments().publishedByChangeFile(changeId, file)) {
      if (comments.include(c)) {
        aic.want(c.getAuthor());
      }

      final Patch.Key pKey = c.getKey().getParentKey();
      final Patch p = byKey.get(pKey);
      if (p != null) {
        p.setCommentCount(p.getCommentCount() + 1);
      }
    }
  }

  private void loadDrafts(final Map<Patch.Key, Patch> byKey,
      final AccountInfoCacheFactory aic, final Account.Id me, final String file)
      throws OrmException {
    for (PatchLineComment c : db.patchComments().draftByChangeFileAuthor(changeId, file, me)) {
      if (comments.include(c)) {
        aic.want(me);
      }

      final Patch.Key pKey = c.getKey().getParentKey();
      final Patch p = byKey.get(pKey);
      if (p != null) {
        p.setDraftCount(p.getDraftCount() + 1);
      }
    }
  }
}
