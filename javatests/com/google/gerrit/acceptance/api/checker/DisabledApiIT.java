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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.checker.CheckerOperations;
import com.google.gerrit.extensions.api.checkers.CheckerInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.server.checker.CheckerUuid;
import com.google.inject.Inject;
import org.junit.Test;

/** By default the checker API is disabled. This test verifies this. */
public class DisabledApiIT extends AbstractDaemonTest {
  @Inject private CheckerOperations checkerOperations;

  @Test
  public void getChecker() throws Exception {
    String uuid = checkerOperations.newChecker().create();

    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("checker API is disabled");
    gApi.checkers().id(uuid).get();
  }

  @Test
  public void getNonExistingChecker() throws Exception {
    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("checker API is disabled");
    gApi.checkers().id(CheckerUuid.make("non-existing")).get();
  }

  @Test
  public void createChecker() throws Exception {
    CheckerInput input = new CheckerInput();
    input.name = "my-checker";

    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("checker API is disabled");
    gApi.checkers().create(input);
  }
}
