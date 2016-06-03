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
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.ProjectApi.ListRefsRequest;
import com.google.gerrit.extensions.api.projects.TagApi;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;

import org.junit.Test;

import java.util.List;

@NoHttpd
public class TagsIT extends AbstractDaemonTest {
  private static final List<String> testTags = ImmutableList.of(
      "tag-A", "tag-B", "tag-C", "tag-D", "tag-E", "tag-F", "tag-G", "tag-H");

  @Test
  public void listTagsOfNonExistingProject() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.projects().name("does-not-exist").tags().get();
  }

  @Test
  public void getTagOfNonExistingProject() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.projects().name("does-not-exist").tag("tag").get();
  }

  @Test
  public void listTagsOfNonVisibleProject() throws Exception {
    blockRead("refs/*");
    setApiUser(user);
    exception.expect(ResourceNotFoundException.class);
    gApi.projects().name(project.get()).tags().get();
  }

  @Test
  public void getTagOfNonVisibleProject() throws Exception {
    blockRead("refs/*");
    exception.expect(ResourceNotFoundException.class);
    gApi.projects().name(project.get()).tag("tag").get();
  }

  @Test
  public void listTags() throws Exception {
    createTags();

    // No options
    List<TagInfo> result = getTags().get();
    assertTagList(FluentIterable.from(testTags), result);

    // With start option
    result = getTags().withStart(1).get();
    assertTagList(FluentIterable.from(testTags).skip(1), result);

    // With limit option
    int limit = testTags.size() - 1;
    result = getTags().withLimit(limit).get();
    assertTagList(FluentIterable.from(testTags).limit(limit), result);

    // With both start and limit
    limit = testTags.size() - 3;
    result = getTags().withStart(1).withLimit(limit).get();
    assertTagList(FluentIterable.from(testTags).skip(1).limit(limit), result);

    // With regular expression filter
    result = getTags().withRegex("^tag-[C|D]$").get();
    assertTagList(
        FluentIterable.from(ImmutableList.of("tag-C", "tag-D")), result);

    // With substring filter
    result = getTags().withSubstring("tag-").get();
    assertTagList(FluentIterable.from(testTags), result);
    result = getTags().withSubstring("ag-B").get();
    assertTagList(FluentIterable.from(ImmutableList.of("tag-B")), result);
  }

  @Test
  public void listTagsOfNonVisibleBranch() throws Exception {
    grantTagPermissions();
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/hidden");

    PushOneCommit push1 = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r1 = push1.to("refs/for/master%submit");
    r1.assertOkStatus();
    TagInput tag1 = new TagInput();
    tag1.ref = "v1.0";
    tag1.revision = r1.getCommit().getName();
    TagInfo result = tag(tag1.ref).create(tag1).get();
    assertThat(result.ref).isEqualTo(R_TAGS + tag1.ref);
    assertThat(result.revision).isEqualTo(tag1.revision);

    pushTo("refs/heads/hidden");
    PushOneCommit push2 = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r2 = push2.to("refs/for/hidden%submit");
    r2.assertOkStatus();

    TagInput tag2 = new TagInput();
    tag2.ref = "v2.0";
    tag2.revision = r2.getCommit().getName();
    result = tag(tag2.ref).create(tag2).get();
    assertThat(result.ref).isEqualTo(R_TAGS + tag2.ref);
    assertThat(result.revision).isEqualTo(tag2.revision);

    List<TagInfo> tags = getTags().get();
    assertThat(tags).hasSize(2);
    assertThat(tags.get(0).ref).isEqualTo(R_TAGS + tag1.ref);
    assertThat(tags.get(0).revision).isEqualTo(tag1.revision);
    assertThat(tags.get(1).ref).isEqualTo(R_TAGS + tag2.ref);
    assertThat(tags.get(1).revision).isEqualTo(tag2.revision);

    blockRead("refs/heads/hidden");
    tags = getTags().get();
    assertThat(tags).hasSize(1);
    assertThat(tags.get(0).ref).isEqualTo(R_TAGS + tag1.ref);
    assertThat(tags.get(0).revision).isEqualTo(tag1.revision);
  }

  @Test
  public void lightweightTag() throws Exception {
    grantTagPermissions();

    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r = push.to("refs/for/master%submit");
    r.assertOkStatus();

    TagInput input = new TagInput();
    input.ref = "v1.0";
    input.revision = r.getCommit().getName();

    TagInfo result = tag(input.ref).create(input).get();
    assertThat(result.ref).isEqualTo(R_TAGS + input.ref);
    assertThat(result.revision).isEqualTo(input.revision);
  }

  @Test
  public void annotatedTag() throws Exception {
    grantTagPermissions();

    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r = push.to("refs/for/master%submit");
    r.assertOkStatus();

    TagInput input = new TagInput();
    input.ref = "v1.0";
    input.revision = r.getCommit().getName();
    input.message = "annotation message";

    TagInfo result = tag(input.ref).create(input).get();
    assertThat(result.ref).isEqualTo(R_TAGS + input.ref);
    assertThat(result.object).isEqualTo(input.revision);
    assertThat(result.message).isEqualTo(input.message);
    assertThat(result.tagger.name).isEqualTo(admin.fullName);
    assertThat(result.tagger.email).isEqualTo(admin.email);

    // A second tag pushed on the same ref should have the same ref
    input.ref = "v2.0";
    result = tag(input.ref).create(input).get();
    assertThat(result.ref).isEqualTo(R_TAGS + input.ref);
    assertThat(result.object).isEqualTo(input.revision);
    assertThat(result.message).isEqualTo(input.message);
    assertThat(result.tagger.name).isEqualTo(admin.fullName);
    assertThat(result.tagger.email).isEqualTo(admin.email);
  }

  @Test
  public void createExistingTag() throws Exception {
    TagInput input = new TagInput();
    TagInfo result = tag("test").create(input).get();
    assertThat(result.ref).isEqualTo(R_TAGS + "test");
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("tag \"" + R_TAGS + "test\" already exists");
    tag("test").create(input);
  }

  private void assertTagList(FluentIterable<String> expected,
      List<TagInfo> actual) throws Exception {
    assertThat(actual).hasSize(expected.size());
    for (int i = 0; i < expected.size(); i ++) {
      assertThat(actual.get(i).ref).isEqualTo(R_TAGS + expected.get(i));
    }
  }

  private void createTags() throws Exception {
    grantTagPermissions();

    String revision = pushTo("refs/heads/master").getCommit().name();
    TagInput input = new TagInput();
    input.revision = revision;

    for (String tagname : testTags) {
      TagInfo result = tag(tagname).create(input).get();
      assertThat(result.revision).isEqualTo(input.revision);
      assertThat(result.ref).isEqualTo(R_TAGS + tagname);
    }
  }

  private void grantTagPermissions() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    grant(Permission.CREATE, project, R_TAGS + "*");
    grant(Permission.PUSH, project, R_TAGS + "*");
  }

  private ListRefsRequest<TagInfo> getTags() throws Exception {
    return gApi.projects().name(project.get()).tags();
  }

  private TagApi tag(String tagname) throws Exception {
    return gApi.projects().name(project.get()).tag(tagname);
  }
}
