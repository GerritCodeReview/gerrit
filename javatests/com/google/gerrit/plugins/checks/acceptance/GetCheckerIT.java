// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks.acceptance;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.api.CheckerInfo;
import com.google.inject.Inject;
import org.junit.Test;

public class GetCheckerIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void getChecker() throws Exception {
    String name = "my-checker";
    String uuid = checkerOperations.newChecker().name(name).create();

    CheckerInfo info = checkersApi.id(uuid).get();
    assertThat(info.uuid).isEqualTo(uuid);
    assertThat(info.name).isEqualTo(name);
    assertThat(info.description).isNull();
    assertThat(info.createdOn).isNotNull();
  }

  @Test
  public void getCheckerWithDescription() throws Exception {
    String name = "my-checker";
    String description = "some description";
    String uuid = checkerOperations.newChecker().name(name).description(description).create();

    CheckerInfo info = checkersApi.id(uuid).get();
    assertThat(info.uuid).isEqualTo(uuid);
    assertThat(info.name).isEqualTo(name);
    assertThat(info.description).isEqualTo(description);
    assertThat(info.createdOn).isNotNull();
  }

  @Test
  public void getNonExistingCheckerFails() throws Exception {
    String checkerUuid = CheckerUuid.make("non-existing");

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + checkerUuid);
    checkersApi.id(checkerUuid);
  }

  @Test
  public void getCheckerByNameFails() throws Exception {
    String name = "my-checker";
    checkerOperations.newChecker().name(name).create();

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + name);
    checkersApi.id(name);
  }

  @Test
  public void getCheckerWithoutAdministrateCheckersCapabilityFails() throws Exception {
    String name = "my-checker";
    String uuid = checkerOperations.newChecker().name(name).create();

    requestScopeOperations.setApiUser(user.getId());

    exception.expect(AuthException.class);
    exception.expectMessage("administrateCheckers for plugin checks not permitted");
    checkersApi.id(uuid);
  }
}
