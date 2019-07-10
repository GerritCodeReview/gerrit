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

package com.google.gerrit.acceptance.rest;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.change.ChangeResource.CHANGE_KIND;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_OK;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.util.cli.UnknownOptionHandler;
import com.google.inject.Module;
import java.util.Optional;
import org.junit.Test;

public class UnknownOptionIT extends AbstractDaemonTest {
  @Override
  public Module createModule() {
    return new RestApiModule() {
      @Override
      protected void configure() {
        get(CHANGE_KIND, "test").to(MyChangeView.class);
      }
    };
  }

  @Test
  public void unknownOptionIsRejectedIfRestEndpointDoesNotHandleUnknownOptions() throws Exception {
    RestResponse response = adminRestSession.get("/accounts/self/detail?foo-bar");
    assertThat(response.getStatusCode()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  public void unknownOptionIsIgnoredIfRestEndpointAcceptsIt() throws Exception {
    String changeId = createChange().getChangeId();
    RestResponse response = adminRestSession.get("/changes/" + changeId + "/test?ignore-foo");
    assertThat(response.getStatusCode()).isEqualTo(SC_OK);
  }

  @Test
  public void unknownOptionCausesFailureIfRestEndpointDoesNotAcceptIt() throws Exception {
    String changeId = createChange().getChangeId();
    RestResponse response = adminRestSession.get("/changes/" + changeId + "/test?foo-bar");
    assertThat(response.getStatusCode()).isEqualTo(SC_BAD_REQUEST);
  }

  private static class MyChangeView implements RestReadView<ChangeResource>, UnknownOptionHandler {
    @Override
    public Response<String> apply(ChangeResource resource) {
      return Response.ok("OK");
    }

    @Override
    public boolean accept(String name, Optional<String> value) {
      return name.startsWith("--ignore");
    }
  }
}
