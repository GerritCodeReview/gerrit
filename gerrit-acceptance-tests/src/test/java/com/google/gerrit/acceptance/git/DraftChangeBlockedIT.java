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

package com.google.gerrit.acceptance.git;

import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.Permission;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class DraftChangeBlockedIT extends AbstractDaemonTest {

  @Before
  public void setUp() throws Exception {
    block(Permission.PUSH, ANONYMOUS_USERS, "refs/drafts/*");
  }

  @Test
  public void testPushDraftChange_Blocked() throws Exception {
    // create draft by pushing to 'refs/drafts/'
    PushOneCommit.Result r = pushTo("refs/drafts/master");
    r.assertErrorStatus("cannot upload drafts");
  }

  @Test
  public void testPushDraftChangeMagic_Blocked() throws Exception {
    // create draft by using 'draft' option
    PushOneCommit.Result r = pushTo("refs/for/master%draft");
    r.assertErrorStatus("cannot upload drafts");
  }
}
