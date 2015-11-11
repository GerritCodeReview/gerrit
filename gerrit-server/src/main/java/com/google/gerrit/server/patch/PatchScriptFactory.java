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

import com.google.common.base.Optional;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.CommentDetail;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
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
import java.util.concurrent.Callable;


public class PatchScriptFactory implements Callable<PatchScript> {
  public interface Factory {
    PatchScriptFactory create(
        ChangeControl control,
        String fileName,
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
  private final AccountInfoCacheFactory.Factory aicFactory;
  private final PatchLineCommentsUtil plcUtil;

  private final String fileName;
  @Nullable
  private final PatchSet.Id psa;
  private final PatchSet.Id psb;
  private final AccountDiffPreference diffPrefs;
  private final ChangeEditUtil editReader;
  private Optional<ChangeEdit> edit;

  private final Change.Id changeId;
  private boolean loadHistory = true;
  private boolean loadComments = true;

  private Change change;
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
      final AccountInfoCacheFactory.Factory aicFactory,
      PatchLineCommentsUtil plcUtil,
      ChangeEditUtil editReader,
      @Assisted ChangeControl control,
      @Assisted final String fileName,
      @Assisted("patchSetA") @Nullable final PatchSet.Id patchSetA,
      @Assisted("patchSetB") final PatchSet.Id patchSetB,
      @Assisted final AccountDiffPreference diffPrefs) {
    this.repoManager = grm;
    this.builderFactory = builderFactory;
    this.patchListCache = patchListCache;
    this.db = db;
    this.control = control;
    this.aicFactory = aicFactory;
    this.plcUtil = plcUtil;
    this.editReader = editReader;

    this.fileName = fileName;
    this.psa = patchSetA;
    this.psb = patchSetB;
    this.diffPrefs = diffPrefs;

    changeId = patchSetB.getParentKey();
  }

  public void setLoadHistory(boolean load) {
    loadHistory = load;
  }

  public void setLoadComments(boolean load) {
    loadComments = load;
  }

  @Override
  public PatchScript call() throws OrmException, NoSuchChangeException,
      LargeObjectException, AuthException,
      InvalidChangeOperationException, IOException {
    validatePatchSetId(psa);
    validatePatchSetId(psb);

    change = control.getChange();
    projectKey = change.getProject();

    aId = psa != null ? toObjectId(db, psa) : null;
    bId = toObjectId(db, psb);

    if ((psa != null && !control.isPatchVisible(db.patchSets().get(psa), db)) ||
        (psb != null && !control.isPatchVisible(db.patchSets().get(psb), db))) {
      throw new NoSuchChangeException(changeId);
    }

    final Repository git;
    try {
      git = repoManager.openRepository(projectKey);
    } catch (RepositoryNotFoundException e) {
      log.error("Repository " + projectKey + " not found", e);
      throw new NoSuchChangeException(changeId, e);
    } catch (IOException e) {
      log.error("Cannot open repository " + projectKey, e);
      throw new NoSuchChangeException(changeId, e);
    }
    try {
      final PatchList list = listFor(keyFor(diffPrefs.getIgnoreWhitespace()));
      final PatchScriptBuilder b = newBuilder(list, git);
      final PatchListEntry content = list.get(fileName);

      loadCommentsAndHistory(content.getChangeType(), //
          content.getOldName(), //
          content.getNewName());

      return b.toPatchScript(content, comments, history);
    } catch (PatchListNotAvailableException e) {
      throw new NoSuchChangeException(changeId, e);
    } catch (IOException e) {
      log.error("File content unavailable", e);
      throw new NoSuchChangeException(changeId, e);
    } catch (org.eclipse.jgit.errors.LargeObjectException err) {
      throw new LargeObjectException("File content is too large", err);
    } finally {
      git.close();
    }
  }

  private PatchListKey keyFor(final Whitespace whitespace) {
    return new PatchListKey(projectKey, aId, bId, whitespace);
  }

  private PatchList listFor(final PatchListKey key)
      throws PatchListNotAvailableException {
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
      throws OrmException, NoSuchChangeException, AuthException,
      NoSuchChangeException, IOException {
    if (!changeId.equals(psId.getParentKey())) {
      throw new NoSuchChangeException(changeId);
    }

    if (psId.get() == 0) {
      return getEditRev();
    }
    PatchSet ps = db.patchSets().get(psId);
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

  private ObjectId getEditRev() throws AuthException,
      NoSuchChangeException, IOException {
    edit = editReader.byChange(change);
    if (edit.isPresent()) {
      return edit.get().getRef().getObjectId();
    }
    throw new NoSuchChangeException(change.getId());
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
    final Map<Patch.Key, Patch> byKey = new HashMap<>();

    if (loadHistory) {
      // This seems like a cheap trick. It doesn't properly account for a
      // file that gets renamed between patch set 1 and patch set 2. We
      // will wind up packing the wrong Patch object because we didn't do
      // proper rename detection between the patch sets.
      //
      history = new ArrayList<>();
      for (final PatchSet ps : db.patchSets().byChange(changeId)) {
        if (!control.isPatchVisible(ps, db)) {
          continue;
        }
        String name = fileName;
        if (psa != null) {
          switch (changeType) {
            case COPIED:
            case RENAMED:
              if (ps.getId().equals(psa)) {
                name = oldName;
              }
              break;

            case MODIFIED:
            case DELETED:
            case ADDED:
            case REWRITE:
              break;
          }
        }

        final Patch p = new Patch(new Patch.Key(ps.getId(), name));
        history.add(p);
        byKey.put(p.getKey(), p);
      }
      if (edit != null && edit.isPresent()) {
        final Patch p = new Patch(new Patch.Key(
            new PatchSet.Id(psb.getParentKey(), 0), fileName));
        history.add(p);
        byKey.put(p.getKey(), p);
      }
    }

    if (loadComments) {
      final AccountInfoCacheFactory aic = aicFactory.create();
      comments = new CommentDetail(psa, psb);
      switch (changeType) {
        case ADDED:
        case MODIFIED:
          loadPublished(byKey, aic, newName);
          break;

        case DELETED:
          loadPublished(byKey, aic, newName);
          break;

        case COPIED:
        case RENAMED:
          if (psa != null) {
            loadPublished(byKey, aic, oldName);
          }
          loadPublished(byKey, aic, newName);
          break;

        case REWRITE:
          break;
      }

      final CurrentUser user = control.getCurrentUser();
      if (user.isIdentifiedUser()) {
        final Account.Id me = ((IdentifiedUser) user).getAccountId();
        switch (changeType) {
          case ADDED:
          case MODIFIED:
            loadDrafts(byKey, aic, me, newName);
            break;

          case DELETED:
            loadDrafts(byKey, aic, me, newName);
            break;

          case COPIED:
          case RENAMED:
            if (psa != null) {
              loadDrafts(byKey, aic, me, oldName);
            }
            loadDrafts(byKey, aic, me, newName);
            break;

          case REWRITE:
            break;
        }
      }

      comments.setAccountInfoCache(aic.create());
    }
  }

  private void loadPublished(final Map<Patch.Key, Patch> byKey,
      final AccountInfoCacheFactory aic, final String file) throws OrmException {
    ChangeNotes notes = control.getNotes();
    PatchSet.Id editParent = null;
    if (edit != null && edit.isPresent()) {
      editParent = edit.get().getBasePatchSet().getId();
    }
    for (PatchLineComment c : plcUtil.publishedByChangeFile(db, notes, changeId, file)) {
      if (comments.include(c, editParent)) {
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
    for (PatchLineComment c :
        plcUtil.draftByChangeFileAuthor(db, control.getNotes(), file, me)) {
      if (comments.include(c, null)) {
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
