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

import static com.google.common.truth.Truth.assertThat;

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
    assertThat(adminSession.get("/projects/non-existing/tags").getStatusCode())
        .isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void listTagsOfNonVisibleProject_NotFound() throws Exception {
    blockRead(project, "refs/*");
    assertThat(
        userSession.get("/projects/" + project.get() + "/tags").getStatusCode())
        .isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void listTags() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    PushOneCommit.Tag tag1 = new PushOneCommit.Tag("v1.0");
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent(), git);
    push1.setTag(tag1);
    PushOneCommit.Result r1 = push1.to("refs/for/master%submit");
    r1.assertOkStatus();

    PushOneCommit.AnnotatedTag tag2 =
        new PushOneCommit.AnnotatedTag("v2.0", "annotation", admin.getIdent());
    PushOneCommit push2 = pushFactory.create(db, admin.getIdent(), git);
    push2.setTag(tag2);
    PushOneCommit.Result r2 = push2.to("refs/for/master%submit");
    r2.assertOkStatus();

    List<TagInfo> result =
        toTagInfoList(adminSession.get("/projects/" + project.get() + "/tags"));
    assertThat(result).hasSize(2);

    TagInfo t = result.get(0);
    assertThat(t.ref).isEqualTo("refs/tags/" + tag1.name);
    assertThat(t.revision).isEqualTo(r1.getCommitId().getName());

    t = result.get(1);
    assertThat(t.ref).isEqualTo("refs/tags/" + tag2.name);
    assertThat(t.object).isEqualTo(r2.getCommitId().getName());
    assertThat(t.message).isEqualTo(tag2.message);
    assertThat(t.tagger.name).isEqualTo(tag2.tagger.getName());
    assertThat(t.tagger.email).isEqualTo(tag2.tagger.getEmailAddress());
  }

  @Test
  public void listTagsOfNonVisibleBranch() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/hidden");
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    PushOneCommit.Tag tag1 = new PushOneCommit.Tag("v1.0");
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent(), git);
    push1.setTag(tag1);
    PushOneCommit.Result r1 = push1.to("refs/for/master%submit");
    r1.assertOkStatus();

    pushTo("refs/heads/hidden");
    PushOneCommit.Tag tag2 = new PushOneCommit.Tag("v2.0");
    PushOneCommit push2 = pushFactory.create(db, admin.getIdent(), git);
    push2.setTag(tag2);
    PushOneCommit.Result r2 = push2.to("refs/for/hidden%submit");
    r2.assertOkStatus();

    List<TagInfo> result =
        toTagInfoList(adminSession.get("/projects/" + project.get() + "/tags"));
    assertThat(result).hasSize(2);
    assertThat(result.get(0).ref).isEqualTo("refs/tags/" + tag1.name);
    assertThat(result.get(0).revision).isEqualTo(r1.getCommitId().getName());
    assertThat(result.get(1).ref).isEqualTo("refs/tags/" + tag2.name);
    assertThat(result.get(1).revision).isEqualTo(r2.getCommitId().getName());

    blockRead(project, "refs/heads/hidden");
    result =
        toTagInfoList(adminSession.get("/projects/" + project.get() + "/tags"));
    assertThat(result).hasSize(1);
    assertThat(result.get(0).ref).isEqualTo("refs/tags/" + tag1.name);
    assertThat(result.get(0).revision).isEqualTo(r1.getCommitId().getName());
  }

  @Test
  public void getTag() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    PushOneCommit.Tag tag1 = new PushOneCommit.Tag("v1.0");
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent(), git);
    push1.setTag(tag1);
    PushOneCommit.Result r1 = push1.to("refs/for/master%submit");
    r1.assertOkStatus();

    RestResponse response =
        adminSession.get("/projects/" + project.get() + "/tags/" + tag1.name);
    TagInfo tagInfo =
        newGson().fromJson(response.getReader(), TagInfo.class);
    assertThat(tagInfo.ref).isEqualTo("refs/tags/" + tag1.name);
    assertThat(tagInfo.revision).isEqualTo(r1.getCommitId().getName());
  }

  private static List<TagInfo> toTagInfoList(RestResponse r) throws Exception {
    List<TagInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<List<TagInfo>>() {}.getType());
    return result;
  }
}
