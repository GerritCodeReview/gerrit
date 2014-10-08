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

import static com.google.gerrit.acceptance.Spec.Operation.REST;
import static com.google.gerrit.acceptance.Spec.Operation.USER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.Spec;
import com.google.gerrit.server.config.ListTasks.TaskInfo;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class GetTaskIT extends AbstractDaemonTest {

  @Test
  @Spec({REST, USER})
  public void getTask() throws IOException {
    RestResponse r =
        adminSession.get("/config/server/tasks/" + getLogFileCompressorTaskId());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    TaskInfo info =
        newGson().fromJson(r.getReader(),
            new TypeToken<TaskInfo>() {}.getType());
    assertNotNull(info.id);
    Long.parseLong(info.id, 16);
    assertEquals("Log File Compressor", info.command);
    assertNotNull(info.startTime);
  }

  @Test
  @Spec({REST, USER})
  public void getTask_NotFound() throws IOException {
    RestResponse r =
        userSession.get("/config/server/tasks/" + getLogFileCompressorTaskId());
    assertEquals(HttpStatus.SC_NOT_FOUND, r.getStatusCode());
  }

  private String getLogFileCompressorTaskId() throws IOException {
    RestResponse r = adminSession.get("/config/server/tasks/");
    List<TaskInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<List<TaskInfo>>() {}.getType());
    r.consume();
    for (TaskInfo info : result) {
      if ("Log File Compressor".equals(info.command)) {
        return info.id;
      }
    }
    return null;
  }
}
