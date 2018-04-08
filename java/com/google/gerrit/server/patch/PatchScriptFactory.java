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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.CommentDetail;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.git.LargeObjectException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PatchScriptFactory implements Callable<PatchScript> {
  public interface Factory {
    PatchScriptFactory create(
        ChangeNotes notes,
        String fileName,
        @Assisted("patchSetA") PatchSet.Id patchSetA,
        @Assisted("patchSetB") PatchSet.Id patchSetB,
        DiffPreferencesInfo diffPrefs);

    PatchScriptFactory create(
        ChangeNotes notes,
        String fileName,
        int parentNum,
        PatchSet.Id patchSetB,
        DiffPreferencesInfo diffPrefs);
  }

  private static final Logger log = LoggerFactory.getLogger(PatchScriptFactory.class);

  private final GitRepositoryManager repoManager;
  private final PatchSetUtil psUtil;
  private final Provider<PatchScriptBuilder> builderFactory;
  private final PatchListCache patchListCache;
  private final ReviewDb db;
  private final CommentsUtil commentsUtil;

  private final String fileName;
  @Nullable private final PatchSet.Id psa;
  private final int parentNum;
  private final PatchSet.Id psb;
  private final DiffPreferencesInfo diffPrefs;
  private final ChangeEditUtil editReader;
  private final Provider<CurrentUser> userProvider;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private Optional<ChangeEdit> edit;

  private final Change.Id changeId;
  private boolean loadHistory = true;
  private boolean loadComments = true;

  private ChangeNotes notes;
  private ObjectId aId;
  private ObjectId bId;
  private List<Patch> history;
  private CommentDetail comments;

  @AssistedInject
  PatchScriptFactory(
      GitRepositoryManager grm,
      PatchSetUtil psUtil,
      Provider<PatchScriptBuilder> builderFactory,
      PatchListCache patchListCache,
      ReviewDb db,
      CommentsUtil commentsUtil,
      ChangeEditUtil editReader,
      Provider<CurrentUser> userProvider,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      @Assisted ChangeNotes notes,
      @Assisted String fileName,
      @Assisted("patchSetA") @Nullable PatchSet.Id patchSetA,
      @Assisted("patchSetB") PatchSet.Id patchSetB,
      @Assisted DiffPreferencesInfo diffPrefs) {
    this.repoManager = grm;
    this.psUtil = psUtil;
    this.builderFactory = builderFactory;
    this.patchListCache = patchListCache;
    this.db = db;
    this.notes = notes;
    this.commentsUtil = commentsUtil;
    this.editReader = editReader;
    this.userProvider = userProvider;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;

    this.fileName = fileName;
    this.psa = patchSetA;
    this.parentNum = -1;
    this.psb = patchSetB;
    this.diffPrefs = diffPrefs;

    changeId = patchSetB.getParentKey();
  }

  @AssistedInject
  PatchScriptFactory(
      GitRepositoryManager grm,
      PatchSetUtil psUtil,
      Provider<PatchScriptBuilder> builderFactory,
      PatchListCache patchListCache,
      ReviewDb db,
      CommentsUtil commentsUtil,
      ChangeEditUtil editReader,
      Provider<CurrentUser> userProvider,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      @Assisted ChangeNotes notes,
      @Assisted String fileName,
      @Assisted int parentNum,
      @Assisted PatchSet.Id patchSetB,
      @Assisted DiffPreferencesInfo diffPrefs) {
    this.repoManager = grm;
    this.psUtil = psUtil;
    this.builderFactory = builderFactory;
    this.patchListCache = patchListCache;
    this.db = db;
    this.notes = notes;
    this.commentsUtil = commentsUtil;
    this.editReader = editReader;
    this.userProvider = userProvider;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;

    this.fileName = fileName;
    this.psa = null;
    this.parentNum = parentNum;
    this.psb = patchSetB;
    this.diffPrefs = diffPrefs;

    changeId = patchSetB.getParentKey();
    checkArgument(parentNum >= 0, "parentNum must be >= 0");
  }

  public void setLoadHistory(boolean load) {
    loadHistory = load;
  }

  public void setLoadComments(boolean load) {
    loadComments = load;
  }

  @Override
  public PatchScript call()
      throws OrmException, LargeObjectException, AuthException, InvalidChangeOperationException,
          IOException, PermissionBackendException {
    if (parentNum < 0) {
      validatePatchSetId(psa);
    }
    validatePatchSetId(psb);

    PatchSet psEntityA = psa != null ? psUtil.get(db, notes, psa) : null;
    PatchSet psEntityB = psb.get() == 0 ? new PatchSet(psb) : psUtil.get(db, notes, psb);
    if (psEntityA != null || psEntityB != null) {
      try {
        permissionBackend.currentUser().change(notes).database(db).check(ChangePermission.READ);
      } catch (AuthException e) {
        throw new NoSuchChangeException(changeId);
      }
    }

    if (!projectCache.checkedGet(notes.getProjectName()).statePermitsRead()) {
      throw new NoSuchChangeException(changeId);
    }

    try (Repository git = repoManager.openRepository(notes.getProjectName())) {
      bId = toObjectId(psEntityB);
      if (parentNum < 0) {
        aId = psEntityA != null ? toObjectId(psEntityA) : null;
      }

      try {
        final PatchList list = listFor(keyFor(diffPrefs.ignoreWhitespace));
        final PatchScriptBuilder b = newBuilder(list, git);
        final PatchListEntry content = list.get(fileName);

        loadCommentsAndHistory(content.getChangeType(), content.getOldName(), content.getNewName());

        return b.toPatchScript(content, comments, history);
      } catch (PatchListNotAvailableException e) {
        throw new NoSuchChangeException(changeId, e);
      } catch (IOException e) {
        log.error("File content unavailable", e);
        throw new NoSuchChangeException(changeId, e);
      } catch (org.eclipse.jgit.errors.LargeObjectException err) {
        throw new LargeObjectException("File content is too large", err);
      }
    } catch (RepositoryNotFoundException e) {
      log.error("Repository " + notes.getProjectName() + " not found", e);
      throw new NoSuchChangeException(changeId, e);
    } catch (IOException e) {
      log.error("Cannot open repository " + notes.getProjectName(), e);
      throw new NoSuchChangeException(changeId, e);
    }
  }

  private PatchListKey keyFor(Whitespace whitespace) {
    if (parentNum < 0) {
      return PatchListKey.againstCommit(aId, bId, whitespace);
    }
    return PatchListKey.againstParentNum(parentNum + 1, bId, whitespace);
  }

  private PatchList listFor(PatchListKey key) throws PatchListNotAvailableException {
    return patchListCache.get(key, notes.getProjectName());
  }

  private PatchScriptBuilder newBuilder(PatchList list, Repository git) {
    final PatchScriptBuilder b = builderFactory.get();
    b.setRepository(git, notes.getProjectName());
    b.setChange(notes.getChange());
    b.setDiffPrefs(diffPrefs);
    b.setTrees(list.getComparisonType(), list.getOldId(), list.getNewId());
    return b;
  }

  private ObjectId toObjectId(PatchSet ps) throws AuthException, IOException, OrmException {
    if (ps.getId().get() == 0) {
      return getEditRev();
    }
    if (ps.getRevision() == null || ps.getRevision().get() == null) {
      throw new NoSuchChangeException(changeId);
    }

    try {
      return ObjectId.fromString(ps.getRevision().get());
    } catch (IllegalArgumentException e) {
      log.error("Patch set " + ps.getId() + " has invalid revision");
      throw new NoSuchChangeException(changeId, e);
    }
  }

  private ObjectId getEditRev() throws AuthException, IOException, OrmException {
    edit = editReader.byChange(notes);
    if (edit.isPresent()) {
      return edit.get().getEditCommit();
    }
    throw new NoSuchChangeException(notes.getChangeId());
  }

  private void validatePatchSetId(PatchSet.Id psId) throws NoSuchChangeException {
    if (psId == null) { // OK, means use base;
    } else if (changeId.equals(psId.getParentKey())) { // OK, same change;
    } else {
      throw new NoSuchChangeException(changeId);
    }
  }

  private void loadCommentsAndHistory(ChangeType changeType, String oldName, String newName)
      throws OrmException {
    Map<Patch.Key, Patch> byKey = new HashMap<>();

    if (loadHistory) {
      // This seems like a cheap trick. It doesn't properly account for a
      // file that gets renamed between patch set 1 and patch set 2. We
      // will wind up packing the wrong Patch object because we didn't do
      // proper rename detection between the patch sets.
      //
      history = new ArrayList<>();
      for (PatchSet ps : psUtil.byChange(db, notes)) {
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

        Patch p = new Patch(new Patch.Key(ps.getId(), name));
        history.add(p);
        byKey.put(p.getKey(), p);
      }
      if (edit != null && edit.isPresent()) {
        Patch p = new Patch(new Patch.Key(new PatchSet.Id(psb.getParentKey(), 0), fileName));
        history.add(p);
        byKey.put(p.getKey(), p);
      }
    }

    if (loadComments && edit == null) {
      comments = new CommentDetail(psa, psb);
      switch (changeType) {
        case ADDED:
        case MODIFIED:
          loadPublished(byKey, newName);
          break;

        case DELETED:
          loadPublished(byKey, newName);
          break;

        case COPIED:
        case RENAMED:
          if (psa != null) {
            loadPublished(byKey, oldName);
          }
          loadPublished(byKey, newName);
          break;

        case REWRITE:
          break;
      }

      CurrentUser user = userProvider.get();
      if (user.isIdentifiedUser()) {
        Account.Id me = user.getAccountId();
        switch (changeType) {
          case ADDED:
          case MODIFIED:
            loadDrafts(byKey, me, newName);
            break;

          case DELETED:
            loadDrafts(byKey, me, newName);
            break;

          case COPIED:
          case RENAMED:
            if (psa != null) {
              loadDrafts(byKey, me, oldName);
            }
            loadDrafts(byKey, me, newName);
            break;

          case REWRITE:
            break;
        }
      }
    }
  }

  private void loadPublished(Map<Patch.Key, Patch> byKey, String file) throws OrmException {
    for (Comment c : commentsUtil.publishedByChangeFile(db, notes, changeId, file)) {
      comments.include(notes.getChangeId(), c);
      PatchSet.Id psId = new PatchSet.Id(notes.getChangeId(), c.key.patchSetId);
      Patch.Key pKey = new Patch.Key(psId, c.key.filename);
      Patch p = byKey.get(pKey);
      if (p != null) {
        p.setCommentCount(p.getCommentCount() + 1);
      }
    }
  }

  private void loadDrafts(Map<Patch.Key, Patch> byKey, Account.Id me, String file)
      throws OrmException {
    for (Comment c : commentsUtil.draftByChangeFileAuthor(db, notes, file, me)) {
      comments.include(notes.getChangeId(), c);
      PatchSet.Id psId = new PatchSet.Id(notes.getChangeId(), c.key.patchSetId);
      Patch.Key pKey = new Patch.Key(psId, c.key.filename);
      Patch p = byKey.get(pKey);
      if (p != null) {
        p.setDraftCount(p.getDraftCount() + 1);
      }
    }
  }
}
