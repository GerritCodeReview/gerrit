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
import com.google.gerrit.server.restapi.config.ListTasks.TaskInfo;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import org.junit.Test;

public class ListTasksIT extends AbstractDaemonTest {

  @Test
  public void listTasks() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/tasks/");
    r.assertOK();
    List<TaskInfo> result =
        newGson().fromJson(r.getReader(), new TypeToken<List<TaskInfo>>() {}.getType());
    assertThat(result).isNotEmpty();
    boolean foundLogFileCompressorTask = false;
    for (TaskInfo info : result) {
      if ("Log File Manager".equals(info.command)) {
        foundLogFileCompressorTask = true;
      }
      assertThat(info.id).isNotNull();
      Long.parseLong(info.id, 16);
      assertThat(info.command).isNotNull();
      assertThat(info.startTime).isNotNull();
    }
    assertThat(foundLogFileCompressorTask).isTrue();
  }

  @Test
  public void listTasksWithoutViewQueueCapability() throws Exception {
    RestResponse r = userRestSession.get("/config/server/tasks/");
    r.assertOK();
    List<TaskInfo> result =
        newGson().fromJson(r.getReader(), new TypeToken<List<TaskInfo>>() {}.getType());

    assertThat(result).isEmpty();
  }
}
