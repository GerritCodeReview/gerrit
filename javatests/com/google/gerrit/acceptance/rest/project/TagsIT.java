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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.projects.ProjectApi.ListRefsRequest;
import com.google.gerrit.extensions.api.projects.TagApi;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.common.ListTagSortOption;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class TagsIT extends AbstractDaemonTest {
  private static final ImmutableList<String> testTags =
      ImmutableList.of("tag-A", "tag-B", "tag-C", "tag-D", "tag-E", "tag-F", "tag-G", "tag-H");

  private static final String ANNOTATION = "annotation";

  private static final String SIGNED_ANNOTATION_PGP =
      String.format(
          """
          %s
          -----BEGIN PGP SIGNATURE-----
          Version: GnuPG v1

          iQEcBAABAgAGBQJVeGg5AAoJEPfTicJkUdPkUggH/RKAeI9/i/LduuiqrL/SSdIa
          9tYaSqJKLbXz63M/AW4Sp+4u+dVCQvnAt/a35CVEnpZz6hN4Kn/tiswOWVJf4CO7
          htNubGs5ZMwvD6sLYqKAnrM3WxV/2TbbjzjZW6Jkidz3jz/WRT4SmjGYiEO7aA+V
          4ZdIS9f7sW5VsHHYlNThCA7vH8Uu48bUovFXyQlPTX0pToSgrWV3JnTxDNxfn3iG
          IL0zTY/qwVCdXgFownLcs6J050xrrBWIKqfcWr3u4D2aCLyR0v+S/KArr7ulZygY
          +SOklImn8TAZiNxhWtA6ens66IiammUkZYFv7SSzoPLFZT4dC84SmGPWgf94NoQ=
          =XFeC
          -----END PGP SIGNATURE-----
          """,
          ANNOTATION);

  private static final String SIGNED_ANNOTATION_SSH =
      String.format(
          """
          %s
          -----BEGIN SSH SIGNATURE-----
          U1NIU0lHAAAAAQAAAZcAAAAHc3NoLXJzYQAAAAMBAAEAAAGBALgr/qW5dQLYVBZB/osBxO
          TtD0aY+HbwhEap54yhqlmjE+XWhZNMc0u38Z+OYFOv5skOlB5oRVH/jqS1lFuGpOAfw/Vo
          TPC/fhZMIX1Ec1VGV/7dHg11WPVknewA8joWP3222Ynox2mT1LbD+WwuxKUGoTSiprGHqa
          mfjy77Lmrr3gJKDuKKOH8aVtfvP21PS9FMMV8ps8wtRAHZ53IgDMj4SSyYGeehtJjDZYMf
          ritguHyKO8WvXlClm+tZtISJIWW9Ke4R1VcU3comh+fYPmovBgxVV+cJeMREHC5pSSFOgG
          8w61C7BY9Uk0S/xsLEfb9PbTLapX34+CLr218xEcRU+ylVnQt1jm0NUGkgiju/NMqVHEQj
          UCbZnlMBW/LOFRqQWXoQBy9mvqdYV5tloExmFVgi4mvyeM/eH+yKjiCo/+Muznbrfaexut
          FZm/uZhM8aB142vM7zeDpD/cdPYlniLODOjrAnuSQmowhkTyQqDCvsmU7EcSwTAEGXiTCE
          QQAAAANnaXQAAAAAAAAABnNoYTUxMgAAAZQAAAAMcnNhLXNoYTItNTEyAAABgC7W/YE8Zy
          qyM9IUz3+BcAWc+1dp9lNuN2tzykqEx/Vk4fEiv547mu0xSeD2v6RMaMyMbJUnyBjZ929j
          pBzWsYh6hBoYc2J+6hj7yrglmQ6up/vgPtrZlJ3ms2sz4B/BKX3VxFSzd1EBscOs3+KiCd
          RlKP3fwlwyQ10oybfxxeeI+B0II22TAAZzMhYCiC6Fc7MYhmofrEYhQMD6fBRSr2lJWgNX
          O9lL6gXLkecLT7dbzkjSNjdBFhF0T9FTz+2ZwzE3iqg2NsMdWzRnctvuqPWY/wxQauzGnd
          zyLm8wAJ9ZTd9GmWJiWRddaNnUjaznAZ3Vh0eyLPRsZkcuLiWOkilMTOOnlXkLyqWRenDb
          P3m4GusrrbvQo0FeT4BdF2wCIC4cxRx+7LcZ1IMFplzlcmEu6ZP9smS25FVMWMqqaGruSD
          fgf+xSD+mpJ/ToObSyoNY0p3eZ+V2iGYrHHNJbgcj4F36aZXUYrJIhH9DgeCRSsDI+9N3X
          CShEWktrOjnA7w==
          -----END SSH SIGNATURE-----
          """,
          ANNOTATION);

  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Before
  public void setupPermissions() throws Exception {
    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      ProjectConfig cfg = u.getConfig();
      removeAllBranchPermissions(
          cfg, Permission.CREATE, Permission.CREATE_TAG, Permission.CREATE_SIGNED_TAG);
      u.save();
    }
  }

  @Test
  public void listTagsOfNonExistingProject() throws Exception {
    assertThrows(
        ResourceNotFoundException.class, () -> gApi.projects().name("does-not-exist").tags().get());
  }

  @Test
  public void getTagOfNonExistingProject() throws Exception {
    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.projects().name("does-not-exist").tag("tag").get());
  }

  @Test
  public void listTagsOfNonVisibleProject() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    requestScopeOperations.setApiUser(user.id());
    assertThrows(
        ResourceNotFoundException.class, () -> gApi.projects().name(project.get()).tags().get());
  }

  @Test
  public void getTagOfNonVisibleProject() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.projects().name(project.get()).tag("tag").get());
  }

  @Test
  @UseClockStep
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

    result = getTags().withRegex("^tag-[c|d]$").get();
    assertTagList(FluentIterable.from(ImmutableList.of()), result);

    // With substring filter
    result = getTags().withSubstring("tag-").get();
    assertTagList(FluentIterable.from(testTags), result);
    result = getTags().withSubstring("ag-B").get();
    assertTagList(FluentIterable.from(ImmutableList.of("tag-B")), result);

    // With conflicting options
    assertBadRequest(getTags().withSubstring("ag-B").withRegex("^tag-[c|d]$"));

    // with descending order
    result = getTags().withDescendingOrder(true).get();
    assertTagList(FluentIterable.from(Lists.reverse(testTags)), result);

    // with sortBy creation time
    result = getTags().withSortBy(ListTagSortOption.CREATION_TIME).get();
    assertTagList(FluentIterable.from(Lists.reverse(testTags)), result);

    // with sortBy, descending order and limit
    result =
        getTags()
            .withDescendingOrder(true)
            .withLimit(2)
            .withSortBy(ListTagSortOption.CREATION_TIME)
            .get();
    assertTagList(FluentIterable.from(ImmutableList.of("tag-A", "tag-B")), result);
  }

  @Test
  public void listTagsOfNonVisibleBranch() throws Exception {
    grantLightweightTagPermissions();
    // Allow creating a new hidden branch
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).group(REGISTERED_USERS).ref("refs/heads/hidden"))
        .update();

    PushOneCommit push1 = pushFactory.create(admin.newIdent(), testRepo);
    PushOneCommit.Result r1 = push1.to("refs/heads/master");
    r1.assertOkStatus();
    TagInput tag1 = new TagInput();
    tag1.ref = "v1.0";
    tag1.revision = r1.getCommit().getName();
    TagInfo result = tag(tag1.ref).create(tag1).get();
    assertThat(result.ref).isEqualTo(R_TAGS + tag1.ref);
    assertThat(result.revision).isEqualTo(tag1.revision);

    pushTo("refs/heads/hidden").assertOkStatus();
    PushOneCommit push2 = pushFactory.create(admin.newIdent(), testRepo);
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

    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/hidden").group(REGISTERED_USERS))
        .update();
    tags = getTags().get();
    assertThat(tags).hasSize(1);
    assertThat(tags.get(0).ref).isEqualTo(R_TAGS + tag1.ref);
    assertThat(tags.get(0).revision).isEqualTo(tag1.revision);
  }

  @Test
  public void lightweightTag() throws Exception {
    grantLightweightTagPermissions();

    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    PushOneCommit.Result r = push.to("refs/heads/master");
    r.assertOkStatus();

    TagInput input = new TagInput();
    input.ref = "v1.0";
    input.revision = r.getCommit().getName();

    TagInfo result = tag(input.ref).create(input).get();
    assertThat(result.ref).isEqualTo(R_TAGS + input.ref);
    assertThat(result.revision).isEqualTo(input.revision);
    assertThat(result.canDelete).isTrue();
    assertThat(result.created.toInstant()).isEqualTo(instant(r));

    input.ref = "refs/tags/v2.0";
    result = tag(input.ref).create(input).get();
    assertThat(result.ref).isEqualTo(input.ref);
    assertThat(result.revision).isEqualTo(input.revision);
    assertThat(result.canDelete).isTrue();
    assertThat(result.created.toInstant()).isEqualTo(instant(r));

    requestScopeOperations.setApiUser(user.id());
    result = tag(input.ref).get();
    assertThat(result.canDelete).isNull();

    eventRecorder.assertRefUpdatedEvents(project.get(), result.ref, null, result.revision);
  }

  @Test
  public void annotatedTag() throws Exception {
    grantAnnotatedTagPermissions();

    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
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
    assertThat(result.tagger.name).isEqualTo(admin.fullName());
    assertThat(result.tagger.email).isEqualTo(admin.email());
    assertThat(result.created).isEqualTo(result.tagger.date);

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
    assertThat(result2.tagger.name).isEqualTo(admin.fullName());
    assertThat(result2.tagger.email).isEqualTo(admin.email());
    assertThat(result2.created).isEqualTo(result2.tagger.date);

    eventRecorder.assertRefUpdatedEvents(project.get(), result2.ref, null, result2.revision);
  }

  @Test
  public void createExistingTag() throws Exception {
    grantLightweightTagPermissions();

    TagInput input = new TagInput();
    input.ref = "test";
    TagInfo result = tag(input.ref).create(input).get();
    assertThat(result.ref).isEqualTo(R_TAGS + "test");

    input.ref = "refs/tags/test";
    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> tag(input.ref).create(input));
    assertThat(thrown).hasMessageThat().contains("tag \"" + R_TAGS + "test\" already exists");
  }

  @Test
  public void createTagNotAllowed() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.CREATE).ref(R_TAGS + "*").group(REGISTERED_USERS))
        .update();
    TagInput input = new TagInput();
    input.ref = "test";
    AuthException thrown = assertThrows(AuthException.class, () -> tag(input.ref).create(input));
    assertThat(thrown).hasMessageThat().contains("not permitted: create");
  }

  @Test
  public void createAnnotatedTagNotAllowed() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.CREATE_TAG).ref(R_TAGS + "*").group(REGISTERED_USERS))
        .update();
    TagInput input = new TagInput();
    input.ref = "test";
    input.message = "annotation";
    AuthException thrown = assertThrows(AuthException.class, () -> tag(input.ref).create(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Cannot create annotated tag \"" + R_TAGS + "test\"");
  }

  @Test
  public void createTagSignedWithSshKey() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE_SIGNED_TAG).ref(R_TAGS + "*").group(REGISTERED_USERS))
        .update();

    TagInput input = new TagInput();
    input.ref = "test";
    input.message = SIGNED_ANNOTATION_SSH;
    input.revision = projectOperations.project(project).getHead("master").name();

    TagInfo tagInfo = tag(input.ref).create(input).get();
    assertThat(tagInfo.ref).isEqualTo("refs/tags/" + input.ref);
    assertThat(tagInfo.object).isEqualTo(input.revision);
    assertThat(tagInfo.message).isEqualTo(ANNOTATION);
    assertThat(tagInfo.tagger.name).isEqualTo(admin.fullName());
    assertThat(tagInfo.tagger.email).isEqualTo(admin.email());
    assertThat(tagInfo.created).isEqualTo(tagInfo.tagger.date);
  }

  @Test
  public void cannotCeateTagSignedWithPgpKey() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE_SIGNED_TAG).ref(R_TAGS + "*").group(REGISTERED_USERS))
        .update();

    TagInput input = new TagInput();
    input.ref = "test";
    input.message = SIGNED_ANNOTATION_PGP;
    input.revision = projectOperations.project(project).getHead("master").name();

    MethodNotAllowedException thrown =
        assertThrows(MethodNotAllowedException.class, () -> tag(input.ref).create(input));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Cannot create signed tag \"%s\": PGP signature not supported", R_TAGS + "test"));
  }

  @Test
  public void createSignedTagNotAllowed() throws Exception {
    TagInput input = new TagInput();
    input.ref = "test";
    input.message = SIGNED_ANNOTATION_SSH;
    AuthException thrown = assertThrows(AuthException.class, () -> tag(input.ref).create(input));
    assertThat(thrown).hasMessageThat().contains("Cannot create signed tag \"" + R_TAGS + "test\"");
  }

  @Test
  @UseClockStep
  public void createTagWithDate() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE_TAG).ref(R_TAGS + "*").group(REGISTERED_USERS))
        .update();

    TagInput input = new TagInput();
    input.ref = "test";
    input.message = "annotated";
    input.date = Timestamp.from(TimeUtil.now());

    TagInfo tagInfo = tag(input.ref).create(input).get();
    assertThat(tagInfo.message).isEqualTo(input.message);
    assertThat(tagInfo.tagger.name).isEqualTo(admin.fullName());
    assertThat(tagInfo.tagger.email).isEqualTo(admin.email());
    assertThat(tagInfo.tagger.date).isEqualTo(input.date);
    assertThat(tagInfo.created).isEqualTo(tagInfo.tagger.date);
  }

  @Test
  public void createTagWithDateInTheFuture() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE_TAG).ref(R_TAGS + "*").group(REGISTERED_USERS))
        .update();

    TagInput input = new TagInput();
    input.ref = "test";
    input.message = "annotated";
    input.date = Timestamp.valueOf("9999-12-31 23:59:59.999");

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> tag(input.ref).create(input));
    assertThat(thrown).hasMessageThat().contains("date cannot be in the future");
  }

  @Test
  public void mismatchedInput() throws Exception {
    TagInput input = new TagInput();
    input.ref = "test";

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> tag("TEST").create(input));
    assertThat(thrown).hasMessageThat().contains("ref must match URL");
  }

  @Test
  public void invalidTagName() throws Exception {
    TagInput input = new TagInput();
    input.ref = "refs/heads/test";

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> tag(input.ref).create(input));
    assertThat(thrown).hasMessageThat().contains("invalid tag name \"" + input.ref + "\"");
  }

  @Test
  public void invalidTagNameOnlySlashes() throws Exception {
    TagInput input = new TagInput();
    input.ref = "//";

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> tag(input.ref).create(input));
    assertThat(thrown).hasMessageThat().contains("invalid tag name \"refs/tags/\"");
  }

  @Test
  public void nonExistingBaseRevision() throws Exception {
    TagInput input = new TagInput();
    input.ref = "test";
    input.revision = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

    UnprocessableEntityException thrown =
        assertThrows(UnprocessableEntityException.class, () -> tag(input.ref).create(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("base revision \"deadbeefdeadbeefdeadbeefdeadbeefdeadbeef\" not found");
  }

  @Test
  public void invalidBaseRevision() throws Exception {
    TagInput input = new TagInput();
    input.ref = "test";
    input.revision = "invalid\trevision";

    UnprocessableEntityException thrown =
        assertThrows(UnprocessableEntityException.class, () -> tag(input.ref).create(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("base revision \"" + input.revision + "\" is invalid");
  }

  @Test
  public void nonCommitRevision() throws Exception {
    TagInput input = new TagInput();
    input.ref = "test";
    input.revision =
        projectOperations.project(project).getHead("refs/heads/master").getTree().name();

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> tag(input.ref).create(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("base revision \"" + input.revision + "\" is not a commit");
  }

  @Test
  public void noBaseRevision() throws Exception {
    grantLightweightTagPermissions();

    // If revision is not specified, the tag is created based on HEAD, which points to master.
    RevCommit expectedRevision = projectOperations.project(project).getHead("master");

    TagInput input = new TagInput();
    input.ref = "test";
    input.revision = null;

    TagInfo result = tag(input.ref).create(input).get();
    assertThat(result.ref).isEqualTo(R_TAGS + input.ref);
    assertThat(result.revision).isEqualTo(expectedRevision.name());
  }

  @Test
  public void emptyBaseRevision() throws Exception {
    grantLightweightTagPermissions();

    // If revision is not specified, the tag is created based on HEAD, which points to master.
    RevCommit expectedRevision = projectOperations.project(project).getHead("master");

    TagInput input = new TagInput();
    input.ref = "test";
    input.revision = "";

    TagInfo result = tag(input.ref).create(input).get();
    assertThat(result.ref).isEqualTo(R_TAGS + input.ref);
    assertThat(result.revision).isEqualTo(expectedRevision.name());
  }

  @Test
  public void baseRevisionIsTrimmed() throws Exception {
    grantLightweightTagPermissions();

    RevCommit revision = projectOperations.project(project).getHead("master");

    TagInput input = new TagInput();
    input.ref = "test";
    input.revision = "\t" + revision.name();

    TagInfo result = tag(input.ref).create(input).get();
    assertThat(result.ref).isEqualTo(R_TAGS + input.ref);
    assertThat(result.revision).isEqualTo(revision.name());
  }

  @Test
  public void cannotCreateTagIfProjectIsReadOnly() throws Exception {
    grantLightweightTagPermissions();
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    PushOneCommit.Result r = push.to("refs/heads/master");
    r.assertOkStatus();

    TagInput input = new TagInput();
    input.ref = "v1.0";
    gApi.projects().name(project.get()).tag(input.ref).create(input);

    try (Repository repo = repoManager.openRepository(project)) {
      try (ProjectConfigUpdate u = updateProject(project)) {
        u.getConfig().updateProject(p -> p.setState(ProjectState.READ_ONLY));
        u.save();
      }
    }

    TagInput newTag = new TagInput();
    newTag.ref = "v2.0";
    assertThrows(
        ResourceConflictException.class,
        () -> gApi.projects().name(project.get()).tag(newTag.ref).create(newTag));
  }

  private void assertTagList(FluentIterable<String> expected, List<TagInfo> actual)
      throws Exception {
    assertThat(actual).hasSize(expected.size());
    for (int i = 0; i < expected.size(); i++) {
      TagInfo info = actual.get(i);
      assertThat(info.created).isNotNull();
      assertThat(info.ref).isEqualTo(R_TAGS + expected.get(i));
    }
  }

  private void createTags() throws Exception {
    grantAnnotatedTagPermissions();

    String revision = pushTo("refs/heads/master").getCommit().name();
    TagInput input = new TagInput();
    input.revision = revision;

    // Creating the tags in reverse order to allow testing the sortBy option
    for (String tagname : Lists.reverse(testTags)) {
      input.message = tagname; // This updates the 'created' time of the tag
      TagInfo result = tag(tagname).create(input).get();
      assertThat(result.ref).isEqualTo(R_TAGS + tagname);
    }
  }

  private ListRefsRequest<TagInfo> getTags() throws Exception {
    return gApi.projects().name(project.get()).tags();
  }

  private TagApi tag(String tagname) throws Exception {
    return gApi.projects().name(project.get()).tag(tagname);
  }

  private Instant instant(PushOneCommit.Result r) {
    return r.getCommit().getCommitterIdent().getWhenAsInstant();
  }

  private void assertBadRequest(ListRefsRequest<TagInfo> req) throws Exception {
    assertThrows(BadRequestException.class, () -> req.get());
  }

  private void grantLightweightTagPermissions() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(R_TAGS + "*").group(adminGroupUuid()))
        .update();
  }

  private void grantAnnotatedTagPermissions() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE_TAG).ref(R_TAGS + "*").group(adminGroupUuid()))
        .update();
  }

  private static void removeAllBranchPermissions(ProjectConfig cfg, String... permissions) {
    for (AccessSection accessSection : cfg.getAccessSections()) {
      cfg.upsertAccessSection(
          accessSection.getName(),
          updatedAccessSection -> {
            Arrays.stream(permissions).forEach(updatedAccessSection::removePermission);
          });
    }
  }
}
