// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.group;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.schema.SchemaCreatorImpl;
import com.google.gson.reflect.TypeToken;
import java.util.Map;
import org.junit.Test;

public class ListGroupsIT extends AbstractDaemonTest {
  @Test
  public void listAllGroups() throws Exception {
    RestResponse response = adminRestSession.get("/groups/");
    response.assertOK();

    Map<String, GroupInfo> groupMap =
        newGson()
            .fromJson(response.getReader(), new TypeToken<Map<String, GroupInfo>>() {}.getType());
    assertThat(groupMap.keySet())
        .containsExactly(
            "Administrators", SchemaCreatorImpl.BLOCKED_USERS, ServiceUserClassifier.SERVICE_USERS);
  }
}
