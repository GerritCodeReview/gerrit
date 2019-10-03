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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.GitUtil.createAnnotatedTag;
import static com.google.gerrit.acceptance.GitUtil.deleteRef;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.GitUtil.updateAnnotatedTag;
import static com.google.gerrit.acceptance.rest.project.AbstractPushTag.TagType.ANNOTATED;
import static com.google.gerrit.acceptance.rest.project.AbstractPushTag.TagType.LIGHTWEIGHT;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.base.MoreObjects;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public abstract class AbstractPushTag extends AbstractDaemonTest {
  enum TagType {
    LIGHTWEIGHT(Permission.CREATE),
    ANNOTATED(Permission.CREATE_TAG);

    final String createPermission;

    TagType(String createPermission) {
      this.createPermission = createPermission;
    }
  }

  @Inject private ProjectOperations projectOperations;

  private RevCommit initialHead;
  private TagType tagType;

  @Before
  public void setUpTestEnvironment() throws Exception {
    // clone with user to avoid inherited tag permissions of admin user
    testRepo = cloneProject(project, user);

    initialHead = projectOperations.project(project).getHead("master");
    tagType = getTagType();
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
    commit(user.newIdent(), "subject");

    boolean createTag = tagName == null;
    tagName = MoreObjects.firstNonNull(tagName, "v1_" + System.nanoTime());
    switch (tagType) {
      case LIGHTWEIGHT:
        break;
      case ANNOTATED:
        if (createTag) {
          createAnnotatedTag(testRepo, tagName, user.newIdent());
        } else {
          updateAnnotatedTag(testRepo, tagName, user.newIdent());
        }
        break;
      default:
        throw new IllegalStateException("unexpected tag type: " + tagType);
    }

    if (!newCommit) {
      projectOperations
          .project(project)
          .forUpdate()
          .add(allow(Permission.SUBMIT).ref("refs/for/refs/heads/master").group(REGISTERED_USERS))
          .update();
      pushHead(testRepo, "refs/for/master%submit");
    }

    String tagRef = tagRef(tagName);
    PushResult r =
        tagType == LIGHTWEIGHT
            ? pushHead(testRepo, tagRef, false, force)
            : GitUtil.pushTag(testRepo, tagName, !createTag);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(tagRef);
    assertWithMessage(tagType.name()).that(refUpdate.getStatus()).isEqualTo(expectedStatus);
    return tagName;
  }

  private void pushTagDeletion(String tagName, Status expectedStatus) throws Exception {
    String tagRef = tagRef(tagName);
    PushResult r = deleteRef(testRepo, tagRef);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(tagRef);
    assertWithMessage(tagType.name()).that(refUpdate.getStatus()).isEqualTo(expectedStatus);
  }

  private void allowTagCreation() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(tagType.createPermission).ref("refs/tags/*").group(REGISTERED_USERS))
        .update();
  }

  private void allowPushOnRefsTags() throws Exception {
    removePushFromRefsTags();
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/tags/*").group(REGISTERED_USERS))
        .update();
  }

  private void allowForcePushOnRefsTags() throws Exception {
    removePushFromRefsTags();
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/tags/*").group(REGISTERED_USERS).force(true))
        .update();
  }

  private void allowTagDeletion() throws Exception {
    removePushFromRefsTags();
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.DELETE).ref("refs/tags/*").group(REGISTERED_USERS).force(true))
        .update();
  }

  private void removePushFromRefsTags() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .remove(permissionKey(Permission.PUSH).ref("refs/tags/*"))
        .update();
  }

  private void commit(PersonIdent ident, String subject) throws Exception {
    commitBuilder().ident(ident).message(subject + " (" + System.nanoTime() + ")").create();
  }

  private static String tagRef(String tagName) {
    return RefNames.REFS_TAGS + tagName;
  }
}
