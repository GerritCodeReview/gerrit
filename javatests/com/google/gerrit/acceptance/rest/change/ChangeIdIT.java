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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.reviewdb.client.Change;
import org.junit.Test;

public class ChangeIdIT extends AbstractDaemonTest {

  @Test
  public void projectChangeNumberReturnsChange() throws Exception {
    PushOneCommit.Result c = createChange();
    RestResponse res = adminRestSession.get(changeDetail(getProjectChangeNumber(c.getChangeId())));
    res.assertOK();
  }

  @Test
  public void wrongProjectChangeNumberReturnsNotFound() throws Exception {
    PushOneCommit.Result c = createChange();
    RestResponse res1 =
        adminRestSession.get(
            changeDetail("unknown/project~" + getNumericChangeId(c.getChangeId())));
    res1.assertNotFound();

    RestResponse res2 = adminRestSession.get(project.get() + "~" + Integer.MAX_VALUE);
    res2.assertNotFound();

    // Try a non-numeric change number
    RestResponse res3 = adminRestSession.get(project.get() + "~some-id");
    res3.assertNotFound();
  }

  @Test
  public void changeNumberReturnsChange() throws Exception {
    PushOneCommit.Result c = createChange();
    RestResponse res = adminRestSession.get(changeDetail(getNumericChangeId(c.getChangeId())));
    res.assertOK();
  }

  @Test
  public void wrongChangeNumberReturnsNotFound() throws Exception {
    RestResponse res = adminRestSession.get(changeDetail(String.valueOf(Integer.MAX_VALUE)));
    res.assertNotFound();
  }

  @Test
  public void tripletChangeIdReturnsChange() throws Exception {
    PushOneCommit.Result c = createChange();
    RestResponse res = adminRestSession.get(changeDetail(getTriplet(c.getChangeId())));
    res.assertOK();
  }

  @Test
  public void wrongTripletChangeIdReturnsNotFound() throws Exception {
    PushOneCommit.Result c = createChange();
    RestResponse res1 = adminRestSession.get(changeDetail("unknown~master~" + c.getChangeId()));
    res1.assertNotFound();

    RestResponse res2 =
        adminRestSession.get(changeDetail(project.get() + "~unknown~" + c.getChangeId()));
    res2.assertNotFound();

    RestResponse res3 = adminRestSession.get(changeDetail(project.get() + "~master~I1234567890"));
    res3.assertNotFound();
  }

  @Test
  public void changeIdReturnsChange() throws Exception {
    PushOneCommit.Result c = createChange();
    RestResponse res = adminRestSession.get(changeDetail(c.getChangeId()));
    res.assertOK();
  }

  @Test
  public void wrongChangeIdReturnsNotFound() throws Exception {
    RestResponse res = adminRestSession.get(changeDetail("I1234567890"));
    res.assertNotFound();
  }

  @Test
  public void changeNumberRedirects() throws Exception {
    // This test tests a redirect that is primarily intended for the UI (though the backend doesn't
    // really care who the caller is). The redirect rewrites a shorthand change number URL (/123) to
    // it's canonical long form (/c/project/+/123).
    int changeId = createChange().getChange().getId().get();
    RestResponse res = anonymousRestSession.get("/" + changeId);
    res.assertTemporaryRedirect("/c/" + project.get() + "/+/" + changeId + "/");
  }

  @Test
  public void changeNumberRedirectsWithTrailingSlash() throws Exception {
    int changeId = createChange().getChange().getId().get();
    RestResponse res = anonymousRestSession.get("/" + changeId + "/");
    res.assertTemporaryRedirect("/c/" + project.get() + "/+/" + changeId + "/");
  }

  @Test
  public void changeNumberOverflowNotFound() throws Exception {
    RestResponse res = anonymousRestSession.get("/9" + Long.MAX_VALUE);
    res.assertNotFound();
  }

  @Test
  public void unknownChangeNumberNotFound() throws Exception {
    RestResponse res = anonymousRestSession.get("/10");
    res.assertNotFound();
  }

  @Test
  public void hiddenChangeNotFound() throws Exception {
    Change.Id changeId = createChange().getChange().getId();
    gApi.changes().id(changeId.get()).setPrivate(true, null);
    RestResponse res = anonymousRestSession.get("/" + changeId.get());
    res.assertNotFound();
  }

  private static String changeDetail(String changeId) {
    return "/changes/" + changeId + "/detail";
  }

  /** Convert a changeId (I0...01) to project~changeNumber (project~00001) */
  private String getProjectChangeNumber(String changeId) throws Exception {
    ChangeApi cApi = gApi.changes().id(changeId);
    return cApi.get().project + "~" + cApi.get()._number;
  }

  /** Convert a changeId (I0...01) to a triplet (project~branch~I0...01) */
  private String getTriplet(String changeId) throws Exception {
    ChangeApi cApi = gApi.changes().id(changeId);
    return cApi.get().project + "~" + cApi.get().branch + "~" + changeId;
  }

  /** Convert a changeId (I0...01) to a numeric changeId (00001) */
  private String getNumericChangeId(String changeId) throws Exception {
    return Integer.toString(gApi.changes().id(changeId).get()._number);
  }
}
