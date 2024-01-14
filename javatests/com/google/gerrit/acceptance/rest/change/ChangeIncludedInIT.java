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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.server.change.FilterIncludedIn;
import com.google.inject.Inject;
import java.util.function.Predicate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

@NoHttpd
public class ChangeIncludedInIT extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  private static class TestFilter implements FilterIncludedIn {
    @Override
    public Predicate<String> getBranchFilter(Project.NameKey project, RevCommit commit) {
      return branch -> !branch.startsWith("t");
    }

    @Override
    public Predicate<String> getTagFilter(Project.NameKey project, RevCommit commit) {
      return tag -> !tag.startsWith("bad");
    }
  }

  @Test
  public void includedInOpenChange() throws Exception {
    Result result = createChange();
    assertThat(gApi.changes().id(result.getChangeId()).includedIn().branches).isEmpty();
    assertThat(gApi.changes().id(result.getChangeId()).includedIn().tags).isEmpty();
  }

  String baseTestCase() throws Exception {
    Result result = createChange();
    gApi.changes()
        .id(result.getChangeId())
        .revision(result.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes().id(result.getChangeId()).revision(result.getCommit().name()).submit();

    assertThat(gApi.changes().id(result.getChangeId()).includedIn().branches)
        .containsExactly("master");
    assertThat(gApi.changes().id(result.getChangeId()).includedIn().tags).isEmpty();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE_TAG).ref(R_TAGS + "*").group(adminGroupUuid()))
        .update();
    gApi.projects().name(project.get()).tag("test-tag").create(new TagInput());

    createBranch(BranchNameKey.create(project, "test-branch"));
    return result.getChangeId();
  }

  @Test
  public void includedInMergedChange() throws Exception {
    String changeId = baseTestCase();
    assertThat(gApi.changes().id(changeId).includedIn().tags).containsExactly("test-tag");

    assertThat(gApi.changes().id(changeId).includedIn().branches)
        .containsExactly("master", "test-branch");
  }

  @Test
  public void includedInFiltered() throws Exception {
    String changeId = baseTestCase();
    gApi.projects().name(project.get()).tag("bad-tag").create(new TagInput());
    assertThat(gApi.changes().id(changeId).includedIn().tags)
        .containsExactly("bad-tag", "test-tag");
    try (ExtensionRegistry.Registration registration =
        extensionRegistry.newRegistration().add(new TestFilter())) {
      assertThat(gApi.changes().id(changeId).includedIn().tags).containsExactly("test-tag");

      assertThat(gApi.changes().id(changeId).includedIn().branches).containsExactly("master");
    }
  }
}
