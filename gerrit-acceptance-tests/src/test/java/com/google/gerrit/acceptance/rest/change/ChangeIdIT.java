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
import static com.google.common.truth.Truth.assert_;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import java.util.List;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;

public class ChangeIdIT extends AbstractDaemonTest {

  @Test
  public void newChangeIdWithProjectReturnsChange() throws Exception {
    PushOneCommit.Result c = createChange();
    RestResponse res = adminRestSession.get(changeDetail(getProjectBasedChangeId(c.getChangeId())));
    res.assertOK();
  }

  @Test
  public void requestsWithDeprecatedChangeIdsIssueRedirect() throws Exception {
    String cId = createChange().getChangeId();

    String url = CharMatcher.is('/').trimTrailingFrom(server.getUrl());
    HttpClient instance = HttpClientBuilder.create().disableRedirectHandling().build();

    List<String> ids = ImmutableList.of(cId, getNumericChangeId(cId), getTriplet(cId));
    for (String s : ids) {
      HttpResponse response = instance.execute(new HttpGet(url + changeDetail(s)));
      assertPermanentRedirect(response, changeDetail(getProjectBasedChangeId(cId)));
    }
  }

  @Test
  public void requestsWithDeprecatedChangeIdsFollowRedirect() throws Exception {
    String cId = createChange().getChangeId();
    List<String> ids = ImmutableList.of(cId, getNumericChangeId(cId), getTriplet(cId));
    for (String s : ids) {
      RestResponse res = adminRestSession.get(changeDetail(s));
      res.assertOK();
    }
  }

  private static String changeDetail(String changeId) {
    return "/changes/" + changeId + "/detail";
  }

  /** Convert a changeId (I0...01) to a project-based changedId (project/+/00001) */
  private String getProjectBasedChangeId(String changeId) throws Exception {
    ChangeApi cApi = gApi.changes().id(changeId);
    return cApi.get().project + "/+/" + cApi.get()._number;
  }

  /** Convert a changeId (I0...01) to a triplet (project~branch~00001) */
  private String getTriplet(String changeId) throws Exception {
    ChangeApi cApi = gApi.changes().id(changeId);
    return cApi.get().project + "~" + cApi.get().branch + "~" + changeId;
  }

  /** Convert a changeId (I0...01) to a numeric changeId (00001) */
  private String getNumericChangeId(String changeId) throws Exception {
    return Integer.toString(gApi.changes().id(changeId).get()._number);
  }

  public static void assertPermanentRedirect(HttpResponse response, String newLocation)
      throws Exception {
    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_MOVED_PERMANENTLY);
    String receivedLocation = response.getFirstHeader("Location").getValue();
    assert_()
        .withFailureMessage(String.format("Expected HTTP 301 to %s", newLocation))
        .that(newLocation)
        .isEqualTo(receivedLocation);
  }
}
