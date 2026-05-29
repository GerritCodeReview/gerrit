// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.binding;

import static com.google.gerrit.acceptance.rest.util.RestCall.Method.GET;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_OK;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.server.group.SystemGroupBackend;
import org.junit.Test;

public class ListProjectOptionsRestApiBindingsIT extends AbstractDaemonTest {
  private static final ImmutableList<RestCall> LIST_PROJECTS_WITH_OPTIONS =
      ImmutableList.of(
          // =========================
          // === Supported options ===
          // =========================
          get200OK("/projects/?show-branch=refs/heads/master"),
          get200OK("/projects/?b=refs/heads/master"),
          get200OK("/projects/?format=TEXT"),
          get200OK("/projects/?format=JSON"),
          get200OK("/projects/?format=JSON_COMPACT"),
          get200OK("/projects/?tree"),
          get200OK("/projects/?tree=true"),
          get200OK("/projects/?tree=false"),
          get200OK("/projects/?t"),
          get200OK("/projects/?t=true"),
          get200OK("/projects/?t=false"),
          get200OK("/projects/?type=ALL"),
          get200OK("/projects/?type=CODE"),
          get200OK("/projects/?type=PERMISSIONS"),
          get200OK("/projects/?description"),
          get200OK("/projects/?description=true"),
          get200OK("/projects/?description=false"),
          get200OK("/projects/?d"),
          get200OK("/projects/?d=true"),
          get200OK("/projects/?d=false"),
          get200OK("/projects/?all"),
          get200OK("/projects/?all=true"),
          get200OK("/projects/?all=false"),
          get200OK("/projects/?state=ACTIVE"),
          get200OK("/projects/?state=READ_ONLY"),
          get200OK("/projects/?state=HIDDEN"),
          get200OK("/projects/?limit=10"),
          get200OK("/projects/?n=10"),
          get200OK("/projects/?start=10"),
          get200OK("/projects/?S=10"),
          get200OK("/projects/?prefix=my-prefix"),
          get200OK("/projects/?p=my-prefix"),
          get200OK("/projects/?match=my-match"),
          get200OK("/projects/?m=my-match"),
          get200OK("/projects/?r=my-regex"),
          get200OK("/projects/?has-acl-for=" + SystemGroupBackend.ANONYMOUS_USERS.get()),

          // ===========================
          // === Unsupported options ===
          // ===========================
          get400BadRequest("/projects/?unknown", "\"--unknown\" is not a valid option"),
          get400BadRequest("/projects/?unknown", "\"--unknown\" is not a valid option"),
          get400BadRequest(
              "/projects/?format=UNKNOWN", "\"UNKNOWN\" is not a valid value for \"--format\""),
          get400BadRequest("/projects/?tree=UNKNOWN", "invalid boolean \"tree=UNKNOWN\""),
          get400BadRequest("/projects/?t=UNKNOWN", "invalid boolean \"t=UNKNOWN\""),
          get400BadRequest(
              "/projects/?type=UNKNOWN", "\"UNKNOWN\" is not a valid value for \"--type\""),
          get400BadRequest(
              "/projects/?description=UNKNOWN", "invalid boolean \"description=UNKNOWN\""),
          get400BadRequest("/projects/?d=UNKNOWN", "invalid boolean \"d=UNKNOWN\""),
          get400BadRequest("/projects/?all=UNKNOWN", "invalid boolean \"all=UNKNOWN\""),
          get400BadRequest(
              "/projects/?state=UNKNOWN", "\"UNKNOWN\" is not a valid value for \"--state\""),
          get400BadRequest("/projects/?n=UNKNOWN", "\"UNKNOWN\" is not a valid value for \"-n\""),
          get400BadRequest(
              "/projects/?start=UNKNOWN", "\"UNKNOWN\" is not a valid value for \"--start\""),
          get400BadRequest("/projects/?S=UNKNOWN", "\"UNKNOWN\" is not a valid value for \"-S\""),
          get400BadRequest("/projects/?has-acl-for=UNKNOWN", "Group \"UNKNOWN\" does not exist"));

  private static RestCall get200OK(String uriFormat) {
    return RestCall.builder(GET, uriFormat).expectedResponseCode(SC_OK).build();
  }

  private static RestCall get400BadRequest(String uriFormat, String expectedMessage) {
    return RestCall.builder(GET, uriFormat)
        .expectedResponseCode(SC_BAD_REQUEST)
        .expectedMessage(expectedMessage)
        .build();
  }

  @Test
  public void listProjectsWithOptions() throws Exception {
    RestApiCallHelper.execute(adminRestSession, LIST_PROJECTS_WITH_OPTIONS);
  }
}
