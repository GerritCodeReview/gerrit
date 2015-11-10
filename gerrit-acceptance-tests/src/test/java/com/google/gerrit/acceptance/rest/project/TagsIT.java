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

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.ProjectApi.ListRefsRequest;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;

import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Test;

import java.util.List;

public class TagsIT extends AbstractDaemonTest {
  private static final List<String> testTags = ImmutableList.of(
      "tag-A", "tag-B", "tag-C", "tag-D", "tag-E", "tag-F", "tag-G", "tag-H");

  @Test
  public void listTagsOfNonExistingProject() throws Exception {
    adminSession
        .get("/projects/non-existing/tags")
        .assertNotFound();
  }

  @Test
  public void listTagsOfNonExistingProjectWithApi() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.projects().name("does-not-exist").tags().get();
  }

  @Test
  public void getTagOfNonExistingProjectWithApi() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.projects().name("does-not-exist").tag("tag").get();
  }

  @Test
  public void listTagsOfNonVisibleProject() throws Exception {
    blockRead(project, "refs/*");
    userSession
        .get("/projects/" + project.get() + "/tags")
        .assertNotFound();
  }

  @Test
  public void listTagsOfNonVisibleProjectWithApi() throws Exception {
    blockRead(project, "refs/*");
    setApiUser(user);
    exception.expect(ResourceNotFoundException.class);
    gApi.projects().name(project.get()).tags().get();
  }

  @Test
  public void getTagOfNonVisibleProjectWithApi() throws Exception {
    blockRead(project, "refs/*");
    exception.expect(ResourceNotFoundException.class);
    gApi.projects().name(project.get()).tag("tag").get();
  }

  @Test
  public void listTags() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    PushOneCommit.Tag tag1 = new PushOneCommit.Tag("v1.0");
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent(), testRepo);
    push1.setTag(tag1);
    PushOneCommit.Result r1 = push1.to("refs/for/master%submit");
    r1.assertOkStatus();

    PushOneCommit.AnnotatedTag tag2 =
        new PushOneCommit.AnnotatedTag("v2.0", "annotation", admin.getIdent());
    PushOneCommit push2 = pushFactory.create(db, admin.getIdent(), testRepo);
    push2.setTag(tag2);
    PushOneCommit.Result r2 = push2.to("refs/for/master%submit");
    r2.assertOkStatus();

    String tag3Ref = Constants.R_TAGS + "vLatest";
    PushCommand pushCmd = testRepo.git().push();
    pushCmd.setRefSpecs(new RefSpec(tag2.name + ":" + tag3Ref));
    Iterable<PushResult> r = pushCmd.call();
    assertThat(Iterables.getOnlyElement(r).getRemoteUpdate(tag3Ref).getStatus())
        .isEqualTo(Status.OK);

    List<TagInfo> result = getTags().get();
    assertThat(result).hasSize(3);

    TagInfo t = result.get(0);
    assertThat(t.ref).isEqualTo(Constants.R_TAGS + tag1.name);
    assertThat(t.revision).isEqualTo(r1.getCommitId().getName());

    t = result.get(1);
    assertThat(t.ref).isEqualTo(Constants.R_TAGS + tag2.name);
    assertThat(t.object).isEqualTo(r2.getCommitId().getName());
    assertThat(t.message).isEqualTo(tag2.message);
    assertThat(t.tagger.name).isEqualTo(tag2.tagger.getName());
    assertThat(t.tagger.email).isEqualTo(tag2.tagger.getEmailAddress());

    t = result.get(2);
    assertThat(t.ref).isEqualTo(tag3Ref);
    assertThat(t.object).isEqualTo(r2.getCommitId().getName());
    assertThat(t.message).isEqualTo(tag2.message);
    assertThat(t.tagger.name).isEqualTo(tag2.tagger.getName());
    assertThat(t.tagger.email).isEqualTo(tag2.tagger.getEmailAddress());
  }

  private void assertTagList(FluentIterable<String> expected, List<TagInfo> actual)
      throws Exception {
    assertThat(actual).hasSize(expected.size());
    for (int i = 0; i < expected.size(); i ++) {
      assertThat(actual.get(i).ref).isEqualTo("refs/tags/" + expected.get(i));
    }
  }

  @Test
  public void listTagsWithoutOptions() throws Exception {
    createTags();
    List<TagInfo> result = getTags().get();
    assertTagList(FluentIterable.from(testTags), result);
  }

  @Test
  public void listTagsWithStartOption() throws Exception {
    createTags();
    List<TagInfo> result = getTags().withStart(1).get();
    assertTagList(FluentIterable.from(testTags).skip(1), result);
  }

  @Test
  public void listTagsWithLimitOption() throws Exception {
    createTags();
    int limit = testTags.size() - 1;
    List<TagInfo> result = getTags().withLimit(limit).get();
    assertTagList(FluentIterable.from(testTags).limit(limit), result);
  }

  @Test
  public void listTagsWithLimitAndStartOption() throws Exception {
    createTags();
    int limit = testTags.size() - 3;
    List<TagInfo> result = getTags().withStart(1).withLimit(limit).get();
    assertTagList(FluentIterable.from(testTags).skip(1).limit(limit), result);
  }

  @Test
  public void listTagsWithRegexFilter() throws Exception {
    createTags();
    List<TagInfo> result = getTags().withRegex("^tag-[C|D]$").get();
    assertTagList(
        FluentIterable.from(ImmutableList.of("tag-C", "tag-D")), result);
  }

  @Test
  public void listTagsWithSubstringFilter() throws Exception {
    createTags();
    List<TagInfo> result = getTags().withSubstring("tag-").get();
    assertTagList(FluentIterable.from(testTags), result);
    result = getTags().withSubstring("ag-B").get();
    assertTagList(FluentIterable.from(ImmutableList.of("tag-B")), result);
  }

  @Test
  public void listTagsOfNonVisibleBranch() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/hidden");
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");

    PushOneCommit.Tag tag1 = new PushOneCommit.Tag("v1.0");
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent(), testRepo);
    push1.setTag(tag1);
    PushOneCommit.Result r1 = push1.to("refs/for/master%submit");
    r1.assertOkStatus();

    pushTo("refs/heads/hidden");
    PushOneCommit.Tag tag2 = new PushOneCommit.Tag("v2.0");
    PushOneCommit push2 = pushFactory.create(db, admin.getIdent(), testRepo);
    push2.setTag(tag2);
    PushOneCommit.Result r2 = push2.to("refs/for/hidden%submit");
    r2.assertOkStatus();

    List<TagInfo> result = getTags().get();
    assertThat(result).hasSize(2);
    assertThat(result.get(0).ref).isEqualTo("refs/tags/" + tag1.name);
    assertThat(result.get(0).revision).isEqualTo(r1.getCommitId().getName());
    assertThat(result.get(1).ref).isEqualTo("refs/tags/" + tag2.name);
    assertThat(result.get(1).revision).isEqualTo(r2.getCommitId().getName());

    blockRead(project, "refs/heads/hidden");
    result = getTags().get();
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
    PushOneCommit push1 = pushFactory.create(db, admin.getIdent(), testRepo);
    push1.setTag(tag1);
    PushOneCommit.Result r1 = push1.to("refs/for/master%submit");
    r1.assertOkStatus();

    TagInfo tagInfo = getTag(tag1.name);
    assertThat(tagInfo.ref).isEqualTo("refs/tags/" + tag1.name);
    assertThat(tagInfo.revision).isEqualTo(r1.getCommitId().getName());
  }

  private void createTags() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    grant(Permission.CREATE, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*");
    for (String tagname : testTags) {
      PushOneCommit.Tag tag = new PushOneCommit.Tag(tagname);
      PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
      push.setTag(tag);
      PushOneCommit.Result result = push.to("refs/for/master%submit");
      result.assertOkStatus();
    }
  }

  private ListRefsRequest<TagInfo> getTags() throws Exception {
    return gApi.projects().name(project.get()).tags();
  }

  private TagInfo getTag(String ref) throws Exception {
    return gApi.projects().name(project.get()).tag(ref).get();
  }
}
