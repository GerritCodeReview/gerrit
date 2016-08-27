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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SubmitPreviewIT extends AbstractSubmitByMerge {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.MERGE_ALWAYS;
  }

  @Test
  public void submitSingleChange() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change = createChange();
    Object request = submitPreview(change.getChangeId());
    RevCommit headAfterSubmit = getRemoteHead();
    assertThat(headAfterSubmit).isEqualTo(initialHead);
    assertRefUpdatedEvents();
    assertChangeMergedEvents();

    Map<Project.NameKey, Map<Branch.NameKey, RevTree>> actual =
        fetchFromBundles(request);
    assertThat(actual.size()).isEqualTo(1);
    assertThat(actual.get(project)).isNotNull();
    assertThat(actual.get(project).size()).isEqualTo(1);

    submit(change.getChangeId());
    assertRevTrees(project, actual.get(project));
  }

  @Test
  public void submitMultipleChangesOtherMergeConflict() throws Exception {
    RevCommit initialHead = getRemoteHead();

    PushOneCommit.Result change =
        createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit headAfterFirstSubmit = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "other content");
    PushOneCommit.Result change3 = createChange("Change 3", "d", "d");
    PushOneCommit.Result change4 = createChange("Change 4", "e", "e");
    // change 2 is not approved, but we ignore labels
    approve(change3.getChangeId());
    Object request = submitPreview(change4.getChangeId());

    assertSubmitPreviewRequestHasError(request, "Failed to submit 3 changes due to the following problems:\n" +
        "Change " + change2.getChange().getId() + ": Change could not be merged due to a path conflict. Please rebase the change locally and upload the rebased commit for review.\n" +
        "Change " + change3.getChange().getId() + ": Change could not be merged due to a path conflict. Please rebase the change locally and upload the rebased commit for review.\n" +
        "Change " + change4.getChange().getId() + ": Change could not be merged due to a path conflict. Please rebase the change locally and upload the rebased commit for review.");

    RevCommit headAfterSubmit = getRemoteHead();
    assertThat(headAfterSubmit).isEqualTo(headAfterFirstSubmit);
    assertRefUpdatedEvents(initialHead, headAfterFirstSubmit);
    assertChangeMergedEvents(change.getChangeId(), headAfterFirstSubmit.name());
  }

  @Test
  public void submitMultipleChanges() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "other content");
    PushOneCommit.Result change3 = createChange("Change 3", "d", "d");
    PushOneCommit.Result change4 = createChange("Change 4", "e", "e");
    // change 2 is not approved, but we ignore labels
    approve(change3.getChangeId());
    Object request = submitPreview(change4.getChangeId());

    Map<String, Map<String, Integer>> expected = new HashMap<>();
    expected.put(project.get(), new HashMap<String, Integer>());
    expected.get(project.get()).put("refs/heads/master", 3);
    Map<Project.NameKey, Map<Branch.NameKey, RevTree>> actual =
        fetchFromBundles(request);

    assertThat(actual.keySet().size()).isEqualTo(1);
    assertThat(actual.get(project)).isNotNull();
    assertThat(actual.get(project).size()).isEqualTo(1);

    // check that the submit preview did not actually submit
    RevCommit headAfterSubmit = getRemoteHead();
    assertThat(headAfterSubmit).isEqualTo(initialHead);
    assertRefUpdatedEvents();
    assertChangeMergedEvents();

    // now check we actually have the same content:
    approve(change2.getChangeId());
    submit(change4.getChangeId());
    assertRevTrees(project, actual.get(project));
  }
}
