// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.server.config.ListTasks.TaskInfo;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import org.junit.Test;

public class KillTaskIT extends AbstractDaemonTest {

  private void killTask() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/tasks/");
    List<TaskInfo> result =
        newGson().fromJson(r.getReader(), new TypeToken<List<TaskInfo>>() {}.getType());
    r.consume();
    int taskCount = result.size();
    assertThat(taskCount).isGreaterThan(0);

    r = adminRestSession.delete("/config/server/tasks/" + result.get(0).id);
    r.assertNoContent();
    r.consume();

    r = adminRestSession.get("/config/server/tasks/");
    result = newGson().fromJson(r.getReader(), new TypeToken<List<TaskInfo>>() {}.getType());
    r.consume();
    assertThat(result).hasSize(taskCount - 1);
  }

  private void killTask_NotFound() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/tasks/");
    List<TaskInfo> result =
        newGson().fromJson(r.getReader(), new TypeToken<List<TaskInfo>>() {}.getType());
    r.consume();
    assertThat(result.size()).isGreaterThan(0);

    userRestSession.delete("/config/server/tasks/" + result.get(0).id).assertNotFound();
  }

  @Test
  public void killTaskTests_inOrder() throws Exception {
    // As killTask() changes the state of the server, we want to test it last
    killTask_NotFound();
    killTask();
  }
}
