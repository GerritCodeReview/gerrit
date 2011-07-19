// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.rules;

import static com.google.gerrit.rules.StoredValue.create;

import com.google.gerrit.reviewdb.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.UserIdentity;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;

import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.SystemException;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

public final class StoredValues {
  public static final StoredValue<ReviewDb> REVIEW_DB = create(ReviewDb.class);
  public static final StoredValue<Change> CHANGE = create(Change.class);
  public static final StoredValue<PatchSet.Id> PATCH_SET_ID = create(PatchSet.Id.class);
  public static final StoredValue<ChangeControl> CHANGE_CONTROL = create(ChangeControl.class);
  public static final StoredValue<RevCommit> REV_COMMIT = create(RevCommit.class);
  public static final StoredValue<String> TOPIC = new StoredValue<String>();

  public static final StoredValue<CurrentUser> CURRENT_USER = new StoredValue<CurrentUser>() {
    @Override
    public CurrentUser createValue(Prolog engine) {
      ChangeControl cControl = StoredValues.CHANGE_CONTROL.get(engine);
      return cControl.getCurrentUser();
    }
  };

  public static final StoredValue<UserIdentity> AUTHOR_IDENT = new StoredValue<UserIdentity>() {
    @Override
    public UserIdentity createValue(Prolog engine) {
      PatchSetInfo psInfo = StoredValues.PATCH_SET_INFO.get(engine);
      return psInfo.getAuthor();
    }
  };

  public static final StoredValue<UserIdentity> COMMITTER_IDENT = new StoredValue<UserIdentity>() {
    @Override
    public UserIdentity createValue(Prolog engine) {
      PatchSetInfo psInfo = StoredValues.PATCH_SET_INFO.get(engine);
      return psInfo.getCommitter();
    }
  };

  public static final StoredValue<Project.NameKey> PROJECT_NAMEKEY = new StoredValue<Project.NameKey>() {
    @Override
    public Project.NameKey createValue(Prolog engine) {
      Change change = StoredValues.CHANGE.get(engine);
      return change.getProject();
    }
  };

  public static final StoredValue<Branch.NameKey> BRANCH_NAMEKEY = new StoredValue<Branch.NameKey>() {
    @Override
    public Branch.NameKey createValue(Prolog engine) {
      Change change = StoredValues.CHANGE.get(engine);
      return change.getDest();
    }
  };

  public static final StoredValue<PatchSetInfo> PATCH_SET_INFO = new StoredValue<PatchSetInfo>() {
    @Override
    public PatchSetInfo createValue(Prolog engine) {
      PatchSet.Id patchSetId = StoredValues.PATCH_SET_ID.get(engine);
      PrologEnvironment env = (PrologEnvironment) engine.control;
      PatchSetInfoFactory patchInfoFactory =
          env.getInjector().getInstance(PatchSetInfoFactory.class);
      try {
        return patchInfoFactory.get(patchSetId);
      } catch (PatchSetInfoNotAvailableException e) {
        throw new SystemException(e.getMessage());
      }
    }
  };

  public static final StoredValue<PatchList> PATCH_LIST = new StoredValue<PatchList>() {
    @Override
    public PatchList createValue(Prolog engine) {
      PrologEnvironment env = (PrologEnvironment) engine.control;
      Project.NameKey projectKey;
      ObjectId b;
      PatchSetInfo psInfo = StoredValues.PATCH_SET_INFO.getOrNull(engine);
      if (psInfo == null) {
        RevCommit commit = StoredValues.REV_COMMIT.get(engine);
        projectKey = StoredValues.PROJECT_NAMEKEY.get(engine);
        b = commit.getId();
      } else {
        Change change = StoredValues.CHANGE.get(engine);
        projectKey = change.getProject();
        b = ObjectId.fromString(psInfo.getRevId());
      }
      PatchListCache plCache = env.getInjector().getInstance(PatchListCache.class);
      Whitespace ws = Whitespace.IGNORE_NONE;
      PatchListKey plKey = new PatchListKey(projectKey, null, b, ws);
      PatchList patchList = plCache.get(plKey);
      if (patchList == null) {
        throw new SystemException("Cannot create " + plKey);
      }
      return patchList;
    }
  };

  public static final StoredValue<Repository> REPOSITORY = new StoredValue<Repository>() {
    @Override
    public Repository createValue(Prolog engine) {
      PrologEnvironment env = (PrologEnvironment) engine.control;
      GitRepositoryManager gitMgr =
        env.getInjector().getInstance(GitRepositoryManager.class);
      Change change = StoredValues.CHANGE.get(engine);
      Project.NameKey projectKey = change.getProject();
      final Repository repo;
      try {
        repo = gitMgr.openRepository(projectKey);
      } catch (RepositoryNotFoundException e) {
        throw new SystemException(e.getMessage());
      }
      env.addToCleanup(new Runnable() {
        @Override
        public void run() {
          repo.close();
        }
      });
      return repo;
    }
  };

  private StoredValues() {
  }
}