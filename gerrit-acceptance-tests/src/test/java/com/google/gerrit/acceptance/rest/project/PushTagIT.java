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
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.base.MoreObjects;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.RefNames;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class PushTagIT extends AbstractDaemonTest {
  private enum TagType {
    LIGHTWEIGHT(Permission.CREATE),
    ANNOTATED(Permission.PUSH_TAG);

    final String createPermission;

    TagType(String createPermission) {
      this.createPermission = createPermission;
    }
  }

  @Before
  public void setup() throws Exception {
    // clone with user to avoid inherited tag permissions of admin user
    testRepo = cloneProject(project, user);
  }

  @Test
  public void create() throws Exception {
    for (TagType tagType : TagType.values()) {
      pushTagForExistingCommit(tagType, Status.REJECTED_OTHER_REASON);

      allowTagCreation(tagType);
      pushTagForExistingCommit(tagType, Status.OK);

      pushTagForNewCommit(tagType, Status.REJECTED_OTHER_REASON);
    }

    allowPushOnRefsTags();
    for (TagType tagType : TagType.values()) {
      pushTagForNewCommit(tagType, Status.OK);
    }
  }

  @Test
  public void fastForward() throws Exception {
    for (TagType tagType : TagType.values()) {
      allowTagCreation(tagType);
      String tagName = pushTagForExistingCommit(tagType, Status.OK);

      fastForwardTagToExistingCommit(tagType, tagName,
          Status.REJECTED_OTHER_REASON);
      fastForwardTagToNewCommit(tagType, tagName, Status.REJECTED_OTHER_REASON);

      allowPushOnRefsTags();
      if (TagType.ANNOTATED.equals(tagType)) {
        fastForwardTagToExistingCommit(tagType, tagName,
            Status.REJECTED_OTHER_REASON);
        fastForwardTagToNewCommit(tagType, tagName,
            Status.REJECTED_OTHER_REASON);
      } else {
        fastForwardTagToExistingCommit(tagType, tagName, Status.OK);
        fastForwardTagToNewCommit(tagType, tagName, Status.OK);
      }

      allowForcePushOnRefsTags();
      fastForwardTagToExistingCommit(tagType, tagName, Status.OK);
      fastForwardTagToNewCommit(tagType, tagName, Status.OK);

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
  }

  private String pushTagForExistingCommit(TagType tagType,
      Status expectedStatus) throws Exception {
    return pushTag(tagType, null, false, expectedStatus);
  }

  private String pushTagForNewCommit(TagType tagType,
      Status expectedStatus) throws Exception {
    return pushTag(tagType, null, true, expectedStatus);
  }

  private void fastForwardTagToExistingCommit(TagType tagType, String tagName,
      Status expectedStatus) throws Exception {
    pushTag(tagType, tagName, false, expectedStatus);
  }

  private void fastForwardTagToNewCommit(TagType tagType, String tagName,
      Status expectedStatus) throws Exception {
    pushTag(tagType, tagName, true, expectedStatus);
  }

  private String pushTag(TagType tagType, String tagName, boolean newCommit,
      Status expectedStatus) throws Exception {
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
      grant(Permission.SUBMIT, project, "refs/for/refs/heads/master", false,
          REGISTERED_USERS);
      pushHead(testRepo, "refs/for/master%submit");
    }

    String tagRef = tagRef(tagName);
    PushResult r = tagType == TagType.LIGHTWEIGHT
        ? pushHead(testRepo, tagRef)
        : GitUtil.pushTag(testRepo, tagName, !createTag);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(tagRef);
    assertThat(refUpdate.getStatus())
        .named(tagType.name())
        .isEqualTo(expectedStatus);
    return tagName;
  }

  private void pushTagDeletion(TagType tagType, String tagName,
      Status expectedStatus) throws Exception {
    String tagRef = tagRef(tagName);
    PushResult r = deleteRef(testRepo, tagRef);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(tagRef);
    assertThat(refUpdate.getStatus()).named(tagType.name())
        .isEqualTo(expectedStatus);
  }

  private void allowTagCreation(TagType tagType) throws Exception {
    grant(tagType.createPermission, project, "refs/tags/*", false,
        REGISTERED_USERS);
  }

  private void allowPushOnRefsTags() throws Exception {
    removePushFromRefsTags();
    grant(Permission.PUSH, project, "refs/tags/*", false, REGISTERED_USERS);
  }

  private void allowForcePushOnRefsTags() throws Exception {
    removePushFromRefsTags();
    grant(Permission.PUSH, project, "refs/tags/*", true, REGISTERED_USERS);
  }

  private void removePushFromRefsTags() throws Exception {
    removePermission(Permission.PUSH, project, "refs/tags/*");
  }

  private void commit(PersonIdent ident, String subject) throws Exception {
    commitBuilder()
        .ident(ident)
        .message(subject)
        .create();
  }

  private static String tagRef(String tagName) {
    return RefNames.REFS_TAGS + tagName;
  }
}
