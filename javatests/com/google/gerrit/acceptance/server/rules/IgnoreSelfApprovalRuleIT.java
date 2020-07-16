// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.rules;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.server.rules.IgnoreSelfApprovalRule;
import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

@NoHttpd
public class IgnoreSelfApprovalRuleIT extends AbstractDaemonTest {
  @Inject private IgnoreSelfApprovalRule rule;

  @Test
  public void blocksWhenUploaderIsOnlyApprover() throws Exception {
    enableRule("Code-Review", true);

    PushOneCommit.Result r = createChange();
    approve(r.getChangeId());

    Optional<SubmitRecord> submitRecord = rule.evaluate(r.getChange());

    assertThat(submitRecord).isPresent();
    SubmitRecord result = submitRecord.get();
    assertThat(result.status).isEqualTo(SubmitRecord.Status.NOT_READY);
    assertThat(result.labels).isNotEmpty();
    assertThat(result.requirements)
        .containsExactly(
            SubmitRequirement.builder()
                .setFallbackText("Approval from non-uploader required")
                .setType("non_uploader_approval")
                .build());
  }

  @Test
  public void allowsSubmissionWhenChangeHasNonUploaderApproval() throws Exception {
    enableRule("Code-Review", true);

    // Create change as user
    TestRepository<InMemoryRepository> userTestRepo = cloneProject(project, user);
    PushOneCommit push = pushFactory.create(user.newIdent(), userTestRepo);
    PushOneCommit.Result r = push.to("refs/for/master");

    // Approve as admin
    approve(r.getChangeId());

    Optional<SubmitRecord> submitRecord = rule.evaluate(r.getChange());
    assertThat(submitRecord).isEmpty();
  }

  @Test
  public void doesNothingByDefault() throws Exception {
    enableRule("Code-Review", false);

    PushOneCommit.Result r = createChange();
    approve(r.getChangeId());

    Optional<SubmitRecord> submitRecord = rule.evaluate(r.getChange());
    assertThat(submitRecord).isEmpty();
  }

  private void enableRule(String labelName, boolean newState) throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      Map<String, LabelType> localLabelSections = u.getConfig().getLabelSections();
      if (localLabelSections.isEmpty()) {
        localLabelSections.putAll(projectCache.getAllProjects().getConfig().getLabelSections());
      }
      u.getConfig().updateLabelType(labelName, lt -> lt.setIgnoreSelfApproval(newState));
      u.save();
    }
  }
}
