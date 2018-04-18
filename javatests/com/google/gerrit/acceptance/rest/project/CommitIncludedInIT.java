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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.IncludedInInfo;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.reviewdb.client.Branch;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class CommitIncludedInIT extends AbstractDaemonTest {
  @Test
  public void includedInOpenChange() throws Exception {
    Result result = createChange();
    assertThat(getIncludedIn(result.getCommit().getId()).branches).isEmpty();
    assertThat(getIncludedIn(result.getCommit().getId()).tags).isEmpty();
  }

  @Test
  public void includedInMergedChange() throws Exception {
    Result result = createChange();
    gApi.changes()
        .id(result.getChangeId())
        .revision(result.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes().id(result.getChangeId()).revision(result.getCommit().name()).submit();

    assertThat(getIncludedIn(result.getCommit().getId()).branches).containsExactly("master");
    assertThat(getIncludedIn(result.getCommit().getId()).tags).isEmpty();

    grant(project, R_TAGS + "*", Permission.CREATE_TAG);
    gApi.projects().name(result.getChange().project().get()).tag("test-tag").create(new TagInput());

    assertThat(getIncludedIn(result.getCommit().getId()).tags).containsExactly("test-tag");

    createBranch(new Branch.NameKey(project.get(), "test-branch"));

    assertThat(getIncludedIn(result.getCommit().getId()).branches)
        .containsExactly("master", "test-branch");
  }

  private IncludedInInfo getIncludedIn(ObjectId id) throws Exception {
    RestResponse r =
        userRestSession.get("/projects/" + project.get() + "/commits/" + id.name() + "/in");
    IncludedInInfo result = newGson().fromJson(r.getReader(), IncludedInInfo.class);
    r.consume();
    return result;
  }
}
