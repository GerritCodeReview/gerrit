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
import com.google.gerrit.common.data.PatchScriptSettings;
import com.google.gerrit.common.data.PatchScriptSettings.Whitespace;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.FileTypeRegistry;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.Nullable;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;


class PatchScriptFactory extends Handler<PatchScript> {
  interface Factory {
    PatchScriptFactory create(Patch.Key patchKey,
        @Assisted("patchSetA") PatchSet.Id patchSetA,
        @Assisted("patchSetB") PatchSet.Id patchSetB,
        PatchScriptSettings settings);
  }

  private static final Logger log =
      LoggerFactory.getLogger(PatchScriptFactory.class);

  private final GitRepositoryManager repoManager;
  private final FileTypeRegistry registry;
  private final PatchListCache patchListCache;
  private final ReviewDb db;
  private final ChangeControl.Factory changeControlFactory;

  private final Patch.Key patchKey;
  @Nullable
  private final PatchSet.Id psa;
  private final PatchSet.Id psb;
  private final PatchScriptSettings settings;

  private final PatchSet.Id patchSetId;
  private final Change.Id changeId;

  private Change change;
  private PatchSet patchSet;
  private Project.NameKey projectKey;
  private Repository git;

  private ChangeControl control;

  private ObjectId aId;

  private ObjectId bId;

  @Inject
  PatchScriptFactory(final GitRepositoryManager grm, final FileTypeRegistry ftr,
      final PatchListCache patchListCache, final ReviewDb db,
      final ChangeControl.Factory changeControlFactory,
      @Assisted final Patch.Key patchKey,
      @Assisted("patchSetA") @Nullable final PatchSet.Id patchSetA,
      @Assisted("patchSetB") final PatchSet.Id patchSetB,
      @Assisted final PatchScriptSettings settings) {
    this.repoManager = grm;
    this.registry = ftr;
    this.patchListCache = patchListCache;
    this.db = db;
    this.changeControlFactory = changeControlFactory;

    this.patchKey = patchKey;
    this.psa = patchSetA;
    this.psb = patchSetB;
    this.settings = settings;

    patchSetId = patchKey.getParentKey();
    changeId = patchSetId.getParentKey();
  }

  @Override
  public PatchScript call() throws OrmException, NoSuchChangeException {
    validatePatchSetId(psa);
    validatePatchSetId(psb);

    control = changeControlFactory.validateFor(changeId);
    change = control.getChange();
    patchSet = db.patchSets().get(patchSetId);
    if (patchSet == null) {
      throw new NoSuchChangeException(changeId);
    }

    projectKey = change.getProject();
    aId = psa != null ? toObjectId(db, psa) : null;
    bId = toObjectId(db, psb);

    try {
      git = repoManager.openRepository(projectKey.get());
    } catch (RepositoryNotFoundException e) {
      log.error("Repository " + projectKey + " not found", e);
      throw new NoSuchChangeException(changeId, e);
    }

    final String fileName = patchKey.getFileName();
    try {
      final PatchList list = listFor(keyFor(settings.getWhitespace()));
      final PatchScriptBuilder b = newBuilder(list);
      final PatchListEntry contentWS = list.get(fileName);
      final CommentDetail comments = allComments(db);

      final PatchListEntry contentActual;
      if (settings.getWhitespace() == Whitespace.IGNORE_NONE) {
        contentActual = contentWS;
      } else {
        // If we are ignoring whitespace in some form, we still need to know
        // where the post-image differs so we can ensure the post-image lines
        // are still packed for the client to display.
        //
        contentActual = listFor(keyFor(Whitespace.IGNORE_NONE)).get(fileName);
      }

      try {
        return b.toPatchScript(contentWS, comments, contentActual);
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

  private PatchScriptBuilder newBuilder(final PatchList list)
      throws NoSuchChangeException {
    final PatchScriptSettings s = new PatchScriptSettings(settings);

    final int ctx = settings.getContext();
    if (ctx == AccountGeneralPreferences.WHOLE_FILE_CONTEXT)
      s.setContext(PatchScriptBuilder.MAX_CONTEXT);
    else if (0 <= ctx && ctx <= PatchScriptBuilder.MAX_CONTEXT)
      s.setContext(ctx);
    else
      throw new NoSuchChangeException(changeId);

    final PatchScriptBuilder b = new PatchScriptBuilder(registry);
    b.setRepository(git);
    b.setChange(change);
    b.setSettings(s);
    b.setTrees(list.getOldId(), list.getNewId());
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

  private CommentDetail allComments(final ReviewDb db) throws OrmException {
    final CommentDetail r = new CommentDetail(psa, psb);
    final String pn = patchKey.get();
    for (PatchLineComment p : db.patchComments().published(changeId, pn)) {
      r.include(p);
    }

    if (control.getCurrentUser() instanceof IdentifiedUser) {
      for (PatchLineComment p : db.patchComments().draft(changeId, pn,
          ((IdentifiedUser) control.getCurrentUser()).getAccountId())) {
        r.include(p);
      }
    }
    return r;
  }
}
