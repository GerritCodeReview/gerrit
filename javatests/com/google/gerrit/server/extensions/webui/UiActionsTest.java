// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.extensions.webui;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.ForProject;
import com.google.gerrit.server.permissions.PermissionBackend.ForRef;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendCondition;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.easymock.EasyMock;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class UiActionsTest {

  private static class FakeForProject extends ForProject {
    private boolean allowValueQueries = true;

    @Override
    public CurrentUser user() {
      return new CurrentUser() {
        @Override
        public GroupMembership getEffectiveGroups() {
          throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Object getCacheKey() {
          return new Object();
        }

        @Override
        public boolean isIdentifiedUser() {
          return true;
        }

        @Override
        public Account.Id getAccountId() {
          return new Account.Id(1);
        }
      };
    }

    @Override
    public String resourcePath() {
      return "/projects/test-project";
    }

    @Override
    public ForProject user(CurrentUser user) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public ForRef ref(String ref) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void check(ProjectPermission perm) throws AuthException, PermissionBackendException {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Set<ProjectPermission> test(Collection<ProjectPermission> permSet)
        throws PermissionBackendException {
      assertThat(allowValueQueries).isTrue();
      return ImmutableSet.of(ProjectPermission.READ);
    }

    @Override
    public Map<String, Ref> filter(Map<String, Ref> refs, Repository repo, RefFilterOptions opts)
        throws PermissionBackendException {
      throw new UnsupportedOperationException("not implemented");
    }

    private void disallowValueQueries() {
      allowValueQueries = false;
    }
  }

  @Test
  public void permissionBackendConditionEvaluationDeduplicatesAndBackfills() throws Exception {
    FakeForProject forProject = new FakeForProject();

    // Create three conditions, two of which are identical
    PermissionBackendCondition cond1 =
        (PermissionBackendCondition) forProject.testCond(ProjectPermission.CREATE_CHANGE);
    PermissionBackendCondition cond2 =
        (PermissionBackendCondition) forProject.testCond(ProjectPermission.READ);
    PermissionBackendCondition cond3 =
        (PermissionBackendCondition) forProject.testCond(ProjectPermission.CREATE_CHANGE);

    // Set up the Mock to expect a call of bulkEvaluateTest to only contain cond{1,2} since cond3
    // needs to be identified as duplicate and not called out explicitly.
    PermissionBackend permissionBackendMock = EasyMock.createMock(PermissionBackend.class);
    permissionBackendMock.bulkEvaluateTest(ImmutableSet.of(cond1, cond2));
    EasyMock.replay(permissionBackendMock);

    UiActions.evaluatePermissionBackendConditions(
        permissionBackendMock, ImmutableList.of(cond1, cond2, cond3));

    // Disallow queries for value to ensure that cond3 (previously left behind) is backfilled with
    // the value of cond1 and issues no additional call to PermissionBackend.
    forProject.disallowValueQueries();

    // Assert the values of all conditions
    assertThat(cond1.value()).isFalse();
    assertThat(cond2.value()).isTrue();
    assertThat(cond3.value()).isFalse();
  }
}
