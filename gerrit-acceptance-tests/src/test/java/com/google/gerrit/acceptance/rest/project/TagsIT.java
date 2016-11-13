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
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import java.util.List;
import org.junit.Test;

@NoHttpd
public class TagsIT extends AbstractDaemonTest {
  private static final List<String> testTags =
      ImmutableList.of("tag-A", "tag-B", "tag-C", "tag-D", "tag-E", "tag-F", "tag-G", "tag-H");

  private static final String SIGNED_ANNOTATION =
      "annotation\n"
          + "-----BEGIN PGP SIGNATURE-----\n"
          + "Version: GnuPG v1\n"
          + "\n"
          + "iQEcBAABAgAGBQJVeGg5AAoJEPfTicJkUdPkUggH/RKAeI9/i/LduuiqrL/SSdIa\n"
          + "9tYaSqJKLbXz63M/AW4Sp+4u+dVCQvnAt/a35CVEnpZz6hN4Kn/tiswOWVJf4CO7\n"
          + "htNubGs5ZMwvD6sLYqKAnrM3WxV/2TbbjzjZW6Jkidz3jz/WRT4SmjGYiEO7aA+V\n"
          + "4ZdIS9f7sW5VsHHYlNThCA7vH8Uu48bUovFXyQlPTX0pToSgrWV3JnTxDNxfn3iG\n"
          + "IL0zTY/qwVCdXgFownLcs6J050xrrBWIKqfcWr3u4D2aCLyR0v+S/KArr7ulZygY\n"
          + "+SOklImn8TAZiNxhWtA6ens66IiammUkZYFv7SSzoPLFZT4dC84SmGPWgf94NoQ=\n"
          + "=XFeC\n"
          + "-----END PGP SIGNATURE-----";

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
    assertTagList(FluentIterable.from(ImmutableList.of("tag-C", "tag-D")), result);

    // With substring filter
    result = getTags().withSubstring("tag-").get();
    assertTagList(FluentIterable.from(testTags), result);
    result = getTags().withSubstring("ag-B").get();
    assertTagList(FluentIterable.from(ImmutableList.of("tag-B")), result);
  }

  @Test
  public void listTagsOfNonVisibleBranch() throws Exception {
    grantTagPermissions();

    PushOneCommit push1 = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r1 = push1.to("refs/heads/master");
    r1.assertOkStatus();
    TagInput tag1 = new TagInput();
    tag1.ref = "v1.0";
    tag1.revision = r1.getCommit().getName();
    TagInfo result = tag(tag1.ref).create(tag1).get();
    assertThat(result.ref).isEqualTo(R_TAGS + tag1.ref);
    assertThat(result.revision).isEqualTo(tag1.revision);

    pushTo("refs/heads/hidden");
    PushOneCommit push2 = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r2 = push2.to("refs/heads/hidden");
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
    PushOneCommit.Result r = push.to("refs/heads/master");
    r.assertOkStatus();

    TagInput input = new TagInput();
    input.ref = "v1.0";
    input.revision = r.getCommit().getName();

    TagInfo result = tag(input.ref).create(input).get();
    assertThat(result.ref).isEqualTo(R_TAGS + input.ref);
    assertThat(result.revision).isEqualTo(input.revision);

    input.ref = "refs/tags/v2.0";
    result = tag(input.ref).create(input).get();
    assertThat(result.ref).isEqualTo(input.ref);
    assertThat(result.revision).isEqualTo(input.revision);

    eventRecorder.assertRefUpdatedEvents(project.get(), result.ref, null, result.revision);
  }

  @Test
  public void annotatedTag() throws Exception {
    grantTagPermissions();

    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r = push.to("refs/heads/master");
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

    eventRecorder.assertRefUpdatedEvents(project.get(), result.ref, null, result.revision);

    // A second tag pushed on the same ref should have the same ref
    TagInput input2 = new TagInput();
    input2.ref = "refs/tags/v2.0";
    input2.revision = input.revision;
    input2.message = "second annotation message";
    TagInfo result2 = tag(input2.ref).create(input2).get();
    assertThat(result2.ref).isEqualTo(input2.ref);
    assertThat(result2.object).isEqualTo(input2.revision);
    assertThat(result2.message).isEqualTo(input2.message);
    assertThat(result2.tagger.name).isEqualTo(admin.fullName);
    assertThat(result2.tagger.email).isEqualTo(admin.email);

    eventRecorder.assertRefUpdatedEvents(project.get(), result2.ref, null, result2.revision);
  }

  @Test
  public void createExistingTag() throws Exception {
    grantTagPermissions();

    TagInput input = new TagInput();
    input.ref = "test";
    TagInfo result = tag(input.ref).create(input).get();
    assertThat(result.ref).isEqualTo(R_TAGS + "test");

    input.ref = "refs/tags/test";
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("tag \"" + R_TAGS + "test\" already exists");
    tag(input.ref).create(input);
  }

  @Test
  public void createTagNotAllowed() throws Exception {
    block(Permission.CREATE, REGISTERED_USERS, R_TAGS + "*");
    TagInput input = new TagInput();
    input.ref = "test";
    exception.expect(AuthException.class);
    exception.expectMessage("Cannot create tag \"" + R_TAGS + "test\"");
    tag(input.ref).create(input);
  }

  @Test
  public void createAnnotatedTagNotAllowed() throws Exception {
    block(Permission.CREATE_TAG, REGISTERED_USERS, R_TAGS + "*");
    TagInput input = new TagInput();
    input.ref = "test";
    input.message = "annotation";
    exception.expect(AuthException.class);
    exception.expectMessage("Cannot create annotated tag \"" + R_TAGS + "test\"");
    tag(input.ref).create(input);
  }

  @Test
  public void createSignedTagNotSupported() throws Exception {
    TagInput input = new TagInput();
    input.ref = "test";
    input.message = SIGNED_ANNOTATION;
    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("Cannot create signed tag \"" + R_TAGS + "test\"");
    tag(input.ref).create(input);
  }

  @Test
  public void mismatchedInput() throws Exception {
    TagInput input = new TagInput();
    input.ref = "test";

    exception.expect(BadRequestException.class);
    exception.expectMessage("ref must match URL");
    tag("TEST").create(input);
  }

  @Test
  public void invalidTagName() throws Exception {
    grantTagPermissions();

    TagInput input = new TagInput();
    input.ref = "refs/heads/test";

    exception.expect(BadRequestException.class);
    exception.expectMessage("invalid tag name \"" + input.ref + "\"");
    tag(input.ref).create(input);
  }

  @Test
  public void invalidTagNameOnlySlashes() throws Exception {
    grantTagPermissions();

    TagInput input = new TagInput();
    input.ref = "//";

    exception.expect(BadRequestException.class);
    exception.expectMessage("invalid tag name \"refs/tags/\"");
    tag(input.ref).create(input);
  }

  @Test
  public void invalidBaseRevision() throws Exception {
    grantTagPermissions();

    TagInput input = new TagInput();
    input.ref = "test";
    input.revision = "abcdefg";

    exception.expect(BadRequestException.class);
    exception.expectMessage("Invalid base revision");
    tag(input.ref).create(input);
  }

  private void assertTagList(FluentIterable<String> expected, List<TagInfo> actual)
      throws Exception {
    assertThat(actual).hasSize(expected.size());
    for (int i = 0; i < expected.size(); i++) {
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
    grant(Permission.CREATE, project, R_TAGS + "*");
    grant(Permission.CREATE_TAG, project, R_TAGS + "*");
    grant(Permission.CREATE_SIGNED_TAG, project, R_TAGS + "*");
  }

  private ListRefsRequest<TagInfo> getTags() throws Exception {
    return gApi.projects().name(project.get()).tags();
  }

  private TagApi tag(String tagname) throws Exception {
    return gApi.projects().name(project.get()).tag(tagname);
  }
}
