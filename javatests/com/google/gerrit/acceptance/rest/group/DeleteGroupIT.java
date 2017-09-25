// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.group;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.group.SystemGroupBackend;
import org.junit.Ignore;
import org.junit.Test;

public class DeleteGroupIT extends AbstractDaemonTest {
  @Test
  public void nonAdminCannotDeleteGroup() throws Exception {
    String g = createGroup("test", true);
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("cannot delete group");
    gApi.groups().id(g).delete();
  }

  @Test
  public void cannotDeleteGroupThatOwnsOtherGroup() throws Exception {
    String parent = createGroup("parent");
    createGroup("child", parent);
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("cannot delete group that is owner of other groups");
    gApi.groups().id(parent).delete();
  }

  @Test
  public void cannotDeleteSystemGroup() throws Exception {
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("cannot delete system group");
    gApi.groups().id(SystemGroupBackend.REGISTERED_USERS.get()).delete();
  }

  @Test
  @Ignore
  public void cannotDeleteExternalGroup() throws Exception {
    // TODO this is handled in DeleteGroup but how do we create an external
    // group in tests?
  }

  @Test
  @Ignore
  public void cannotDeleteGroupUsedInRefPermissions() throws Exception {
    // TODO should it be possible to delete a group if it is used
    // to set permissions?
  }

  @Test
  public void deleteGroupNotImplementedYet() throws Exception {
    String g = createGroup("test");
    exception.expect(NotImplementedException.class);
    gApi.groups().id(g).delete();
  }
}
