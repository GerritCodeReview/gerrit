// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest;

import static com.google.common.truth.Truth.assertThat;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import org.junit.Test;

public class NotFoundIT extends AbstractDaemonTest {
  @Test
  public void nonExistingRootCollection() throws Exception {
    RestResponse response = adminRestSession.get("/non-existing/");
    assertThat(response.getStatusCode()).isEqualTo(SC_NOT_FOUND);
    assertThat(response.getEntityContent()).isEqualTo("Not Found");

    response = adminRestSession.post("/non-existing/");
    assertThat(response.getStatusCode()).isEqualTo(SC_NOT_FOUND);
    assertThat(response.getEntityContent()).isEqualTo("Not Found");
  }

  @Test
  public void nonExistingView() throws Exception {
    RestResponse response = adminRestSession.get("/accounts/self/non-existing");
    assertThat(response.getStatusCode()).isEqualTo(SC_NOT_FOUND);
    assertThat(response.getEntityContent()).isEqualTo("Not found: non-existing");

    response = adminRestSession.post("/accounts/self/non-existing");
    assertThat(response.getStatusCode()).isEqualTo(SC_NOT_FOUND);
    assertThat(response.getEntityContent()).isEqualTo("Not found: non-existing");
  }
}
