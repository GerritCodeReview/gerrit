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

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class CommitIncludedInIT extends AbstractDaemonTest {
  @Test
  public void includedInRestEndPoint() throws Exception {
    Result result = createChange();
    RestResponse r = userRestSession.get("/projects/" + project.get()
        + "/commits/" + result.getCommit().name() + "/in");
    r.assertOK();
  }

  @Test
  public void includedInOpenChange() throws Exception {
    Result result = createChange();
    assertThat(getIncludedIn(result.getCommit().getId()).branches).hasSize(0);
    assertThat(getIncludedIn(result.getCommit().getId()).tags).hasSize(0);
  }

  @Test
  public void includedInMergedChange() throws Exception {
    Result result = createChange();
    gApi.changes().id(result.getChangeId()).revision(result.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes().id(result.getChangeId()).revision(result.getCommit().name())
        .submit();

    assertThat(getIncludedIn(result.getCommit().getId()).branches).hasSize(1);
    assertThat(getIncludedIn(result.getCommit().getId()).tags).hasSize(0);

    grantTagPermissions();
    gApi.projects().name(result.getChange().project().get()).tag("test-tag")
        .create(new TagInput());

    assertThat(getIncludedIn(result.getCommit().getId()).tags).hasSize(1);
  }

  private void grantTagPermissions() throws Exception {
    grant(Permission.CREATE, project, R_TAGS + "*");
    grant(Permission.CREATE_TAG, project, R_TAGS + "*");
    grant(Permission.CREATE_SIGNED_TAG, project, R_TAGS + "*");
  }

  private IncludedInInfo getIncludedIn(ObjectId id) throws Exception {
    RestResponse r = userRestSession
        .get("/projects/" + project.get() + "/commits/" + id.name() + "/in");
    IncludedInInfo result =
        newGson().fromJson(r.getReader(), IncludedInInfo.class);
    r.consume();
    return result;
  }
}
