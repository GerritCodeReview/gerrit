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
import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import org.apache.http.client.fluent.Request;
import org.junit.Test;

public class MethodNotAllowedIT extends AbstractDaemonTest {
  @Test
  public void unsupportedPostOnRootCollection() throws Exception {
    RestResponse response = adminRestSession.post("/accounts/");
    assertThat(response.getStatusCode()).isEqualTo(SC_METHOD_NOT_ALLOWED);
    assertThat(response.getEntityContent()).isEqualTo("Not implemented: POST /accounts/");
  }

  @Test
  public void unsupportedPostOnChildCollection() throws Exception {
    RestResponse response = adminRestSession.post("/accounts/self/emails");
    assertThat(response.getStatusCode()).isEqualTo(SC_METHOD_NOT_ALLOWED);
    assertThat(response.getEntityContent())
        .isEqualTo("Not implemented: POST /accounts/self/emails");
  }

  @Test
  public void unsupportedDeleteOnRootCollection() throws Exception {
    RestResponse response = adminRestSession.delete("/accounts/");
    assertThat(response.getStatusCode()).isEqualTo(SC_METHOD_NOT_ALLOWED);
    assertThat(response.getEntityContent()).isEqualTo("Not implemented: DELETE /accounts/");
  }

  @Test
  public void unsupportedDeleteOnChildCollection() throws Exception {
    RestResponse response = adminRestSession.delete("/accounts/self/emails");
    assertThat(response.getStatusCode()).isEqualTo(SC_METHOD_NOT_ALLOWED);
    assertThat(response.getEntityContent())
        .isEqualTo("Not implemented: DELETE /accounts/self/emails");
  }

  @Test
  public void unsupportedHttpMethodOnRootCollection() throws Exception {
    Request patch = Request.Patch(adminRestSession.url() + "/a/accounts/");
    RestResponse response = adminRestSession.execute(patch);
    assertThat(response.getStatusCode()).isEqualTo(SC_METHOD_NOT_ALLOWED);
    assertThat(response.getEntityContent()).isEqualTo("Not implemented: PATCH /accounts/");
  }

  @Test
  public void unsupportedHttpMethodOnChildCollection() throws Exception {
    Request patch = Request.Patch(adminRestSession.url() + "/a/accounts/self/emails");
    RestResponse response = adminRestSession.execute(patch);
    assertThat(response.getStatusCode()).isEqualTo(SC_METHOD_NOT_ALLOWED);
    assertThat(response.getEntityContent())
        .isEqualTo("Not implemented: PATCH /accounts/self/emails");
  }
}
