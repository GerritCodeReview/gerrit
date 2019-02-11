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

package com.google.gerrit.acceptance.api.checker;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.SkipProjectClone;
import com.google.gerrit.acceptance.testsuite.checker.CheckerOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.checkers.CheckerInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.checker.CheckerUuid;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

@NoHttpd
@SkipProjectClone
public class GetCheckerIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private CheckerOperations checkerOperations;

  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setBoolean("checks", "api", "enabled", true);
    return cfg;
  }

  @Test
  public void getChecker() throws Exception {
    String name = "my-checker";
    String uuid = checkerOperations.newChecker().name(name).create();

    CheckerInfo info = gApi.checkers().id(uuid).get();
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

    CheckerInfo info = gApi.checkers().id(uuid).get();
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
    gApi.checkers().id(checkerUuid);
  }

  @Test
  public void getCheckerByNameFails() throws Exception {
    String name = "my-checker";
    checkerOperations.newChecker().name(name).create();

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + name);
    gApi.checkers().id(name);
  }

  @Test
  public void getCheckerWithoutAdministrateCheckersCapabilityFails() throws Exception {
    String name = "my-checker";
    String uuid = checkerOperations.newChecker().name(name).create();

    requestScopeOperations.setApiUser(user.getId());

    exception.expect(AuthException.class);
    exception.expectMessage("administrate checkers not permitted");
    gApi.checkers().id(uuid);
  }
}
