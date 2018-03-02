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

package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.UniversalGroupBackend;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class TestGroupBackendTest extends AbstractDaemonTest {
  @Inject private TestGroupBackend backend;
  @Inject private UniversalGroupBackend universalBackend;

  private static final String testGroup = "testgroup:" + ObjectId.zeroId().name();
  private static final AccountGroup.UUID testGroupUuid = new AccountGroup.UUID(testGroup);

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        DynamicSet.bind(binder(), GroupBackend.class).to(TestGroupBackend.class);
      }
    };
  }

  @Test
  public void handlesTestGroup() throws Exception {
    assertThat(backend.handles(testGroupUuid)).isTrue();
  }

  @Test
  public void universalBackendHandlesTestGroup() throws Exception {
    assertThat(universalBackend.handles(testGroupUuid)).isTrue();
  }

  @Test
  public void doesNotHandleLDAP() throws Exception {
    assertThat(backend.handles(new AccountGroup.UUID("ldap:1234"))).isFalse();
  }

  @Test
  public void doesNotHandleNull() throws Exception {
    assertThat(backend.handles(null)).isFalse();
  }

  @Test
  public void doesNotHandleInvalidTestGroupUUID() throws Exception {
    assertThat(backend.handles(new AccountGroup.UUID("testgroup:1234"))).isFalse();
  }

  @Test
  public void returnsNullWhenNoGroupsExist() throws Exception {
    assertThat(backend.get(testGroupUuid)).isNull();
  }
}
