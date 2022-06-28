// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import org.junit.Test;

public class ApplyPatchIT extends AbstractDaemonTest {

  private final static String COMMIT_MESSAGE = "change to apply the patch in";

  @Test
  public void exampleApplyPatchUsage() throws Exception {
    ApplyPatchInput in = new ApplyPatchInput();
    in.patch = "Patch compatible with `git diff` output.";
    ChangeApi changeApi = gApi.changes()
        .create(new ChangeInput(project.get(), "master", COMMIT_MESSAGE));

    NotImplementedException exception =
        assertThrows(NotImplementedException.class, () -> changeApi.applyPatch(in));

    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("ApplyPatch is not yet implemented.");
  }
}
