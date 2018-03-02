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
import com.google.inject.Inject;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class TestGroupBackendTest extends AbstractDaemonTest {
  @Inject TestGroupBackend backend;

  private static final String testGroupUuid = "testgroup:" + ObjectId.zeroId().name();

  @Test
  public void handles() throws Exception {
    assertThat(backend.handles(new AccountGroup.UUID("ldap:1234"))).isFalse();
    assertThat(backend.handles(null)).isFalse();
    assertThat(backend.handles(new AccountGroup.UUID("testgroup:1234"))).isFalse();
    assertThat(backend.handles(new AccountGroup.UUID(testGroupUuid))).isTrue();
  }

  @Test
  public void get() throws Exception {
    assertThat(backend.get(new AccountGroup.UUID(testGroupUuid))).isNull();
  }
}
