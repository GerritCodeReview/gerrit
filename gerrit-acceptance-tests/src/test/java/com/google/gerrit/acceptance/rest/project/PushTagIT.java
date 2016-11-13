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
import static com.google.gerrit.acceptance.rest.project.PushTagIT.TagType.ANNOTATED;
import static com.google.gerrit.acceptance.rest.project.PushTagIT.TagType.LIGHTWEIGHT;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.base.MoreObjects;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.RefNames;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class PushTagIT extends AbstractDaemonTest {
  enum TagType {
    LIGHTWEIGHT(Permission.CREATE),
    ANNOTATED(Permission.CREATE_TAG);

    final String createPermission;

    TagType(String createPermission) {
      this.createPermission = createPermission;
    }
  }

  private RevCommit initialHead;

  @Before
  public void setup() throws Exception {
    // clone with user to avoid inherited tag permissions of admin user
    testRepo = cloneProject(project, user);

    initialHead = getRemoteHead();
  }

  @Test
  public void createTagForExistingCommit() throws Exception {
    for (TagType tagType : TagType.values()) {
      pushTagForExistingCommit(tagType, Status.REJECTED_OTHER_REASON);

      allowTagCreation(tagType);
      pushTagForExistingCommit(tagType, Status.OK);

      allowPushOnRefsTags();
      pushTagForExistingCommit(tagType, Status.OK);

      removePushFromRefsTags();
    }
  }

  @Test
  public void createTagForNewCommit() throws Exception {
    for (TagType tagType : TagType.values()) {
      pushTagForNewCommit(tagType, Status.REJECTED_OTHER_REASON);

      allowTagCreation(tagType);
      pushTagForNewCommit(tagType, Status.REJECTED_OTHER_REASON);

      allowPushOnRefsTags();
      pushTagForNewCommit(tagType, Status.OK);

      removePushFromRefsTags();
    }
  }

  @Test
  public void fastForward() throws Exception {
    for (TagType tagType : TagType.values()) {
      allowTagCreation(tagType);
      String tagName = pushTagForExistingCommit(tagType, Status.OK);

      fastForwardTagToExistingCommit(tagType, tagName, Status.REJECTED_OTHER_REASON);
      fastForwardTagToNewCommit(tagType, tagName, Status.REJECTED_OTHER_REASON);

      allowTagDeletion();
      fastForwardTagToExistingCommit(tagType, tagName, Status.REJECTED_OTHER_REASON);
      fastForwardTagToNewCommit(tagType, tagName, Status.REJECTED_OTHER_REASON);

      allowPushOnRefsTags();
      Status expectedStatus = tagType == ANNOTATED ? Status.REJECTED_OTHER_REASON : Status.OK;
      fastForwardTagToExistingCommit(tagType, tagName, expectedStatus);
      fastForwardTagToNewCommit(tagType, tagName, expectedStatus);

      allowForcePushOnRefsTags();
      fastForwardTagToExistingCommit(tagType, tagName, Status.OK);
      fastForwardTagToNewCommit(tagType, tagName, Status.OK);

      removePushFromRefsTags();
    }
  }

  @Test
  public void forceUpdate() throws Exception {
    for (TagType tagType : TagType.values()) {
      allowTagCreation(tagType);
      String tagName = pushTagForExistingCommit(tagType, Status.OK);

      forceUpdateTagToExistingCommit(tagType, tagName, Status.REJECTED_OTHER_REASON);
      forceUpdateTagToNewCommit(tagType, tagName, Status.REJECTED_OTHER_REASON);

      allowPushOnRefsTags();
      forceUpdateTagToExistingCommit(tagType, tagName, Status.REJECTED_OTHER_REASON);
      forceUpdateTagToNewCommit(tagType, tagName, Status.REJECTED_OTHER_REASON);

      allowTagDeletion();
      forceUpdateTagToExistingCommit(tagType, tagName, Status.REJECTED_OTHER_REASON);
      forceUpdateTagToNewCommit(tagType, tagName, Status.REJECTED_OTHER_REASON);

      allowForcePushOnRefsTags();
      forceUpdateTagToExistingCommit(tagType, tagName, Status.OK);
      forceUpdateTagToNewCommit(tagType, tagName, Status.OK);

      removePushFromRefsTags();
    }
  }

  @Test
  public void delete() throws Exception {
    for (TagType tagType : TagType.values()) {
      allowTagCreation(tagType);
      String tagName = pushTagForExistingCommit(tagType, Status.OK);

      pushTagDeletion(tagType, tagName, Status.REJECTED_OTHER_REASON);

      allowPushOnRefsTags();
      pushTagDeletion(tagType, tagName, Status.REJECTED_OTHER_REASON);
    }

    allowForcePushOnRefsTags();
    for (TagType tagType : TagType.values()) {
      String tagName = pushTagForExistingCommit(tagType, Status.OK);
      pushTagDeletion(tagType, tagName, Status.OK);
    }

    removePushFromRefsTags();
    allowTagDeletion();
    for (TagType tagType : TagType.values()) {
      String tagName = pushTagForExistingCommit(tagType, Status.OK);
      pushTagDeletion(tagType, tagName, Status.OK);
    }
  }

  private String pushTagForExistingCommit(TagType tagType, Status expectedStatus) throws Exception {
    return pushTag(tagType, null, false, false, expectedStatus);
  }

  private String pushTagForNewCommit(TagType tagType, Status expectedStatus) throws Exception {
    return pushTag(tagType, null, true, false, expectedStatus);
  }

  private void fastForwardTagToExistingCommit(
      TagType tagType, String tagName, Status expectedStatus) throws Exception {
    pushTag(tagType, tagName, false, false, expectedStatus);
  }

  private void fastForwardTagToNewCommit(TagType tagType, String tagName, Status expectedStatus)
      throws Exception {
    pushTag(tagType, tagName, true, false, expectedStatus);
  }

  private void forceUpdateTagToExistingCommit(
      TagType tagType, String tagName, Status expectedStatus) throws Exception {
    pushTag(tagType, tagName, false, true, expectedStatus);
  }

  private void forceUpdateTagToNewCommit(TagType tagType, String tagName, Status expectedStatus)
      throws Exception {
    pushTag(tagType, tagName, true, true, expectedStatus);
  }

  private String pushTag(
      TagType tagType, String tagName, boolean newCommit, boolean force, Status expectedStatus)
      throws Exception {
    if (force) {
      testRepo.reset(initialHead);
    }
    commit(user.getIdent(), "subject");

    boolean createTag = tagName == null;
    tagName = MoreObjects.firstNonNull(tagName, "v1" + "_" + System.nanoTime());
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
      grant(Permission.SUBMIT, project, "refs/for/refs/heads/master", false, REGISTERED_USERS);
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

  private void pushTagDeletion(TagType tagType, String tagName, Status expectedStatus)
      throws Exception {
    String tagRef = tagRef(tagName);
    PushResult r = deleteRef(testRepo, tagRef);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(tagRef);
    assertThat(refUpdate.getStatus()).named(tagType.name()).isEqualTo(expectedStatus);
  }

  private void allowTagCreation(TagType tagType) throws Exception {
    grant(tagType.createPermission, project, "refs/tags/*", false, REGISTERED_USERS);
  }

  private void allowPushOnRefsTags() throws Exception {
    removePushFromRefsTags();
    grant(Permission.PUSH, project, "refs/tags/*", false, REGISTERED_USERS);
  }

  private void allowForcePushOnRefsTags() throws Exception {
    removePushFromRefsTags();
    grant(Permission.PUSH, project, "refs/tags/*", true, REGISTERED_USERS);
  }

  private void allowTagDeletion() throws Exception {
    removePushFromRefsTags();
    grant(Permission.DELETE, project, "refs/tags/*", true, REGISTERED_USERS);
  }

  private void removePushFromRefsTags() throws Exception {
    removePermission(Permission.PUSH, project, "refs/tags/*");
  }

  private void commit(PersonIdent ident, String subject) throws Exception {
    commitBuilder().ident(ident).message(subject + " (" + System.nanoTime() + ")").create();
  }

  private static String tagRef(String tagName) {
    return RefNames.REFS_TAGS + tagName;
  }
}
