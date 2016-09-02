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
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.common.data.Permission;

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

    allowPushOfTagsForNewCommits();
    for (TagType tagType : TagType.values()) {
      pushTagForNewCommit(tagType, Status.OK);
    }
  }

  @Test
  public void delete() throws Exception {
    for (TagType tagType : TagType.values()) {
      allowTagCreation(tagType);
      String tagRef = pushTagForExistingCommit(tagType, Status.OK);

      pushTagDeletion(tagType, tagRef, Status.REJECTED_OTHER_REASON);

      allowFastForwardTagUpdate();
      pushTagDeletion(tagType, tagRef, Status.REJECTED_OTHER_REASON);
    }

    allowTagDeletion();
    for (TagType tagType : TagType.values()) {
      String tagRef = pushTagForExistingCommit(tagType, Status.OK);
      pushTagDeletion(tagType, tagRef, Status.OK);
    }
  }

  private String pushTagForExistingCommit(TagType tagType,
      Status expectedStatus) throws Exception {
    return pushTag(tagType, false, expectedStatus);
  }

  private String pushTagForNewCommit(TagType tagType,
      Status expectedStatus) throws Exception {
    return pushTag(tagType, true, expectedStatus);
  }

  private String pushTag(TagType tagType, boolean newCommit,
      Status expectedStatus) throws Exception {
    commit(user.getIdent(), "subject");

    String tagName = "v1" + "_" + System.nanoTime();
    String tagRef = "refs/tags/" + tagName;
    switch (tagType) {
      case LIGHTWEIGHT:
        break;
      case ANNOTATED:
        createAnnotatedTag(testRepo, tagName, user.getIdent());
        break;
      default:
        throw new IllegalStateException("unexpected tag type: " + tagType);
    }

    if (!newCommit) {
      grant(Permission.SUBMIT, project, "refs/for/refs/heads/master", false,
          REGISTERED_USERS);
      pushHead(testRepo, "refs/for/master%submit");
    }

    PushResult r = tagType == TagType.LIGHTWEIGHT
        ? pushHead(testRepo, tagRef)
        : GitUtil.pushTag(testRepo, tagName);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(tagRef);
    assertThat(refUpdate.getStatus())
        .named(tagType.name())
        .isEqualTo(expectedStatus);
    return tagRef;
  }

  private void pushTagDeletion(TagType tagType, String tagRef,
      Status expectedStatus) throws Exception {
    PushResult r = deleteRef(testRepo, tagRef);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(tagRef);
    assertThat(refUpdate.getStatus()).named(tagType.name())
        .isEqualTo(expectedStatus);
  }

  private void allowTagCreation(TagType tagType) throws Exception {
    grant(tagType.createPermission, project, "refs/tags/*", false,
        REGISTERED_USERS);
  }

  private void allowPushOfTagsForNewCommits() throws Exception {
    grant(Permission.PUSH, project, "refs/tags/*", false, REGISTERED_USERS);
  }

  private void allowFastForwardTagUpdate() throws Exception {
    grant(Permission.PUSH, project, "refs/tags/*", false, REGISTERED_USERS);
  }

  private void allowTagDeletion() throws Exception {
    removePermission(Permission.PUSH, project, "refs/tags/*");
    grant(Permission.PUSH, project, "refs/tags/*", true, REGISTERED_USERS);
  }

  private void commit(PersonIdent ident, String subject) throws Exception {
    commitBuilder()
        .ident(ident)
        .message(subject)
        .create();
  }
}
