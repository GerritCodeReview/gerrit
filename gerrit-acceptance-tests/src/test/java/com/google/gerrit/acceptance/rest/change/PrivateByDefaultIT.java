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
import static com.google.common.truth.TruthJUnit.assume;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.testutil.ConfigSuite;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class PrivateByDefaultIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config privateByDefaultEnabled() {
    return privateByDefaultEnabledConfig();
  }

  @Test
  public void createChangeWithPrivateByDefaultEnabled() throws Exception {
    assume().that(isPrivateByDefault()).isTrue();
    ChangeInfo info =
        gApi.changes().create(new ChangeInput(project.get(), "master", "empty change")).get();
    assertThat(info.isPrivate).isEqualTo(true);
  }

  @Test
  public void createChangeWithPrivateByDefaultDisabled() throws Exception {
    assume().that(isPrivateByDefault()).isFalse();
    ChangeInfo info =
        gApi.changes().create(new ChangeInput(project.get(), "master", "empty change")).get();
    assertThat(info.isPrivate).isNull();
  }

  @Test
  public void pushWithPrivateByDefaultEnabled() throws Exception {
    assume().that(isPrivateByDefault()).isTrue();
    assertThat(createChange().getChange().change().isPrivate()).isEqualTo(true);
  }

  @Test
  public void pushWithPrivateByDefaultDisabled() throws Exception {
    assume().that(isPrivateByDefault()).isFalse();
    assertThat(createChange().getChange().change().isPrivate()).isEqualTo(false);
  }
}
