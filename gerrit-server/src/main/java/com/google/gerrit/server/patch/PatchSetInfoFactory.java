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

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.UserIdentity;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.util.FutureUtil;
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
import java.util.concurrent.Future;


/**
 * Factory class creating PatchSetInfo from meta-data found in Git repository.
 */
@Singleton
public class PatchSetInfoFactory {
  private final GitRepositoryManager repoManager;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final AccountCache accountCache;

  @Inject
  public PatchSetInfoFactory(final GitRepositoryManager grm,
      final SchemaFactory<ReviewDb> schemaFactory,
      final AccountCache accountCache) {
    this.repoManager = grm;
    this.schemaFactory = schemaFactory;
    this.accountCache = accountCache;
  }

  public PatchSetInfo get(RevCommit src, PatchSet.Id psi) {
    Future<UserIdentity> author = toUserIdentity(src.getAuthorIdent());
    Future<UserIdentity> committer = toUserIdentity(src.getCommitterIdent());

    PatchSetInfo info = new PatchSetInfo(psi);
    info.setSubject(src.getShortMessage());
    info.setMessage(src.getFullMessage());
    info.setAuthor(FutureUtil.get(author));
    info.setCommitter(FutureUtil.get(committer));
    return info;
  }

  public PatchSetInfo get(PatchSet.Id patchSetId)
      throws PatchSetInfoNotAvailableException {
    final RevCommit src;

    try {
      final ReviewDb db = schemaFactory.open();
      try {
        final PatchSet patchSet = db.patchSets().get(patchSetId);
        final String revId = patchSet.getRevision().get();
        final Change change = db.changes().get(patchSet.getId().getParentKey());
        final Project.NameKey projectKey = change.getProject();
        try {
          final Repository repo = repoManager.openRepository(projectKey.get());
          try {
            final RevWalk rw = new RevWalk(repo);
            src = rw.parseCommit(ObjectId.fromString(revId));
          } finally {
            repo.close();
          }
        } catch (IOException e) {
          throw new PatchSetInfoNotAvailableException(e);
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new PatchSetInfoNotAvailableException(e);
    }

    return get(src, patchSetId);
  }

  private ListenableFuture<UserIdentity> toUserIdentity(final PersonIdent who) {
    return Futures.compose(accountCache.byEmail(who.getEmailAddress()),
        new Function<Set<Account.Id>, UserIdentity>() {
          @Override
          public UserIdentity apply(Set<Account.Id> from) {
            final UserIdentity u = new UserIdentity();
            u.setName(who.getName());
            u.setEmail(who.getEmailAddress());
            u.setDate(new Timestamp(who.getWhen().getTime()));
            u.setTimeZone(who.getTimeZoneOffset());

            if (from != null && from.size() == 1) {
              // If only one account has access to this email address, select it
              // as the identity of the user.
              //
              u.setAccount(from.iterator().next());
            }
            return u;
          }
        });
  }
}
