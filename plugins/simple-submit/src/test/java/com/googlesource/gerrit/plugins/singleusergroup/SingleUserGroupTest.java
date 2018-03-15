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

package com.googlesource.gerrit.plugins.singleusergroup;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.extensions.common.GroupInfo;
import java.util.Map;
import org.junit.Test;

@NoHttpd
@TestPlugin(
  name = "singleusergroup",
  sysModule = "com.googlesource.gerrit.plugins.singleusergroup.SingleUserGroup$Module"
)
public class SingleUserGroupTest extends LightweightPluginDaemonTest {
  @Test
  public void testSuggestion() throws Exception {
    // No ability to modify account and therefore no ACL to see secondary email
    setApiUser(user);
    Map<String, GroupInfo> groups = gApi.groups().list().withSuggest("adm").getAsMap();
    assertThat(groups).containsKey("user/Administrator (admin)");
  }
}
