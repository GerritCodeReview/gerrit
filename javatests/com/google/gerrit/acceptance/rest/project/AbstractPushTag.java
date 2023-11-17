// Copyright (C) 2016 The Android Open Source Project
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
import static com.google.gerrit.acceptance.GitUtil.createAnnotatedTag;
import static com.google.gerrit.acceptance.GitUtil.deleteRef;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.GitUtil.updateAnnotatedTag;
import static com.google.gerrit.acceptance.rest.project.AbstractPushTag.TagType.ANNOTATED;
import static com.google.gerrit.acceptance.rest.project.AbstractPushTag.TagType.LIGHTWEIGHT;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.base.MoreObjects;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.project.testing.Util;
import com.google.gerrit.testing.ConfigSuite;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractPushTag extends AbstractDaemonTest {
  enum TagType {
    LIGHTWEIGHT(Permission.CREATE),
    ANNOTATED(Permission.CREATE_TAG);

    final String createPermission;

    TagType(String createPermission) {
      this.createPermission = createPermission;
    }
  }

  @ConfigSuite.Config
  public static Config skipFalse() {
    Config config = new Config();
    config.setBoolean("auth", null, "skipFullRefEvaluationIfAllRefsAreVisible", false);
    return config;
  }

  private RevCommit initialHead;
  private TagType tagType;

  @Before
  public void setUpTestEnvironment() throws Exception {
    initialHead = getRemoteHead();
    tagType = getTagType();
    removeAnonymousRead();
  }

  protected abstract TagType getTagType();

  @Test
  public void createTagForExistingCommit() throws Exception {
    pushTagForExistingCommit(Status.REJECTED_OTHER_REASON);

    allowTagCreation();
    pushTagForExistingCommit(Status.OK);

    allowPushOnRefsTags();
    pushTagForExistingCommit(Status.OK);

    removePushFromRefsTags();
  }

  @Test
  public void createTagForNewCommit() throws Exception {
    pushTagForNewCommit(Status.REJECTED_OTHER_REASON);

    allowTagCreation();
    pushTagForNewCommit(Status.REJECTED_OTHER_REASON);

    allowPushOnRefsTags();
    pushTagForNewCommit(Status.OK);

    removePushFromRefsTags();
  }

  @Test
  public void createTagForExistingCommit_withoutGlobalReadPermissions() throws Exception {
    removeReadAccessOnRefsStar();
    grantReadAccessOnRefsHeadsStar();
    createTagForExistingCommit();
  }

  @Test
  public void createTagForNewCommit_withoutGlobalReadPermissions() throws Exception {
    removeReadAccessOnRefsStar();
    grantReadAccessOnRefsHeadsStar();
    createTagForNewCommit();
  }

  @Test
  public void fastForward() throws Exception {
    allowTagCreation();
    String tagName = pushTagForExistingCommit(Status.OK);

    fastForwardTagToExistingCommit(tagName, Status.REJECTED_OTHER_REASON);
    fastForwardTagToNewCommit(tagName, Status.REJECTED_OTHER_REASON);

    allowTagDeletion();
    fastForwardTagToExistingCommit(tagName, Status.REJECTED_OTHER_REASON);
    fastForwardTagToNewCommit(tagName, Status.REJECTED_OTHER_REASON);

    allowPushOnRefsTags();
    Status expectedStatus = tagType == ANNOTATED ? Status.REJECTED_OTHER_REASON : Status.OK;
    fastForwardTagToExistingCommit(tagName, expectedStatus);
    fastForwardTagToNewCommit(tagName, expectedStatus);

    // Above we just fast-forwarded the tag to a new commit which is not part of any branch. By
    // default this tag is not visible, as users can only see tags that point to commits that are
    // part of visible branches, which is not the case for this tag. It's odd that we allow the user
    // to create such a tag that is then not visible to the creator. Below we want to fast-forward
    // this tag, but this is only possible if the tag is visible. To make it visible we must allow
    // the user to read all tags, regardless of whether it points to a commit that is part of a
    // visible branch.
    allowReadingAllTag();

    allowForcePushOnRefsTags();
    fastForwardTagToExistingCommit(tagName, Status.OK);
    fastForwardTagToNewCommit(tagName, Status.OK);

    removePushFromRefsTags();
  }

  @Test
  public void forceUpdate() throws Exception {
    allowTagCreation();
    String tagName = pushTagForExistingCommit(Status.OK);

    forceUpdateTagToExistingCommit(tagName, Status.REJECTED_OTHER_REASON);
    forceUpdateTagToNewCommit(tagName, Status.REJECTED_OTHER_REASON);

    allowPushOnRefsTags();
    forceUpdateTagToExistingCommit(tagName, Status.REJECTED_OTHER_REASON);
    forceUpdateTagToNewCommit(tagName, Status.REJECTED_OTHER_REASON);

    allowTagDeletion();
    forceUpdateTagToExistingCommit(tagName, Status.REJECTED_OTHER_REASON);
    forceUpdateTagToNewCommit(tagName, Status.REJECTED_OTHER_REASON);

    allowForcePushOnRefsTags();
    forceUpdateTagToExistingCommit(tagName, Status.OK);
    forceUpdateTagToNewCommit(tagName, Status.OK);

    removePushFromRefsTags();
  }

  @Test
  public void delete() throws Exception {
    allowTagCreation();
    String tagName = pushTagForExistingCommit(Status.OK);

    pushTagDeletion(tagName, Status.REJECTED_OTHER_REASON);

    allowPushOnRefsTags();
    pushTagDeletion(tagName, Status.REJECTED_OTHER_REASON);

    allowForcePushOnRefsTags();
    tagName = pushTagForExistingCommit(Status.OK);
    pushTagDeletion(tagName, Status.OK);

    removePushFromRefsTags();
    allowTagDeletion();
    tagName = pushTagForExistingCommit(Status.OK);
    pushTagDeletion(tagName, Status.OK);
  }

  private String pushTagForExistingCommit(Status expectedStatus) throws Exception {
    return pushTag(null, false, false, expectedStatus);
  }

  private String pushTagForNewCommit(Status expectedStatus) throws Exception {
    return pushTag(null, true, false, expectedStatus);
  }

  private void fastForwardTagToExistingCommit(String tagName, Status expectedStatus)
      throws Exception {
    pushTag(tagName, false, false, expectedStatus);
  }

  private void fastForwardTagToNewCommit(String tagName, Status expectedStatus) throws Exception {
    pushTag(tagName, true, false, expectedStatus);
  }

  private void forceUpdateTagToExistingCommit(String tagName, Status expectedStatus)
      throws Exception {
    pushTag(tagName, false, true, expectedStatus);
  }

  private void forceUpdateTagToNewCommit(String tagName, Status expectedStatus) throws Exception {
    pushTag(tagName, true, true, expectedStatus);
  }

  private String pushTag(String tagName, boolean newCommit, boolean force, Status expectedStatus)
      throws Exception {
    if (force) {
      testRepo.reset(initialHead);
    }
    commit(user.getIdent(), "subject");

    boolean createTag = tagName == null;
    tagName = MoreObjects.firstNonNull(tagName, "v1_" + System.nanoTime());
    switch (tagType) {
      case LIGHTWEIGHT:
        break;
      case ANNOTATED:
        if (createTag) {
          createAnnotatedTag(testRepo, tagName, user.getIdent());
        } else {
          updateAnnotatedTag(testRepo, tagName, user.getIdent());
        }
        break;
      default:
        throw new IllegalStateException("unexpected tag type: " + tagType);
    }

    if (!newCommit) {
      grant(project, "refs/for/refs/heads/master", Permission.SUBMIT, false, REGISTERED_USERS);
      pushHead(testRepo, "refs/for/master%submit");
    }

    String tagRef = tagRef(tagName);
    PushResult r =
        tagType == LIGHTWEIGHT
            ? pushHead(testRepo, tagRef, false, force)
            : GitUtil.pushTag(testRepo, tagName, !createTag);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(tagRef);
    assertThat(refUpdate.getStatus()).named(tagType.name()).isEqualTo(expectedStatus);
    return tagName;
  }

  private void pushTagDeletion(String tagName, Status expectedStatus) throws Exception {
    String tagRef = tagRef(tagName);
    PushResult r = deleteRef(testRepo, tagRef);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(tagRef);
    assertThat(refUpdate.getStatus()).named(tagType.name()).isEqualTo(expectedStatus);
  }

  private void removeReadAccessOnRefsStar() throws Exception {
    removePermission(allProjects, "refs/heads/*", Permission.READ);
    removePermission(project, "refs/heads/*", Permission.READ);
  }

  private void grantReadAccessOnRefsHeadsStar() throws Exception {
    grant(project, "refs/heads/*", Permission.READ, false, REGISTERED_USERS);
  }

  private void allowReadingAllTag() throws Exception {
    // Tags are only visible if the commits to which they point are part of a visible branch.
    // To make all tags visible, including tags that point to commits that are not part of a visible
    // branch, either auth.skipFullRefEvaluationIfAllRefsAreVisible in gerrit.config needs to be
    // true, or the user must have READ access for all refs in the repository.

    if (cfg.getBoolean("auth", "skipFullRefEvaluationIfAllRefsAreVisible", true)) {
      return;
    }

    // By default READ access in the All-Projects project is granted to registered users on refs/*,
    // which makes all refs, except refs/meta/config, visible to them. refs/meta/config is not
    // visible since by default READ access to it is exclusively granted to the project owners only.
    // This means to make all refs, and thus all tags, visible, we must allow registered users to
    // see the refs/meta/config branch.
    allow(project, "refs/meta/config", Permission.READ, REGISTERED_USERS);
  }

  private void allowTagCreation() throws Exception {
    grant(project, "refs/tags/*", tagType.createPermission, false, REGISTERED_USERS);
  }

  private void allowPushOnRefsTags() throws Exception {
    removePushFromRefsTags();
    grant(project, "refs/tags/*", Permission.PUSH, false, REGISTERED_USERS);
  }

  private void allowForcePushOnRefsTags() throws Exception {
    removePushFromRefsTags();
    grant(project, "refs/tags/*", Permission.PUSH, true, REGISTERED_USERS);
  }

  private void allowTagDeletion() throws Exception {
    removePushFromRefsTags();
    grant(project, "refs/tags/*", Permission.DELETE, true, REGISTERED_USERS);
  }

  private void removePushFromRefsTags() throws Exception {
    removePermission(project, "refs/tags/*", Permission.PUSH);
  }

  private void removeAnonymousRead() throws Exception {
    AccountGroup.UUID anonymous = systemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
    AccountGroup.UUID registered = systemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    String allRefs = RefNames.REFS + "*";
    try (ProjectConfigUpdate u = updateProject(project)) {
      Util.block(u.getConfig(), Permission.READ, anonymous, allRefs);
      Util.allow(u.getConfig(), Permission.READ, registered, allRefs);
      u.save();
    }
  }

  private void commit(PersonIdent ident, String subject) throws Exception {
    commitBuilder().ident(ident).message(subject + " (" + System.nanoTime() + ")").create();
  }

  private static String tagRef(String tagName) {
    return RefNames.REFS_TAGS + tagName;
  }
}