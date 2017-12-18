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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class DisablePrivateChangesIT extends AbstractDaemonTest {

  @Test
  @GerritConfig(name = "change.disablePrivateChanges", value = "true")
  public void createChangeWithDisablePrivateChangesTrue() throws Exception {
    ChangeInput input = new ChangeInput(project.get(), "master", "empty change");
    input.isPrivate = true;
    exception.expect(BadRequestException.class);
    exception.expectMessage("private changes are not supported");
    gApi.changes().create(input);
  }

  @Test
  public void createChangeWithDisablePrivateChangesFalse() throws Exception {
    ChangeInput input = new ChangeInput(project.get(), "master", "empty change");
    input.isPrivate = true;
    assertThat(gApi.changes().create(input).get().isPrivate).isEqualTo(true);
  }

  @Test
  @GerritConfig(name = "change.disablePrivateChanges", value = "true")
  public void pushPrivatesWithDisablePrivateChangesTrue() throws Exception {
    PushOneCommit.Result result =
        pushFactory.create(db, admin.getIdent(), testRepo).to("refs/for/master%private");
    result.assertErrorStatus();
  }

  @Test
  @GerritConfig(name = "change.disablePrivateChanges", value = "true")
  public void pushDraftsWithDisablePrivateChangesTrue() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result result =
        pushFactory.create(db, admin.getIdent(), testRepo).to("refs/for/master%draft");
    result.assertErrorStatus();

    testRepo.reset(initialHead);
    result = pushFactory.create(db, admin.getIdent(), testRepo).to("refs/drafts/master");
    result.assertErrorStatus();
  }

  @Test
  public void pushPrivatesWithDisablePrivateChangesFalse() throws Exception {
    PushOneCommit.Result result =
        pushFactory.create(db, admin.getIdent(), testRepo).to("refs/for/master%private");
    assertThat(result.getChange().change().isPrivate()).isEqualTo(true);
  }

  @Test
  public void pushDraftsWithDisablePrivateChangesFalse() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result result =
        pushFactory.create(db, admin.getIdent(), testRepo).to("refs/for/master%draft");
    assertThat(result.getChange().change().isPrivate()).isEqualTo(true);

    testRepo.reset(initialHead);
    result = pushFactory.create(db, admin.getIdent(), testRepo).to("refs/drafts/master");
    assertThat(result.getChange().change().isPrivate()).isEqualTo(true);
  }
}
