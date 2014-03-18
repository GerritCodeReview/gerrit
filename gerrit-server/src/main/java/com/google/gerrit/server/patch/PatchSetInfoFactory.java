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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.client.UserIdentity;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;


/**
 * Factory class creating PatchSetInfo from meta-data found in Git repository.
 */
@Singleton
public class PatchSetInfoFactory {
  private final GitRepositoryManager repoManager;
  private final AccountByEmailCache byEmailCache;

  @Inject
  public PatchSetInfoFactory(final GitRepositoryManager grm,
      final AccountByEmailCache byEmailCache) {
    this.repoManager = grm;
    this.byEmailCache = byEmailCache;
  }

  public PatchSetInfo get(RevCommit src, PatchSet.Id psi) {
    PatchSetInfo info = new PatchSetInfo(psi);
    info.setSubject(src.getShortMessage());
    info.setMessage(src.getFullMessage());
    info.setAuthor(toUserIdentity(src.getAuthorIdent()));
    info.setCommitter(toUserIdentity(src.getCommitterIdent()));
    info.setRevId(src.getName());
    return info;
  }

  public PatchSetInfo get(ReviewDb db, PatchSet.Id patchSetId)
      throws PatchSetInfoNotAvailableException {
    try {
      final PatchSet patchSet = db.patchSets().get(patchSetId);
      final Change change = db.changes().get(patchSet.getId().getParentKey());
      return get(change, patchSet);
    } catch (OrmException e) {
      throw new PatchSetInfoNotAvailableException(e);
    }
  }

  public PatchSetInfo get(Change change, PatchSet patchSet)
      throws PatchSetInfoNotAvailableException {
    Repository repo;
    try {
      repo = repoManager.openRepository(change.getProject());
    } catch (IOException e) {
      throw new PatchSetInfoNotAvailableException(e);
    }
    try {
      final RevWalk rw = new RevWalk(repo);
      try {
        final RevCommit src =
            rw.parseCommit(ObjectId.fromString(patchSet.getRevision().get()));
        PatchSetInfo info = get(src, patchSet.getId());
        info.setParents(toParentInfos(src.getParents(), rw));
        info.setOtherBranchCommit(otherBranchCommit(change, patchSet));
        return info;
      } finally {
        rw.release();
      }
    } catch (IOException e) {
      throw new PatchSetInfoNotAvailableException(e);
    } finally {
      repo.close();
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

  private List<PatchSetInfo.ParentInfo> toParentInfos(final RevCommit[] parents,
      final RevWalk walk) throws IOException, MissingObjectException {
    List<PatchSetInfo.ParentInfo> pInfos =
      new ArrayList<PatchSetInfo.ParentInfo>(parents.length);
    for (RevCommit parent : parents) {
      walk.parseBody(parent);
      RevId rev = new RevId(parent.getId().name());
      String msg = parent.getShortMessage();
      pInfos.add(new PatchSetInfo.ParentInfo(rev, msg));
    }
    return pInfos;
  }

  private String otherBranchCommit(Change change, PatchSet patchSet)
      throws RepositoryNotFoundException, IOException,
      PatchSetInfoNotAvailableException {
    Repository git = repoManager.openRepository(change.getProject());
    String targetBranch = change.getDest().get();
    String commitBranchFlag = "different branch commit";
    try {
      RefDatabase refDb = git.getRefDatabase();
      Ref targetRef = refDb.getRef(targetBranch);
      Collection<Ref> branches = refDb.getRefs(Constants.R_HEADS).values();
      RevWalk rw = new RevWalk(git);
      RevCommit pushedCommit =
          rw.parseCommit(ObjectId.fromString(patchSet.getRevision().get()));
      RevCommit target = rw.parseCommit(targetRef.getObjectId());
      try {
        rw.markStart(pushedCommit);
        for (Ref branch : branches) {
          if (branch.getObjectId() != targetRef.getObjectId()) {
            rw.markUninteresting(rw.parseCommit(branch.getObjectId()));
          }
        }
        for (RevCommit n; (n = rw.next()) != null;) {
          if (n.equals(target)) {
            // targetRef reached
            commitBranchFlag = "same branch commit";
            break;
          }
        }
      } finally {
        rw.release();
      }
    } finally {
      git.close();
    }
    return commitBranchFlag;
  }
}
