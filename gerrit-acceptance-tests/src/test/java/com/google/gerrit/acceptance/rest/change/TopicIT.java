// Copyright (C) 2014 The Android Open Source Project
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
import com.google.gerrit.acceptance.PushOneCommit.Result;

import org.apache.http.HttpStatus;
import org.junit.Test;

public class TopicIT extends AbstractDaemonTest {
  @Test
  public void topic() throws Exception {
    Result result = createChange();
    String endpoint = "/changes/" + result.getChangeId() + "/topic";
    RestResponse response = adminSession.put(endpoint, "topic");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SC_OK);

    response = adminSession.delete(endpoint);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SC_NO_CONTENT);

    response = adminSession.put(endpoint, "topic");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SC_OK);

    response = adminSession.put(endpoint, "");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SC_NO_CONTENT);
  }
}
