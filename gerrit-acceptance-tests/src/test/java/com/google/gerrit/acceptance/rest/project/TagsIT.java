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
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.ProjectApi.ListRefsRequest;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;

import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
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
    assertThat(result.get(0).ref).isEqualTo(R_TAGS + tag1.name);
    assertThat(result.get(0).revision).isEqualTo(r1.getCommit().getName());
    assertThat(result.get(1).ref).isEqualTo(R_TAGS + tag2.name);
    assertThat(result.get(1).revision).isEqualTo(r2.getCommit().getName());

    blockRead("refs/heads/hidden");
    result = getTags().get();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).ref).isEqualTo(R_TAGS + tag1.name);
    assertThat(result.get(0).revision).isEqualTo(r1.getCommit().getName());
  }

  @Test
  public void lightweightTag() throws Exception {
    grantTagPermissions();

    PushOneCommit.Tag tag = new PushOneCommit.Tag("v1.0");
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    push.setTag(tag);
    PushOneCommit.Result r = push.to("refs/for/master%submit");
    r.assertOkStatus();

    TagInfo tagInfo = getTag(tag.name);
    assertThat(tagInfo.ref).isEqualTo(R_TAGS + tag.name);
    assertThat(tagInfo.revision).isEqualTo(r.getCommit().getName());
  }

  @Test
  public void annotatedTag() throws Exception {
    grantTagPermissions();

    PushOneCommit.AnnotatedTag tag =
        new PushOneCommit.AnnotatedTag("v2.0", "annotation", admin.getIdent());
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    push.setTag(tag);
    PushOneCommit.Result r = push.to("refs/for/master%submit");
    r.assertOkStatus();

    TagInfo tagInfo = getTag(tag.name);
    assertThat(tagInfo.ref).isEqualTo(R_TAGS + tag.name);
    assertThat(tagInfo.object).isEqualTo(r.getCommit().getName());
    assertThat(tagInfo.message).isEqualTo(tag.message);
    assertThat(tagInfo.tagger.name).isEqualTo(tag.tagger.getName());
    assertThat(tagInfo.tagger.email).isEqualTo(tag.tagger.getEmailAddress());

    // A second tag pushed on the same ref should have the same ref
    String tag2ref = R_TAGS + "v2.0.1";
    PushCommand pushCmd = testRepo.git().push();
    pushCmd.setRefSpecs(new RefSpec(tag.name + ":" + tag2ref));
    Iterable<PushResult> result = pushCmd.call();
    assertThat(
        Iterables.getOnlyElement(result).getRemoteUpdate(tag2ref).getStatus())
        .isEqualTo(Status.OK);

    tagInfo = getTag(tag2ref);
    assertThat(tagInfo.ref).isEqualTo(tag2ref);
    assertThat(tagInfo.object).isEqualTo(r.getCommit().getName());
    assertThat(tagInfo.message).isEqualTo(tag.message);
    assertThat(tagInfo.tagger.name).isEqualTo(tag.tagger.getName());
    assertThat(tagInfo.tagger.email).isEqualTo(tag.tagger.getEmailAddress());
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
    for (String tagname : testTags) {
      PushOneCommit.Tag tag = new PushOneCommit.Tag(tagname);
      PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
      push.setTag(tag);
      PushOneCommit.Result result = push.to("refs/for/master%submit");
      result.assertOkStatus();
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

  private TagInfo getTag(String ref) throws Exception {
    return gApi.projects().name(project.get()).tag(ref).get();
  }
}
