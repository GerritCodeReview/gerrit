// Copyright (C) 2020 The Android Open Source Project
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
import static org.apache.http.HttpStatus.SC_OK;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import java.util.regex.Pattern;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.junit.Test;

public class RestApiServletIT extends AbstractDaemonTest {
  private static String ANY_REST_API = "/accounts/self/capabilities";
  private static Pattern ANY_SPACE = Pattern.compile("\\s");

  @Test
  public void restResponseBodyShouldBeCompactWithoutSpaces() throws Exception {
    RestResponse response = adminRestSession.getWithHeader(ANY_REST_API, acceptStarHeader());
    assertThat(response.getStatusCode()).isEqualTo(SC_OK);

    assertThat(response.getEntityContent()).doesNotContainMatch(ANY_SPACE);
  }

  @Test
  public void restResponseBodyShouldBePrettyfiedWhenPPIsOne() throws Exception {

    RestResponse response =
        adminRestSession.getWithHeader(ANY_REST_API + "?pp=1", acceptStarHeader());
    assertThat(response.getStatusCode()).isEqualTo(SC_OK);

    assertThat(response.getEntityContent()).containsMatch(ANY_SPACE);
  }

  private Header acceptStarHeader() {
    return new BasicHeader("Accept", "*/*");
  }
}
