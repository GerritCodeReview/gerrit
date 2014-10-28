// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.gerrit.acceptance.rest.group.GroupAssert.assertGroupInfo;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.GroupJson.GroupInfo;

import org.junit.Test;

import java.io.IOException;

public class GetGroupIT extends AbstractDaemonTest {
  @Test
  public void testGetGroup() throws Exception {
    AccountGroup adminGroup = groupCache.get(new AccountGroup.NameKey("Administrators"));

    // by UUID
    testGetGroup("/groups/" + adminGroup.getGroupUUID().get(), adminGroup);

    // by name
    testGetGroup("/groups/" + adminGroup.getName(), adminGroup);

    // by legacy numeric ID
    testGetGroup("/groups/" + adminGroup.getId().get(), adminGroup);
  }

  private void testGetGroup(String url, AccountGroup expectedGroup)
      throws IOException {
    RestResponse r = adminSession.get(url);
    GroupInfo group = newGson().fromJson(r.getReader(), GroupInfo.class);
    assertGroupInfo(expectedGroup, group);
  }
}
