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
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.common.TagInfo;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class ListTagsIT extends AbstractDaemonTest {
  @Test
  public void listTagsOfNonExistingProject_NotFound() throws IOException {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        GET("/projects/non-existing/tags").getStatusCode());
  }

  @Test
  public void listTagsOfNonVisibleProject_NotFound() throws Exception {
    blockRead(project, "refs/*");
    assertEquals(HttpStatus.SC_NOT_FOUND,
        userSession.get("/projects/" + project.get() + "/tags").getStatusCode());
  }

  @Test
  public void listTags() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    String tag1 = "v1.0";
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent());
    push1.setTag(tag1);
    PushOneCommit.Result r1 = push1.to(git, "refs/for/master%submit");
    r1.assertOkStatus();

    String tag2 = "v2.0";
    PushOneCommit push2 = pushFactory.create(db, admin.getIdent());
    push2.setTag(tag2);
    PushOneCommit.Result r2 = push2.to(git, "refs/for/master%submit");
    r2.assertOkStatus();

    RestResponse response = adminSession.get("/projects/" + project.get() + "/tags");
    List<TagInfo> result = toTagInfoList(response);
    assertEquals(result.size(), 2);
    assertEquals("refs/tags/" + tag1, result.get(0).getRef());
    assertEquals(r1.getCommitId().getName(), result.get(0).getRevision());
    assertEquals("refs/tags/" + tag2, result.get(1).getRef());
    assertEquals(r2.getCommitId().getName(), result.get(1).getRevision());
  }

  private static List<TagInfo> toTagInfoList(RestResponse r)
      throws IOException {
    List<TagInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<List<TagInfo>>() {}.getType());
    return result;
  }

  private RestResponse GET(String endpoint) throws IOException {
    return adminSession.get(endpoint);
  }
}
