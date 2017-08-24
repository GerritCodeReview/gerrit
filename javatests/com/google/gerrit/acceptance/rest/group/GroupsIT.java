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
// limitations under the License

package com.google.gerrit.acceptance.rest.group;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import org.junit.Test;

public class GroupsIT extends AbstractDaemonTest {
  @Test
  public void invalidQueryOptions() throws Exception {
    RestResponse r = adminRestSession.put("/groups/?query=foo&query2=bar");
    r.assertBadRequest();
    assertThat(r.getEntityContent())
        .isEqualTo("\"query\" and \"query2\" options are mutually exclusive");
  }
}
