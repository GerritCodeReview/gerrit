// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.reviewdb.client.Change;

import org.junit.Test;

import java.io.IOException;

public class StarredChangesIT extends AbstractDaemonTest {

  @Test
  public void starredChangeState() throws Exception {
    Result c1 = createChange();
    Result c2 = createChange();
    assertNull(getChange(c1.getChangeId()).starred);
    assertNull(getChange(c2.getChangeId()).starred);
    starChange(true, c1.getPatchSetId().getParentKey());
    starChange(true, c2.getPatchSetId().getParentKey());
    assertTrue(getChange(c1.getChangeId()).starred);
    assertTrue(getChange(c2.getChangeId()).starred);
    starChange(false, c1.getPatchSetId().getParentKey());
    starChange(false, c2.getPatchSetId().getParentKey());
    assertNull(getChange(c1.getChangeId()).starred);
    assertNull(getChange(c2.getChangeId()).starred);
  }

  private void starChange(boolean on, Change.Id id) throws IOException {
    String url = "/accounts/self/starred.changes/" + id.get();
    if (on) {
      RestResponse r = adminSession.put(url);
      assertEquals(204, r.getStatusCode());
    } else {
      RestResponse r = adminSession.delete(url);
      assertEquals(204, r.getStatusCode());
    }
  }
}
