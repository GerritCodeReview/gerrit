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

package com.google.gerrit.acceptance.rest.change;

import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeStatus;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.ChangeJson;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;

public class CreateChangeIT extends AbstractDaemonTest {

  @Test
  public void createEmptyChange_MissingBranch() throws IOException {
    ChangeInfo ci = new ChangeInfo();
    ci.project = project.get();
    RestResponse r = adminSession.post("/changes/", ci);
    assertEquals(HttpStatus.SC_BAD_REQUEST, r.getStatusCode());
    r.getEntityContent().contains("branch must be non-empty");
  }

  @Test
  public void createEmptyChange_MissingMessage() throws IOException {
    ChangeInfo ci = new ChangeInfo();
    ci.project = project.get();
    ci.branch = "master";
    RestResponse r = adminSession.post("/changes/", ci);
    assertEquals(HttpStatus.SC_BAD_REQUEST, r.getStatusCode());
    r.getEntityContent().contains("commit message must be non-empty");
  }

  @Test
  public void createDraftChange() throws IOException, RestApiException {
    ChangeInfo in = new ChangeInfo();
    in.project = project.get();
    in.branch = "master";
    in.subject = "Empty change";
    in.topic = "support-gerrit-workflow-in-browser";
    in.status = ChangeStatus.DRAFT;

    RestResponse r = adminSession.post("/changes/", in);
    assertEquals(HttpStatus.SC_CREATED, r.getStatusCode());

    ChangeJson.ChangeInfo info = newGson().fromJson(r.getReader(),
        ChangeJson.ChangeInfo.class);
    ChangeInfo out = get(info.changeId);

    assertEquals(in.branch, out.branch);
    assertEquals(in.subject, out.subject);
    assertEquals(in.topic, out.topic);
    assertEquals(in.status, out.status);
  }

  @Test
  public void createNewChange() throws IOException, RestApiException {
    ChangeInfo in = new ChangeInfo();
    in.project = project.get();
    in.branch = "master";
    in.subject = "Empty change";
    in.topic = "support-gerrit-workflow-in-browser";
    in.status = ChangeStatus.NEW;

    RestResponse r = adminSession.post("/changes/", in);
    assertEquals(HttpStatus.SC_CREATED, r.getStatusCode());

    ChangeJson.ChangeInfo info = newGson().fromJson(r.getReader(),
        ChangeJson.ChangeInfo.class);
    ChangeInfo out = get(info.changeId);

    assertEquals(in.branch, out.branch);
    assertEquals(in.subject, out.subject);
    assertEquals(in.topic, out.topic);
    assertEquals(in.status, out.status);
  }
}
