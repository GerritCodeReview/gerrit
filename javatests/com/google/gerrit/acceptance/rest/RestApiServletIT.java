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
import com.google.gerrit.httpd.restapi.RestApiServlet;
import java.io.IOException;
import java.util.regex.Pattern;
import org.apache.http.message.BasicHeader;
import org.junit.Test;

public class RestApiServletIT extends AbstractDaemonTest {
  private static String ANY_REST_API = "/accounts/self/capabilities";
  private static BasicHeader ACCEPT_STAR_HEADER = new BasicHeader("Accept", "*/*");
  private static Pattern ANY_SPACE = Pattern.compile("\\s");

  @Test
  public void restResponseBodyShouldBeCompactWithoutSpaces() throws Exception {
    RestResponse response = adminRestSession.getWithHeader(ANY_REST_API, ACCEPT_STAR_HEADER);
    assertThat(response.getStatusCode()).isEqualTo(SC_OK);

    assertThat(contentWithoutMagicJson(response)).doesNotContainMatch(ANY_SPACE);
  }

  @Test
  public void restResponseBodyShouldBeCompactWithoutSpacesWhenPPIsZero() throws Exception {
    assertThat(contentWithoutMagicJson(prettyJsonRestResponse("prettyPrint", 0)))
        .doesNotContainMatch(ANY_SPACE);
  }

  @Test
  public void restResponseBodyShouldBeCompactWithoutSpacesWhenPrerryPrintIsZero() throws Exception {
    assertThat(contentWithoutMagicJson(prettyJsonRestResponse("pp", 0)))
        .doesNotContainMatch(ANY_SPACE);
  }

  @Test
  public void restResponseBodyShouldBePrettyfiedWhenPPIsOne() throws Exception {
    assertThat(contentWithoutMagicJson(prettyJsonRestResponse("pp", 1))).containsMatch(ANY_SPACE);
  }

  @Test
  public void restResponseBodyShouldBePrettyfiedWhenPrettyPrintIsOne() throws Exception {
    assertThat(contentWithoutMagicJson(prettyJsonRestResponse("prettyPrint", 1)))
        .containsMatch(ANY_SPACE);
  }

  private RestResponse prettyJsonRestResponse(String ppArgument, int ppValue) throws Exception {
    RestResponse response =
        adminRestSession.getWithHeader(
            ANY_REST_API + "?" + ppArgument + "=" + ppValue, ACCEPT_STAR_HEADER);
    assertThat(response.getStatusCode()).isEqualTo(SC_OK);

    return response;
  }

  private String contentWithoutMagicJson(RestResponse response) throws IOException {
    return response.getEntityContent().substring(RestApiServlet.JSON_MAGIC.length);
  }
}
