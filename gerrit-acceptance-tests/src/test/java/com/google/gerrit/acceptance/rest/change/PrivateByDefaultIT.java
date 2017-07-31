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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import org.junit.Test;

public class PrivateByDefaultIT extends AbstractDaemonTest {
  @Test
  @GerritConfig(name = "change.privateByDefault", value = "true")
  public void createChangeWithPrivateByDefaultEnabled() throws Exception {
    ChangeInput input = new ChangeInput(project.get(), "master", "empty change");
    assertThat(gApi.changes().create(input).get().isPrivate).isEqualTo(true);
  }

  @Test
  @GerritConfig(name = "change.privateByDefault", value = "true")
  public void createChangeBypassPrivateByDefaultEnabled() throws Exception {
    ChangeInput input = new ChangeInput(project.get(), "master", "empty change");
    input.isPrivate = false;
    assertThat(gApi.changes().create(input).get().isPrivate).isNull();
  }

  @Test
  public void createChangeWithPrivateByDefaultDisabled() throws Exception {
    ChangeInfo info =
        gApi.changes().create(new ChangeInput(project.get(), "master", "empty change")).get();
    assertThat(info.isPrivate).isNull();
  }

  @Test
  @GerritConfig(name = "change.privateByDefault", value = "true")
  public void pushWithPrivateByDefaultEnabled() throws Exception {
    assertThat(createChange().getChange().change().isPrivate()).isEqualTo(true);
  }

  @Test
  @GerritConfig(name = "change.privateByDefault", value = "true")
  public void pushBypassPrivateByDefaultEnabled() throws Exception {
    assertThat(createChange("refs/for/master%remove-private").getChange().change().isPrivate())
        .isEqualTo(false);
  }

  @Test
  public void pushWithPrivateByDefaultDisabled() throws Exception {
    assertThat(createChange().getChange().change().isPrivate()).isEqualTo(false);
  }
}
