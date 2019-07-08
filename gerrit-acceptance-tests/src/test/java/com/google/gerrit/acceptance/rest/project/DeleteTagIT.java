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

import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.TagApi;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import org.junit.Before;
import org.junit.Test;

public class DeleteTagIT extends AbstractDaemonTest {
  private static final String TAG = "refs/tags/test";

  @Before
  public void setUp() throws Exception {
    tag().create(new TagInput());
  }

  @Test
  public void deleteTag_Forbidden() throws Exception {
    setApiUser(user);
    assertDeleteForbidden();
  }

  @Test
  public void deleteTagByAdmin() throws Exception {
    assertDeleteSucceeds();
  }

  @Test
  public void deleteTagByProjectOwner() throws Exception {
    grantOwner();
    setApiUser(user);
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
    setApiUser(user);
    assertDeleteForbidden();
  }

  @Test
  public void deleteTagByUserWithForcePushPermission() throws Exception {
    grantForcePush();
    setApiUser(user);
    assertDeleteSucceeds();
  }

  @Test
  public void deleteTagByUserWithDeletePermission() throws Exception {
    grantDelete();
    setApiUser(user);
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
    block(Permission.PUSH, ANONYMOUS_USERS, "refs/tags/*").setForce(true);
  }

  private void grantForcePush() throws Exception {
    grant(Permission.PUSH, project, "refs/tags/*", true, ANONYMOUS_USERS);
  }

  private void grantDelete() throws Exception {
    allow(Permission.DELETE, ANONYMOUS_USERS, "refs/tags/*");
  }

  private void grantOwner() throws Exception {
    allow(Permission.OWNER, REGISTERED_USERS, "refs/tags/*");
  }

  private TagApi tag() throws Exception {
    return gApi.projects().name(project.get()).tag(TAG);
  }

  private void assertDeleteSucceeds() throws Exception {
    String tagRev = tag().get().revision;
    tag().delete();
    eventRecorder.assertRefUpdatedEvents(project.get(), TAG, null, tagRev, tagRev, null);
    exception.expect(ResourceNotFoundException.class);
    tag().get();
  }

  private void assertDeleteForbidden() throws Exception {
    exception.expect(AuthException.class);
    exception.expectMessage("Cannot delete tag");
    tag().delete();
  }
}
