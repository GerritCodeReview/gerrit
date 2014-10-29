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

package com.google.gerrit.acceptance.api.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.common.AccountInfo;

import org.junit.Test;

public class AccountIT extends AbstractDaemonTest {

  @Test
  public void get() throws Exception {
    AccountInfo info = gApi
        .accounts()
        .id("admin")
        .get();
    assertEquals("Administrator", info.name);
    assertEquals("admin@example.com", info.email);
    assertEquals("admin", info.username);
  }

  @Test
  public void self() throws Exception {
    AccountInfo info = gApi
        .accounts()
        .self()
        .get();
    assertEquals("Administrator", info.name);
    assertEquals("admin@example.com", info.email);
    assertEquals("admin", info.username);
  }

  @Test
  public void starUnstarChange() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = "p~master~" + r.getChangeId();
    gApi.accounts()
        .self()
        .starChange(triplet);
    assertTrue(getChange(triplet).starred);
    gApi.accounts()
        .self()
        .unstarChange(triplet);
    assertNull(getChange(triplet).starred);
  }
}
