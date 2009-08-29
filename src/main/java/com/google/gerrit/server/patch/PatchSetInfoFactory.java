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

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.UserIdentity;
import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Set;


/**
 * Factory class creating PatchSetInfo from meta-data found in Git repository.
 */
@Singleton
public class PatchSetInfoFactory {
  private final GerritServer gs;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final AccountByEmailCache byEmailCache;

  @Inject
  public PatchSetInfoFactory(final GerritServer gs,
      final SchemaFactory<ReviewDb> schemaFactory,
      final AccountByEmailCache byEmailCache) {
    this.gs = gs;
    this.schemaFactory = schemaFactory;
    this.byEmailCache = byEmailCache;
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
      final Project.NameKey projectKey = change.getProject();
      final String projectName = projectKey.get();
      repo = gs.openRepository(projectName);
      final RevWalk rw = new RevWalk(repo);
      final RevCommit src =
          rw.parseCommit(ObjectId.fromString(patchSet.getRevision().get()));
      return get(src, patchSetId);
    } catch (OrmException e) {
      throw new PatchSetInfoNotAvailableException(e);
    } catch (IOException e) {
      throw new PatchSetInfoNotAvailableException(e);
    } finally {
      if (db != null) {
        db.close();
      }
      if (repo != null) {
        repo.close();
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
