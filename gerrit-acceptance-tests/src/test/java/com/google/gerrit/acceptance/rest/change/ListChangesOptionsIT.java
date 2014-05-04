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

import static com.google.gerrit.extensions.common.ListChangesOption.ALL_REVISIONS;
import static com.google.gerrit.extensions.common.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.common.ListChangesOption.MESSAGES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.common.ChangeInfo;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

@NoHttpd
public class ListChangesOptionsIT extends AbstractDaemonTest {

  private String changeId;
  private List<PushOneCommit.Result> results;

  @Before
  public void setUp() throws Exception {
    results = Lists.newArrayList();
    results.add(push("file contents", null));
    changeId = results.get(0).getChangeId();
    results.add(push("new contents 1", changeId));
    results.add(push("new contents 2", changeId));
  }

  private PushOneCommit.Result push(String content, String baseChangeId)
      throws Exception {
    String subject = "Change subject";
    String fileName = "a.txt";
    PushOneCommit push = pushFactory.create(
        db, admin.getIdent(), subject, fileName, content, baseChangeId);
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    r.assertOkStatus();
    return r;
  }

  @Test
  public void noRevisionOptions() throws Exception {
    ChangeInfo c = info(changeId);
    assertNull(c.currentRevision);
    assertNull(c.revisions);
  }

  @Test
  public void currentRevision() throws Exception {
    ChangeInfo c = get(changeId, CURRENT_REVISION);
    assertEquals(commitId(2), c.currentRevision);
    assertEquals(ImmutableSet.of(commitId(2)), c.revisions.keySet());
    assertEquals(3, c.revisions.get(commitId(2))._number);
  }

  @Test
  public void currentRevisionAndMessages() throws Exception {
    ChangeInfo c = get(changeId, CURRENT_REVISION, MESSAGES);
    assertEquals(1, c.revisions.size());
    assertEquals(commitId(2), c.currentRevision);
    assertEquals(ImmutableSet.of(commitId(2)), c.revisions.keySet());
    assertEquals(3, c.revisions.get(commitId(2))._number);
  }

  @Test
  public void allRevisions() throws Exception {
    ChangeInfo c = get(changeId, ALL_REVISIONS);
    assertEquals(commitId(2), c.currentRevision);
    assertEquals(ImmutableSet.of(commitId(0), commitId(1), commitId(2)),
        c.revisions.keySet());
    assertEquals(1, c.revisions.get(commitId(0))._number);
    assertEquals(2, c.revisions.get(commitId(1))._number);
    assertEquals(3, c.revisions.get(commitId(2))._number);
  }

  private String commitId(int i) {
    return results.get(i).getCommitId().name();
  }
}
