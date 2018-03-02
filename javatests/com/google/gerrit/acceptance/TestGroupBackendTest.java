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

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.UniversalGroupBackend;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class TestGroupBackendTest extends AbstractDaemonTest {
  @Inject private UniversalGroupBackend universalBackend;

  @Test
  public void handlesTestGroup() throws Exception {
    AccountGroup.UUID uuid = TestGroupBackend.createUuid(ObjectId.zeroId().name());
    assertThat(testGroupBackend.handles(uuid)).isTrue();
  }

  @Test
  public void universalBackendHandlesTestGroup() throws Exception {
    AccountGroup.UUID uuid = TestGroupBackend.createUuid(ObjectId.zeroId().name());
    assertThat(universalBackend.handles(uuid)).isTrue();
  }

  @Test
  public void doesNotHandleLDAP() throws Exception {
    assertThat(testGroupBackend.handles(new AccountGroup.UUID("ldap:1234"))).isFalse();
  }

  @Test
  public void doesNotHandleNull() throws Exception {
    assertThat(testGroupBackend.handles(null)).isFalse();
  }

  @Test
  public void doesNotHandleInvalidTestGroupUUID() throws Exception {
    assertThat(testGroupBackend.handles(TestGroupBackend.createUuid("1234"))).isFalse();
  }

  @Test
  public void returnsNullWhenGroupDoesNotExist() throws Exception {
    AccountGroup.UUID uuid = TestGroupBackend.createUuid(ObjectId.zeroId().name());
    assertThat(testGroupBackend.get(uuid)).isNull();
  }

  @Test
  public void returnsNullForNullGroup() throws Exception {
    assertThat(testGroupBackend.get(null)).isNull();
  }

  @Test
  public void returnsKnownGroup() throws Exception {
    AccountGroup.UUID uuid = testGroupBackend.add(ObjectId.zeroId().name());
    assertThat(testGroupBackend.get(uuid)).isNotNull();
  }
}
