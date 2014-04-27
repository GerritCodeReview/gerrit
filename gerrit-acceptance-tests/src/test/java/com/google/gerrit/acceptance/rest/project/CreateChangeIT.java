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

package com.google.gerrit.acceptance.rest.project;

import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.projects.CreateChangeInput;
import com.google.gerrit.server.change.ChangeJson;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;

public class CreateChangeIT extends AbstractDaemonTest {

  @Test
  public void createEmptyChange_MissingDestination() throws IOException {
    RestResponse r = adminSession.put("/projects/" + project.get()
        + "/change");
    assertEquals(HttpStatus.SC_BAD_REQUEST, r.getStatusCode());
    r.getEntityContent().contains("destination must be non-empty");
  }

  @Test
  public void createEmptyChange_MissingMessage() throws IOException {
    CreateChangeInput in = new CreateChangeInput();
    in.destination = "master";
    RestResponse r = adminSession.put("/projects/" + project.get()
        + "/change", in);
    assertEquals(HttpStatus.SC_BAD_REQUEST, r.getStatusCode());
    r.getEntityContent().contains("commit message must be non-empty");
  }

  @Test
  public void createEmptyChange() throws IOException {
    CreateChangeInput in = new CreateChangeInput();
    in.destination = "master";
    in.message = "Empty change";
    RestResponse r = adminSession.put("/projects/" + project.get()
        + "/change", in);
    assertEquals(HttpStatus.SC_CREATED, r.getStatusCode());
    ChangeJson.ChangeInfo info = newGson().fromJson(r.getReader(),
        ChangeJson.ChangeInfo.class);
    assertEquals("master", info.branch);
    assertEquals("Empty change", info.subject);

  }
}
