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

import java.util.List;

public class TagsIT extends AbstractDaemonTest {
  @Test
  public void listTagsOfNonExistingProject_NotFound() throws Exception {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        adminSession.get("/projects/non-existing/tags").getStatusCode());
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

    PushOneCommit.Tag tag1 = new PushOneCommit.Tag("v1.0");
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent());
    push1.setTag(tag1);
    PushOneCommit.Result r1 = push1.to(git, "refs/for/master%submit");
    r1.assertOkStatus();

    PushOneCommit.AnnotatedTag tag2 =
        new PushOneCommit.AnnotatedTag("v2.0", "annotation", admin.getIdent());
    PushOneCommit push2 = pushFactory.create(db, admin.getIdent());
    push2.setTag(tag2);
    PushOneCommit.Result r2 = push2.to(git, "refs/for/master%submit");
    r2.assertOkStatus();

    List<TagInfo> result =
        toTagInfoList(adminSession.get("/projects/" + project.get() + "/tags"));
    assertEquals(2, result.size());

    TagInfo t = result.get(0);
    assertEquals("refs/tags/" + tag1.name, t.ref);
    assertEquals(r1.getCommitId().getName(), t.revision);

    t = result.get(1);
    assertEquals("refs/tags/" + tag2.name, t.ref);
    assertEquals(r2.getCommitId().getName(), t.object);
    assertEquals(tag2.message, t.message);
    assertEquals(tag2.tagger.getName(), t.tagger.name);
    assertEquals(tag2.tagger.getEmailAddress(), t.tagger.email);
  }

  @Test
  public void listTagsOfNonVisibleBranch() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/hidden");
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    PushOneCommit.Tag tag1 = new PushOneCommit.Tag("v1.0");
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent());
    push1.setTag(tag1);
    PushOneCommit.Result r1 = push1.to(git, "refs/for/master%submit");
    r1.assertOkStatus();

    pushTo("refs/heads/hidden");
    PushOneCommit.Tag tag2 = new PushOneCommit.Tag("v2.0");
    PushOneCommit push2 = pushFactory.create(db, admin.getIdent());
    push2.setTag(tag2);
    PushOneCommit.Result r2 = push2.to(git, "refs/for/hidden%submit");
    r2.assertOkStatus();

    List<TagInfo> result =
        toTagInfoList(adminSession.get("/projects/" + project.get() + "/tags"));
    assertEquals(2, result.size());
    assertEquals("refs/tags/" + tag1.name, result.get(0).ref);
    assertEquals(r1.getCommitId().getName(), result.get(0).revision);
    assertEquals("refs/tags/" + tag2.name, result.get(1).ref);
    assertEquals(r2.getCommitId().getName(), result.get(1).revision);

    blockRead(project, "refs/heads/hidden");
    result =
        toTagInfoList(adminSession.get("/projects/" + project.get() + "/tags"));
    assertEquals(1, result.size());
    assertEquals("refs/tags/" + tag1.name, result.get(0).ref);
    assertEquals(r1.getCommitId().getName(), result.get(0).revision);
  }

  @Test
  public void getTag() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    PushOneCommit.Tag tag1 = new PushOneCommit.Tag("v1.0");
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent());
    push1.setTag(tag1);
    PushOneCommit.Result r1 = push1.to(git, "refs/for/master%submit");
    r1.assertOkStatus();

    RestResponse response =
        adminSession.get("/projects/" + project.get() + "/tags/" + tag1.name);
    TagInfo tagInfo =
        newGson().fromJson(response.getReader(), TagInfo.class);
    assertEquals("refs/tags/" + tag1.name, tagInfo.ref);
    assertEquals(r1.getCommitId().getName(), tagInfo.revision);
  }

  private static List<TagInfo> toTagInfoList(RestResponse r) throws Exception {
    List<TagInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<List<TagInfo>>() {}.getType());
    return result;
  }
}
