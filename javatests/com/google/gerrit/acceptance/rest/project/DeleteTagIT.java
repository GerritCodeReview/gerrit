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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.projects.TagApi;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

public class DeleteTagIT extends AbstractDaemonTest {
  private static final String TAG = "refs/tags/test";

  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Before
  public void setUp() throws Exception {
    tag().create(new TagInput());
  }

  @Test
  public void deleteTag_Forbidden() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    assertDeleteForbidden();
  }

  @Test
  public void deleteTagByAdmin() throws Exception {
    assertDeleteSucceeds();
  }

  @Test
  public void deleteTagByProjectOwner() throws Exception {
    grantOwner();
    requestScopeOperations.setApiUser(user.id());
    assertDeleteSucceeds();
  }

  @Test
  public void deleteTagByAdminForcePushBlocked() throws Exception {
    blockForcePush();
    assertDeleteSucceeds();
  }

  @Test
  public void deleteTagByProjectOwnerForcePushBlocked_Forbidden() throws Exception {
    grantOwner();
    blockForcePush();
    requestScopeOperations.setApiUser(user.id());
    assertDeleteForbidden();
  }

  @Test
  public void deleteTagByUserWithForcePushPermission() throws Exception {
    grantForcePush();
    requestScopeOperations.setApiUser(user.id());
    assertDeleteSucceeds();
  }

  @Test
  public void deleteTagByUserWithDeletePermission() throws Exception {
    grantDelete();
    requestScopeOperations.setApiUser(user.id());
    assertDeleteSucceeds();
  }

  @Test
  public void deleteTagByRestWithoutRefsTagsPrefix() throws Exception {
    grantDelete();
    String ref = TAG.substring(R_TAGS.length());
    RestResponse r = userRestSession.delete("/projects/" + project.get() + "/tags/" + ref);
    r.assertNoContent();
  }

  private void blockForcePush() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.PUSH).ref("refs/tags/*").group(ANONYMOUS_USERS).force(true))
        .update();
  }

  private void grantForcePush() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/tags/*").group(ANONYMOUS_USERS).force(true))
        .update();
  }

  private void grantDelete() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.DELETE).ref("refs/tags/*").group(ANONYMOUS_USERS))
        .update();
  }

  private void grantOwner() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.OWNER).ref("refs/tags/*").group(REGISTERED_USERS))
        .update();
  }

  private TagApi tag() throws Exception {
    return gApi.projects().name(project.get()).tag(TAG);
  }

  private void assertDeleteSucceeds() throws Exception {
    TagInfo tagInfo = tag().get();
    assertThat(tagInfo.canDelete).isTrue();
    String tagRev = tagInfo.revision;
    tag().delete();
    eventRecorder.assertRefUpdatedEvents(project.get(), TAG, null, tagRev, tagRev, null);
    assertThrows(ResourceNotFoundException.class, () -> tag().get());
  }

  private void assertDeleteForbidden() throws Exception {
    assertThat(tag().get().canDelete).isNull();
    AuthException thrown = assertThrows(AuthException.class, () -> tag().delete());
    assertThat(thrown).hasMessageThat().contains("not permitted: delete");
  }
}
