// Copyright (C) 2023 The Android Open Source Project
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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.groups.ProjectRefInfo;
import com.google.gerrit.server.restapi.group.CreateNamedDestinationChange;
import java.util.Collections;
import org.junit.Test;

public class GroupNamedDestinationsIT extends AbstractDaemonTest {
  @Test
  public void cannotCreateChangeIfUserIsNotPartOfGroup() throws Exception {
    adminRestSession.put("/groups/MyGroup").assertCreated();

    CreateNamedDestinationChange.Input input = new CreateNamedDestinationChange.Input();
    input.projectsAndRefs =
        Collections.singletonList(new ProjectRefInfo("foo", "refs/heads/master"));
    RestResponse response =
        userRestSession.put("/groups/MyGroup/named_destinations/dest1/review", input);
    response.assertNotFound();
  }

  @Test
  public void canCreateChangeIfUserIsPartOfGroup() throws Exception {
    adminRestSession.put("/groups/MyGroup").assertCreated();
    adminRestSession.put("/groups/MyGroup/members/" + user.username()).assertCreated();

    CreateNamedDestinationChange.Input input = new CreateNamedDestinationChange.Input();
    input.projectsAndRefs =
        Collections.singletonList(new ProjectRefInfo("foo", "refs/heads/master"));
    RestResponse response =
        userRestSession.put("/groups/MyGroup/named_destinations/dest1/review", input);
    response.assertCreated();
  }
}
