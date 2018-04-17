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

package com.google.gerrit.server.rules;

import static com.google.gerrit.server.rules.StoredValue.create;

import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectAccessor;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.googlecode.prolog_cafe.exceptions.SystemException;
import com.googlecode.prolog_cafe.lang.Prolog;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

public final class StoredValues {
  public static final StoredValue<Accounts> ACCOUNTS = create(Accounts.class);
  public static final StoredValue<AccountCache> ACCOUNT_CACHE = create(AccountCache.class);
  public static final StoredValue<Emails> EMAILS = create(Emails.class);
  public static final StoredValue<ReviewDb> REVIEW_DB = create(ReviewDb.class);
  public static final StoredValue<ChangeData> CHANGE_DATA = create(ChangeData.class);
  public static final StoredValue<ProjectAccessor> PROJECT_ACCESSOR = create(ProjectAccessor.class);
  public static final StoredValue<ProjectState> PROJECT_STATE = create(ProjectState.class);

  public static Change getChange(Prolog engine) throws SystemException {
    ChangeData cd = CHANGE_DATA.get(engine);
    try {
      return cd.change();
    } catch (OrmException e) {
      throw new SystemException("Cannot load change " + cd.getId());
    }
  }

  public static PatchSet getPatchSet(Prolog engine) throws SystemException {
    ChangeData cd = CHANGE_DATA.get(engine);
    try {
      return cd.currentPatchSet();
    } catch (OrmException e) {
      throw new SystemException(e.getMessage());
    }
  }

  public static final StoredValue<PatchSetInfo> PATCH_SET_INFO =
      new StoredValue<PatchSetInfo>() {
        @Override
        public PatchSetInfo createValue(Prolog engine) {
          Change change = getChange(engine);
          PatchSet ps = getPatchSet(engine);
          PrologEnvironment env = (PrologEnvironment) engine.control;
          PatchSetInfoFactory patchInfoFactory = env.getArgs().getPatchSetInfoFactory();
          try {
            return patchInfoFactory.get(change.getProject(), ps);
          } catch (PatchSetInfoNotAvailableException e) {
            throw new SystemException(e.getMessage());
          }
        }
      };

  public static final StoredValue<PatchList> PATCH_LIST =
      new StoredValue<PatchList>() {
        @Override
        public PatchList createValue(Prolog engine) {
          PrologEnvironment env = (PrologEnvironment) engine.control;
          PatchSet ps = getPatchSet(engine);
          PatchListCache plCache = env.getArgs().getPatchListCache();
          Change change = getChange(engine);
          Project.NameKey project = change.getProject();
          ObjectId b = ObjectId.fromString(ps.getRevision().get());
          Whitespace ws = Whitespace.IGNORE_NONE;
          PatchListKey plKey = PatchListKey.againstDefaultBase(b, ws);
          PatchList patchList;
          try {
            patchList = plCache.get(plKey, project);
          } catch (PatchListNotAvailableException e) {
            throw new SystemException("Cannot create " + plKey);
          }
          return patchList;
        }
      };

  public static final StoredValue<Repository> REPOSITORY =
      new StoredValue<Repository>() {
        @Override
        public Repository createValue(Prolog engine) {
          PrologEnvironment env = (PrologEnvironment) engine.control;
          GitRepositoryManager gitMgr = env.getArgs().getGitRepositoryManager();
          Change change = getChange(engine);
          Project.NameKey projectKey = change.getProject();
          Repository repo;
          try {
            repo = gitMgr.openRepository(projectKey);
          } catch (IOException e) {
            throw new SystemException(e.getMessage());
          }
          env.addToCleanup(repo::close);
          return repo;
        }
      };

  public static final StoredValue<PermissionBackend> PERMISSION_BACKEND =
      new StoredValue<PermissionBackend>() {
        @Override
        protected PermissionBackend createValue(Prolog engine) {
          PrologEnvironment env = (PrologEnvironment) engine.control;
          return env.getArgs().getPermissionBackend();
        }
      };

  public static final StoredValue<AnonymousUser> ANONYMOUS_USER =
      new StoredValue<AnonymousUser>() {
        @Override
        protected AnonymousUser createValue(Prolog engine) {
          PrologEnvironment env = (PrologEnvironment) engine.control;
          return env.getArgs().getAnonymousUser();
        }
      };

  public static final StoredValue<Map<Account.Id, IdentifiedUser>> USERS =
      new StoredValue<Map<Account.Id, IdentifiedUser>>() {
        @Override
        protected Map<Account.Id, IdentifiedUser> createValue(Prolog engine) {
          return new HashMap<>();
        }
      };

  private StoredValues() {}
}
