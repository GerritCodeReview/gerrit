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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.UserIdentity;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Set;


/**
 * Factory class creating PatchSetInfo from meta-data found in Git repository.
 */
@Singleton
public class PatchSetInfoFactory {
  private final GitRepositoryManager repoManager;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final AccountByEmailCache byEmailCache;
  private final ProjectCache projectCache;

  @Inject
  public PatchSetInfoFactory(final GitRepositoryManager grm,
      final SchemaFactory<ReviewDb> schemaFactory,
      final AccountByEmailCache byEmailCache,
      final ProjectCache projectCache) {
    this.repoManager = grm;
    this.schemaFactory = schemaFactory;
    this.byEmailCache = byEmailCache;
    this.projectCache = projectCache;
  }

  public PatchSetInfo get(RevCommit src, PatchSet.Id psi) {
    PatchSetInfo info = new PatchSetInfo(psi);
    info.setSubject(src.getShortMessage());
    info.setMessage(src.getFullMessage());
    info.setAuthor(toUserIdentity(src.getAuthorIdent()));
    info.setCommitter(toUserIdentity(src.getCommitterIdent()));

    return info;
  }

  public PatchSetInfo get(PatchSet.Id patchSetId)
      throws PatchSetInfoNotAvailableException {
    ReviewDb db = null;
    Repository repo = null;
    try {
      db = schemaFactory.open();
      final PatchSet patchSet = db.patchSets().get(patchSetId);
      final Change change = db.changes().get(patchSet.getId().getParentKey());
      final String projectName = projectCache.get(change.getProject()).getProject().getName();
      repo = repoManager.openRepository(projectName);
      final RevWalk rw = new RevWalk(repo);
      try {
        final RevCommit src =
            rw.parseCommit(ObjectId.fromString(patchSet.getRevision().get()));
        return get(src, patchSetId);
      } finally {
        rw.release();
      }
    } catch (OrmException e) {
      throw new PatchSetInfoNotAvailableException(e);
    } catch (IOException e) {
      throw new PatchSetInfoNotAvailableException(e);
    } finally {
      if (db != null) {
        db.close();
      }
      if (repo != null) {
        repoManager.closeRepository(repo);
      }
    }
  }

  private UserIdentity toUserIdentity(final PersonIdent who) {
    final UserIdentity u = new UserIdentity();
    u.setName(who.getName());
    u.setEmail(who.getEmailAddress());
    u.setDate(new Timestamp(who.getWhen().getTime()));
    u.setTimeZone(who.getTimeZoneOffset());

    // If only one account has access to this email address, select it
    // as the identity of the user.
    //
    final Set<Account.Id> a = byEmailCache.get(u.getEmail());
    if (a.size() == 1) {
      u.setAccount(a.iterator().next());
    }

    return u;
  }

}
