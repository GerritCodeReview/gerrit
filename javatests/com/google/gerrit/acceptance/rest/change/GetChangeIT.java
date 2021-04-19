// Copyright (C) 2021 The Android Open Source Project
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
import com.google.gerrit.acceptance.RestResponse;
import org.junit.Test;

public class GetChangeIT extends AbstractDaemonTest {
  @Test
  public void cannotGetCodeOwnersWithUnknownChangeOption() throws Exception {
    String changeId = createChange().getChangeId();
    RestResponse r =
        adminRestSession.get(String.format("/changes/%s/detail?o=unknown-option", changeId));
    r.assertBadRequest();
    assertThat(r.getEntityContent())
        .isEqualTo("\"unknown_option\" is not a valid value for \"-o\"");
  }

  @Test
  public void cannotGetCodeOwnersWithUnknownHexChangeOption() throws Exception {
    String changeId = createChange().getChangeId();
    String unknownHexOption = Integer.toHexString(100);
    RestResponse r =
        adminRestSession.get(String.format("/changes/%s/detail?O=%s", changeId, unknownHexOption));
    r.assertBadRequest();
    assertThat(r.getEntityContent())
        .isEqualTo(String.format("\"%s\" is not a valid value for \"-O\"", unknownHexOption));
  }

  @Test
  public void cannotGetCodeOwnersWithInvalidHexChangeOption() throws Exception {
    String changeId = createChange().getChangeId();
    String invalidHexOption = "invalid";
    RestResponse r =
        adminRestSession.get(String.format("/changes/%s/detail?O=%s", changeId, invalidHexOption));
    r.assertBadRequest();
    assertThat(r.getEntityContent())
        .isEqualTo(String.format("\"%s\" is not a valid value for \"-O\"", invalidHexOption));
  }
}
