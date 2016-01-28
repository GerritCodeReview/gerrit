// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.git.ChangeSet;
import com.google.gerrit.server.git.MergeSuperSet;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class SubmitResolvingMergeCommitIT extends AbstractDaemonTest {
  @Inject
  private MergeSuperSet mergeSuperSet;

  @Inject
  private Submit submit;

  @Test
  public void resolvingMergeCommitAtEndOfChain() throws Exception {
    /*
      A <- B <- C <------- D
      ^                    ^
      |                    |
      E <- F <- G <- H <-- M*

      G has a conflict with C and is resolved in M which is a merge
      commit of H and D.
    */

    PushOneCommit.Result a = createChange("A");
    PushOneCommit.Result b =
        createChange("B", "new.txt", "No conflict line", a.getCommit());
    PushOneCommit.Result c = createChange("C", b.getCommit());
    PushOneCommit.Result d = createChange("D", c.getCommit());

    PushOneCommit.Result e = createChange("E", a.getCommit());
    PushOneCommit.Result f = createChange("F", e.getCommit());
    PushOneCommit.Result g =
        createChange("G", "new.txt", "Conflicting line", f.getCommit());
    PushOneCommit.Result h = createChange("H", g.getCommit());

    approve(a.getChangeId());
    approve(b.getChangeId());
    approve(c.getChangeId());
    approve(d.getChangeId());
    submit(d.getChangeId());

    approve(e.getChangeId());
    approve(f.getChangeId());
    approve(g.getChangeId());
    approve(h.getChangeId());

    assertMergeable(e.getChange(), true);
    assertMergeable(f.getChange(), true);
    assertMergeable(g.getChange(), false);
    assertMergeable(h.getChange(), false);

    PushOneCommit.Result m = createChange("M", "new.txt", "Resolved conflict",
        d.getCommit(), h.getCommit());
    approve(m.getChangeId());

    ChangeSet cs = mergeSuperSet.completeChangeSet(db, m.getChange().change());
    String result = submit.problemsForSubmittingChangeset(cs,
        identifiedUserFactory.create(admin.getId()), db);
    assertThat(result).isNull();

    assertMergeable(m.getChange(), true);
    submit(m.getChangeId());

    assertMerged(e.getChangeId());
    assertMerged(f.getChangeId());
    assertMerged(g.getChangeId());
    assertMerged(h.getChangeId());
    assertMerged(m.getChangeId());
  }

  @Test
  public void resolvingMergeCommitComingBeforeConflict() throws Exception {
    /*
      A <- B <- C <- D
      ^    ^
      |    |
      E <- F* <- G

      F is a merge commit of E and B and resolves any conflict.
      However G is conflicting with C.
    */

    PushOneCommit.Result a = createChange("A");
    PushOneCommit.Result b =
        createChange("B", "new.txt", "No conflict line", a.getCommit());
    PushOneCommit.Result c =
        createChange("C", "new.txt", "No conflict line #2", b.getCommit());
    PushOneCommit.Result d = createChange("D", c.getCommit());
    PushOneCommit.Result e =
        createChange("E", "new.txt", "Conflicting line", a.getCommit());
    PushOneCommit.Result f = createChange("F", "new.txt", "Resolved conflict",
        b.getCommit(), e.getCommit());
    PushOneCommit.Result g =
        createChange("G", "new.txt", "Conflicting line #2", f.getCommit());

    assertMergeable(e.getChange(), true);

    approve(a.getChangeId());
    approve(b.getChangeId());
    submit(b.getChangeId());

    assertMergeable(e.getChange(), false);
    assertMergeable(f.getChange(), true);
    assertMergeable(g.getChange(), true);

    approve(c.getChangeId());
    approve(d.getChangeId());
    submit(d.getChangeId());

    approve(e.getChangeId());
    approve(f.getChangeId());
    approve(g.getChangeId());

    assertMergeable(g.getChange(), false);

    ChangeSet cs = mergeSuperSet.completeChangeSet(db, g.getChange().change());
    String result = submit.problemsForSubmittingChangeset(cs,
        identifiedUserFactory.create(admin.getId()), db);
    assertThat(result).isEqualTo(Submit.CLICK_FAILURE_OTHER_TOOLTIP);
  }

  private void assertMergeable(ChangeData change, boolean expected)
      throws Exception {
    change.setMergeable(null);
    assertThat(change.isMergeable()).isEqualTo(expected);
  }

  private void submit(String changeId) throws Exception {
    gApi.changes()
        .id(changeId)
        .current()
        .submit();
  }

  private void assertMerged(String changeId) throws Exception {
    assertThat(gApi
        .changes()
        .id(changeId)
        .get()
        .status).isEqualTo(ChangeStatus.MERGED);
  }

  private PushOneCommit.Result createChange(String subject,
      RevCommit... parents) throws Exception {
    return createChange(subject, "", "", parents);
  }
}
