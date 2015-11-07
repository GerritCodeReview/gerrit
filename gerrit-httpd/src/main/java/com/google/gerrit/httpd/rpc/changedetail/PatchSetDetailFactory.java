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

package com.google.gerrit.httpd.rpc.changedetail;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Optional;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.DiffType;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountPatchReview;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Creates a {@link PatchSetDetail} from a {@link PatchSet}. */
class PatchSetDetailFactory extends Handler<PatchSetDetail> {

  private static final Logger log =
    LoggerFactory.getLogger(PatchSetDetailFactory.class);

  interface Factory {
    PatchSetDetailFactory create(
        @Assisted("psIdBase") @Nullable PatchSet.Id psIdBase,
        @Assisted("psIdNew") PatchSet.Id psIdNew,
        @Nullable DiffPreferencesInfo diffPrefs,
        @Nullable DiffType difType);
  }

  private final PatchSetInfoFactory infoFactory;
  private final ReviewDb db;
  private final PatchListCache patchListCache;
  private final Provider<CurrentUser> userProvider;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final PatchLineCommentsUtil plcUtil;
  private final PatchSetUtil psUtil;
  private final ChangeEditUtil editUtil;

  private Project.NameKey project;
  private final PatchSet.Id psIdBase;
  private final PatchSet.Id psIdNew;
  private final DiffType diffType;
  private final DiffPreferencesInfo diffPrefs;
  private ObjectId oldId;
  private ObjectId newId;

  private PatchSetDetail detail;
  ChangeControl control;
  PatchSet patchSet;

  @Inject
  PatchSetDetailFactory(final PatchSetInfoFactory psif, final ReviewDb db,
      final PatchListCache patchListCache,
      final Provider<CurrentUser> userProvider,
      final ChangeControl.GenericFactory changeControlFactory,
      final PatchLineCommentsUtil plcUtil,
      final PatchSetUtil psUtil,
      ChangeEditUtil editUtil,
      @Assisted("psIdBase") @Nullable final PatchSet.Id psIdBase,
      @Assisted("psIdNew") final PatchSet.Id psIdNew,
      @Assisted @Nullable final DiffPreferencesInfo diffPrefs,
      @Assisted @Nullable DiffType diffType) {
    this.infoFactory = psif;
    this.db = db;
    this.patchListCache = patchListCache;
    this.userProvider = userProvider;
    this.changeControlFactory = changeControlFactory;
    this.plcUtil = plcUtil;
    this.psUtil = psUtil;
    this.editUtil = editUtil;

    this.psIdBase = psIdBase;
    this.psIdNew = psIdNew;
    this.diffType = diffType;
    if (psIdBase != null && psIdNew != null) {
      checkArgument(psIdBase.getParentKey().equals(psIdNew.getParentKey()),
          "cannot compare PatchSets from different changes: %s and %s",
          psIdBase, psIdNew);
    }
    this.diffPrefs = diffPrefs;
  }

  @Override
  public PatchSetDetail call() throws OrmException, NoSuchEntityException,
      PatchSetInfoNotAvailableException, NoSuchChangeException, AuthException,
      IOException {
    Optional<ChangeEdit> edit = null;
    ChangeNotes notes;
    if (control == null || patchSet == null) {
      control = changeControlFactory.validateFor(db, psIdNew.getParentKey(),
          userProvider.get());
      notes = control.getNotes();
      if (psIdNew.get() == 0) {
        edit = editUtil.byChange(control.getChange());
        if (edit.isPresent()) {
          patchSet = edit.get().getBasePatchSet();
        }
      } else {
        patchSet = psUtil.get(db, notes, psIdNew);
      }
      if (patchSet == null) {
        throw new NoSuchEntityException();
      }
    } else {
      notes = control.getNotes();
    }
    project = control.getProject().getNameKey();
    final PatchList list;

    try {
      if (psIdBase != null) {
        oldId = toObjectId(psUtil.get(db, notes, psIdBase));
        if (edit != null && edit.isPresent()) {
          newId = edit.get().getEditCommit().toObjectId();
        } else {
          newId = toObjectId(patchSet);
        }

        list = listFor(keyFor(diffPrefs.ignoreWhitespace));
      } else { // OK, means use base to compare
        list = patchListCache.get(control.getChange(), patchSet,
            diffType == null ? DiffType.AUTO_MERGE : diffType);
      }
    } catch (PatchListNotAvailableException e) {
      throw new NoSuchEntityException();
    }

    final List<Patch> patches = list.toPatchList(patchSet.getId());
    final Map<Patch.Key, Patch> byKey = new HashMap<>();
    for (final Patch p : patches) {
      byKey.put(p.getKey(), p);
    }

    if (edit == null) {
      for (PatchLineComment c : plcUtil.publishedByPatchSet(db, notes, psIdNew)) {
        final Patch p = byKey.get(c.getKey().getParentKey());
        if (p != null) {
          p.setCommentCount(p.getCommentCount() + 1);
        }
      }
    }

    detail = new PatchSetDetail();
    detail.setPatchSet(patchSet);
    detail.setProject(project);

    detail.setInfo(infoFactory.get(db, notes, patchSet.getId()));
    detail.setPatches(patches);

    final CurrentUser user = control.getUser();
    if (user.isIdentifiedUser() && edit == null) {
      // If we are signed in, compute the number of draft comments by the
      // current user on each of these patch files. This way they can more
      // quickly locate where they have pending drafts, and review them.
      //
      final Account.Id me = user.getAccountId();
      for (PatchLineComment c
          : plcUtil.draftByPatchSetAuthor(db, psIdNew, me, notes)) {
        final Patch p = byKey.get(c.getKey().getParentKey());
        if (p != null) {
          p.setDraftCount(p.getDraftCount() + 1);
        }
      }

      for (AccountPatchReview r : db.accountPatchReviews().byReviewer(me, psIdNew)) {
        final Patch p = byKey.get(r.getKey().getPatchKey());
        if (p != null) {
          p.setReviewedByCurrentUser(true);
        }
      }
    }

    return detail;
  }

  private ObjectId toObjectId(PatchSet ps) throws NoSuchEntityException {
    try {
      return ObjectId.fromString(ps.getRevision().get());
    } catch (IllegalArgumentException e) {
      log.error("Patch set " + ps.getId() + " has invalid revision");
      throw new NoSuchEntityException();
    }
  }

  private PatchListKey keyFor(Whitespace whitespace) {
    return new PatchListKey(oldId, newId, whitespace);
  }

  private PatchList listFor(PatchListKey key)
      throws PatchListNotAvailableException {
    return patchListCache.get(key, project);
  }
}
