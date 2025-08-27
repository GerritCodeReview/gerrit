// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser.ImpersonationPermissionMode;
import com.google.gerrit.server.permissions.DefaultPermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

public class QueryChangesFilterPermissionBackendIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  @Singleton
  public static class TestPermissionBackend extends PermissionBackend {
    private final DefaultPermissionBackend defaultPermissionBackend;
    private final AtomicReference<String> extraQueryFilter;

    public static class Module extends AbstractModule {
      @Override
      protected void configure() {
        bind(PermissionBackend.class).to(TestPermissionBackend.class).in(Scopes.SINGLETON);
      }
    }

    @Inject
    TestPermissionBackend(DefaultPermissionBackend defaultPermissionBackend) {
      this.defaultPermissionBackend = defaultPermissionBackend;
      this.extraQueryFilter = new AtomicReference<>();
    }

    @Override
    public WithUser currentUser() {
      return new TestPermissionWithUser(defaultPermissionBackend.currentUser());
    }

    @Override
    public WithUser user(CurrentUser user) {
      return new TestPermissionWithUser(defaultPermissionBackend.user(user));
    }

    @Override
    public WithUser user(CurrentUser user, ImpersonationPermissionMode permissionMode) {
      return new TestPermissionWithUser(defaultPermissionBackend.user(user, permissionMode));
    }

    @Override
    public WithUser exactUser(CurrentUser user) {
      return new TestPermissionWithUser(defaultPermissionBackend.exactUser(user));
    }

    @Override
    public WithUser absentUser(Account.Id id) {
      return new TestPermissionWithUser(defaultPermissionBackend.absentUser(id));
    }

    public String getExtraQueryFilter() {
      return extraQueryFilter.get();
    }

    public void setExtraQueryFilter(String extraQueryFilter) {
      this.extraQueryFilter.set(extraQueryFilter);
    }

    class TestPermissionWithUser extends WithUser {

      private final WithUser defaultPermissioBackendWithUser;

      TestPermissionWithUser(WithUser defaultPermissioBackendWithUser) {
        this.defaultPermissioBackendWithUser = defaultPermissioBackendWithUser;
      }

      @Override
      public ForProject project(Project.NameKey project) {
        return defaultPermissioBackendWithUser.project(project);
      }

      @Override
      public void check(GlobalOrPluginPermission perm)
          throws AuthException, PermissionBackendException {
        defaultPermissioBackendWithUser.check(perm);
      }

      @Override
      public <T extends GlobalOrPluginPermission> Set<T> test(Collection<T> permSet)
          throws PermissionBackendException {
        return defaultPermissioBackendWithUser.test(permSet);
      }

      @Override
      public BooleanCondition testCond(GlobalOrPluginPermission perm) {
        return defaultPermissioBackendWithUser.testCond(perm);
      }

      @Override
      public String filterQueryChanges() {
        return extraQueryFilter.get();
      }
    }
  }

  @Override
  public Module createModule() {
    return new TestPermissionBackend.Module();
  }

  @Test
  public void filterHidenProjectByAuthenticationBackend() throws Exception {
    String projectChangeId = createChange().getChangeId();

    Project.NameKey hiddenProject = projectOperations.newProject().create();
    TestRepository<InMemoryRepository> hiddenRepo = cloneProject(hiddenProject, admin);
    createChange(hiddenRepo);

    String changeQuery = "author:self OR status:open";
    assertThat(gApi.changes().query(changeQuery).get()).hasSize(2);

    server
        .getTestInjector()
        .getInstance(TestPermissionBackend.class)
        .setExtraQueryFilter("-project:" + hiddenProject);
    List<ChangeInfo> projectChanges = gApi.changes().query(changeQuery).get();
    assertThat(projectChanges.stream().map(c -> c.changeId)).containsExactly(projectChangeId);
  }
}
