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

package com.google.gerrit.server.patchsetinfo;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.UserIdentity;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.server.GerritServer;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

public class PatchSetInfoFactory {

  public static PatchSetInfo patchSetInfoFromRevCommit(RevCommit src, PatchSet
      patchSet) throws OrmException {
    PatchSetInfo info;
    info = new PatchSetInfo(patchSet.getId());

    info.setSubject(src.getShortMessage());
    info.setMessage(src.getFullMessage());
    info.setAuthor(toUserIdentity(src.getAuthorIdent()));
    info.setCommitter(toUserIdentity(src.getCommitterIdent()));
    
    return info;
  }
  
  public static PatchSetInfo patchSetInfoForPatchSet(PatchSet patchSet) throws
   PatchSetInfoNotAvailableException {
    GerritServer gs;
    try {
      gs = GerritServer.getInstance(false);
    } catch (OrmException e) {
      throw new PatchSetInfoNotAvailableException(e);
    } catch (XsrfException e) {
      throw new PatchSetInfoNotAvailableException(e);
    }
    ReviewDb db;
    try {
      db = Common.getSchemaFactory().open();
    } catch (OrmException e) {
      throw new PatchSetInfoNotAvailableException(e);
    }
    try {
      final Change change = db.changes().get(patchSet.getId().getParentKey());
      final Project.NameKey projectKey = change.getDest().getParentKey();
      final String projectName = projectKey.get();
      final Repository repo = gs.openRepository(projectName);
      final RevWalk rw = new RevWalk(repo);
      final RevCommit src = 
        rw.parseCommit(ObjectId.fromString(patchSet.getRevision().get()));
      return patchSetInfoFromRevCommit(src, patchSet);
    } catch (OrmException e) {
      throw new PatchSetInfoNotAvailableException(e); 
    } catch (IOException e) {
      throw new PatchSetInfoNotAvailableException(e);
    } finally {
      db.close();
    }
  }
  
  public static PatchSetInfo patchSetInfoForPatchSetId(PatchSet.Id id) 
    throws PatchSetInfoNotAvailableException {
    ReviewDb db;
    try {
      db = Common.getSchemaFactory().open();
      return patchSetInfoForPatchSet(db.patchSets().get(id));
    } catch (OrmException e) {
      throw new PatchSetInfoNotAvailableException(e);
    }
  }
  
  private static UserIdentity toUserIdentity(final PersonIdent who)
  throws OrmException {
    
    final UserIdentity u = new UserIdentity();
    u.setName(who.getName());
    u.setEmail(who.getEmailAddress());
    u.setDate(new Timestamp(who.getWhen().getTime()));
    u.setTimeZone(who.getTimeZoneOffset());

    if (u.getEmail() != null) {
      // If only one account has access to this email address, select it
      // as the identity of the user.
      //
      final Set<Account.Id> a = new HashSet<Account.Id>();
      final ReviewDb db = Common.getSchemaFactory().open();
      try {
        for (final AccountExternalId e : db.accountExternalIds().byEmailAddress(
            u.getEmail())) {
          a.add(e.getAccountId());
        }
      } finally {
        db.close();
      }
      if (a.size() == 1) {
        u.setAccount(a.iterator().next());
      }
    }

    return u;
  }
  
}
