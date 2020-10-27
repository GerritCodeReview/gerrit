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

package com.google.gerrit.acceptance.pgm;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.schema.NoteDbSchemaVersion;
import com.google.gerrit.server.schema.Schema_184;
import com.google.gerrit.testing.TestUpdateUI;
import com.google.inject.Inject;
import org.junit.Test;

public class Schema_184IT extends AbstractDaemonTest {
  private static final AccountGroup.NameKey SERVICE_USERS =
      AccountGroup.nameKey(ServiceUserClassifier.SERVICE_USERS);
  private static final AccountGroup.NameKey NON_INTERACTIVE_USERS =
      AccountGroup.nameKey("Non-Interactive Users");

  @Inject private GroupOperations groupOperations;
  @Inject private NoteDbSchemaVersion.Arguments args;

  @Test
  public void groupGetsRenamed() throws Exception {
    groupOperations
        .group(groupCache.get(SERVICE_USERS).get().getGroupUUID())
        .forUpdate()
        .name(NON_INTERACTIVE_USERS.get())
        .update();
    assertThat(hasGroup(NON_INTERACTIVE_USERS)).isTrue();

    Schema_184 upgrade = new Schema_184();
    upgrade.upgrade(args, new TestUpdateUI());
    assertThat(hasGroup(SERVICE_USERS)).isTrue();
    assertThat(hasGroup(NON_INTERACTIVE_USERS)).isFalse();
  }

  @Test
  public void upgradeIsIdempotent() throws Exception {
    groupOperations
        .group(groupCache.get(SERVICE_USERS).get().getGroupUUID())
        .forUpdate()
        .name(NON_INTERACTIVE_USERS.get())
        .update();
    Schema_184 upgrade = new Schema_184();
    upgrade.upgrade(args, new TestUpdateUI());
    upgrade.upgrade(args, new TestUpdateUI());
    assertThat(hasGroup(SERVICE_USERS)).isTrue();
    assertThat(hasGroup(NON_INTERACTIVE_USERS)).isFalse();
  }

  private boolean hasGroup(AccountGroup.NameKey key) {
    // We have to evict here because the schema migration doesn't have the cache available.
    // That's OK because that also means it won't cache an old state in production.
    groupCache.evict(key);
    return groupCache.get(key).isPresent();
  }
}
