// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.AccountGroup;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Test;

public class GroupUUIDTest {
  @Test
  public void createdUuidsForSameInputShouldBeDifferent() {
    String groupName = "Users";
    PersonIdent personIdent = new PersonIdent("John", "john@example.com");
    AccountGroup.UUID uuid1 = GroupUUID.make(groupName, personIdent);
    AccountGroup.UUID uuid2 = GroupUUID.make(groupName, personIdent);
    assertThat(uuid2).isNotEqualTo(uuid1);
  }
}
