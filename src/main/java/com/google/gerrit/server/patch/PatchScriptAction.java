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

import static com.google.gerrit.client.rpc.BaseServiceImplementation.canRead;

import com.google.gerrit.client.data.PatchScript;
import com.google.gerrit.client.data.PatchScriptSettings;
import com.google.gerrit.client.data.PatchScriptSettings.Whitespace;
import com.google.gerrit.client.patches.CommentDetail;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGeneralPreferences;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.CorruptEntityException;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.client.rpc.BaseServiceImplementation.Action;
import com.google.gerrit.client.rpc.BaseServiceImplementation.Failure;
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gerrit.git.RepositoryCache;
import com.google.gerrit.server.GerritServer;
import com.google.gwtorm.client.OrmException;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Element;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ObjectWriter;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevWalk;

import java.io.IOException;


class PatchScriptAction implements Action<PatchScript> {
  private static final Logger log =
      LoggerFactory.getLogger(PatchScriptAction.class);

  private final GerritServer server;
  private final RepositoryCache rc;
  private final Patch.Key patchKey;
  private final PatchSet.Id psa;
  private final PatchSet.Id psb;
  private final PatchScriptSettings settings;

  private final PatchSet.Id patchSetId;
  private final Change.Id changeId;

  private Change change;
  private Patch patch;
  private Project.NameKey projectKey;
  private Repository git;

  PatchScriptAction(final GerritServer gs, final RepositoryCache rc,
      final Patch.Key patchKey, final PatchSet.Id psa, final PatchSet.Id psb,
      final PatchScriptSettings settings) {
    this.server = gs;
    this.rc = rc;
    this.patchKey = patchKey;
    this.psa = psa;
    this.psb = psb;
    this.settings = settings;

    patchSetId = patchKey.getParentKey();
    changeId = patchSetId.getParentKey();
  }

  public PatchScript run(final ReviewDb db) throws OrmException, Failure {
    validatePatchSetId(psa);
    validatePatchSetId(psb);

    change = db.changes().get(changeId);
    patch = db.patches().get(patchKey);

    if (change == null || patch == null || !canRead(change)) {
      throw new Failure(new NoSuchEntityException());
    }

    projectKey = change.getDest().getParentKey();
    try {
      git = rc.get(projectKey.get());
    } catch (InvalidRepositoryException e) {
      log.error("Repository " + projectKey + " not found", e);
      throw new Failure(new NoSuchEntityException());
    }

    final PatchScriptBuilder b = newBuilder();
    final ObjectId bId = toObjectId(db, psb);
    final ObjectId aId = psa == null ? ancestor(bId) : toObjectId(db, psa);

    final DiffCacheKey key = keyFor(bId, aId);
    final DiffCacheContent contentWS = get(key);
    final CommentDetail comments = allComments(db);

    final DiffCacheContent contentActual;
    if (settings.getWhitespace() != Whitespace.IGNORE_NONE) {
      // If we are ignoring whitespace in some form, we still need to know
      // where the post-image differs so we can ensure the post-image lines
      // are still packed for the client to display.
      //
      final PatchScriptSettings s = new PatchScriptSettings(settings);
      s.setWhitespace(Whitespace.IGNORE_NONE);
      contentActual = get(new DiffCacheKey(projectKey, aId, bId, patch, s));
    } else {
      contentActual = contentWS;
    }

    try {
      return b.toPatchScript(contentWS, comments, contentActual);
    } catch (CorruptEntityException e) {
      log.error("File content for " + key + " unavailable", e);
      throw new Failure(new NoSuchEntityException());
    }
  }

  private DiffCacheKey keyFor(final ObjectId bId, final ObjectId aId) {
    return new DiffCacheKey(projectKey, aId, bId, patch, settings);
  }

  private DiffCacheContent get(final DiffCacheKey key) throws Failure {
    final Element cacheElem;
    try {
      cacheElem = server.getDiffCache().get(key);
    } catch (IllegalStateException e) {
      log.error("Cache get failed for " + key, e);
      throw new Failure(new NoSuchEntityException());
    } catch (CacheException e) {
      log.error("Cache get failed for " + key, e);
      throw new Failure(new NoSuchEntityException());
    }
    if (cacheElem == null || cacheElem.getObjectValue() == null) {
      log.error("Cache get failed for " + key);
      throw new Failure(new NoSuchEntityException());
    }
    return (DiffCacheContent) cacheElem.getObjectValue();
  }

  private PatchScriptBuilder newBuilder() throws Failure {
    final PatchScriptSettings s = new PatchScriptSettings(settings);

    final int ctx = settings.getContext();
    if (ctx == AccountGeneralPreferences.WHOLE_FILE_CONTEXT)
      s.setContext(PatchScriptBuilder.MAX_CONTEXT);
    else if (0 <= ctx && ctx <= PatchScriptBuilder.MAX_CONTEXT)
      s.setContext(ctx);
    else
      throw new Failure(new NoSuchEntityException());

    final PatchScriptBuilder b = new PatchScriptBuilder();
    b.setRepository(git);
    b.setPatch(patch);
    b.setSettings(s);
    return b;
  }

  private ObjectId ancestor(final ObjectId id) throws Failure {
    try {
      final RevCommit c = new RevWalk(git).parseCommit(id);
      switch (c.getParentCount()) {
        case 0:
          return emptyTree();
        case 1:
          return c.getParent(0).getId();
        default:
          return null;
      }
    } catch (IOException e) {
      log.error("Commit information for " + id.name() + " unavailable", e);
      throw new Failure(new NoSuchEntityException());
    }
  }

  private ObjectId emptyTree() throws IOException {
    return new ObjectWriter(git).writeCanonicalTree(new byte[0]);
  }

  private ObjectId toObjectId(final ReviewDb db, final PatchSet.Id psId)
      throws Failure, OrmException {
    if (!changeId.equals(psId.getParentKey())) {
      throw new Failure(new NoSuchEntityException());
    }

    final PatchSet ps = db.patchSets().get(psId);
    if (ps == null || ps.getRevision() == null
        || ps.getRevision().get() == null) {
      throw new Failure(new NoSuchEntityException());
    }

    try {
      return ObjectId.fromString(ps.getRevision().get());
    } catch (IllegalArgumentException e) {
      log.error("Patch set " + psId + " has invalid revision");
      throw new Failure(new NoSuchEntityException());
    }
  }

  private void validatePatchSetId(final PatchSet.Id psId) throws Failure {
    if (psId == null) { // OK, means use base;
    } else if (changeId.equals(psId.getParentKey())) { // OK, same change;
    } else {
      throw new Failure(new NoSuchEntityException());
    }
  }

  private CommentDetail allComments(final ReviewDb db) throws OrmException {
    final CommentDetail r = new CommentDetail(psa, psb);
    final String pn = patchKey.get();
    for (PatchLineComment p : db.patchComments().published(changeId, pn)) {
      r.include(p);
    }

    final Account.Id me = Common.getAccountId();
    if (me != null) {
      for (PatchLineComment p : db.patchComments().draft(changeId, pn, me)) {
        r.include(p);
      }
    }
    return r;
  }
}
