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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class CreateChangeIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config allowDraftsDisabled() {
    return allowDraftsDisabledConfig();
  }

  @Inject
  @GerritServerConfig Config cfg;

  protected boolean isAllowDrafts() {
    return cfg.getBoolean("change", "allowDrafts", true);
  }

  @Test
  public void createEmptyChange_MissingBranch() throws Exception {
    ChangeInfo ci = new ChangeInfo();
    ci.project = project.get();
    RestResponse r = adminSession.post("/changes/", ci);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
    assertThat(r.getEntityContent()).contains("branch must be non-empty");
  }

  @Test
  public void createEmptyChange_MissingMessage() throws Exception {
    ChangeInfo ci = new ChangeInfo();
    ci.project = project.get();
    ci.branch = "master";
    RestResponse r = adminSession.post("/changes/", ci);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
    assertThat(r.getEntityContent()).contains("commit message must be non-empty");
  }

  @Test
  public void createEmptyChange_InvalidStatus() throws Exception {
    ChangeInfo ci = newChangeInfo(ChangeStatus.SUBMITTED);
    RestResponse r = adminSession.post("/changes/", ci);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
    assertThat(r.getEntityContent()).contains("unsupported change status");
  }

  @Test
  public void createNewChange() throws Exception {
    assertChange(newChangeInfo(ChangeStatus.NEW));
  }

  @Test
  public void createDraftChange() throws Exception {
    assume().that(isAllowDrafts()).isTrue();
    assertChange(newChangeInfo(ChangeStatus.DRAFT));
  }

  @Test
  public void createDraftChangeNotAllowed() throws Exception {
    assume().that(isAllowDrafts()).isFalse();
    ChangeInfo ci = newChangeInfo(ChangeStatus.DRAFT);
    RestResponse r = adminSession.post("/changes/", ci);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
    assertThat(r.getEntityContent()).contains("cannot upload drafts");
  }

  private ChangeInfo newChangeInfo(ChangeStatus status) {
    ChangeInfo in = new ChangeInfo();
    in.project = project.get();
    in.branch = "master";
    in.subject = "Empty change";
    in.topic = "support-gerrit-workflow-in-browser";
    in.status = status;
    return in;
  }

  private void assertChange(ChangeInfo in) throws Exception {
    RestResponse r = adminSession.post("/changes/", in);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);

    ChangeInfo info = newGson().fromJson(r.getReader(), ChangeInfo.class);
    ChangeInfo out = get(info.changeId);

    assertThat(out.branch).isEqualTo(in.branch);
    assertThat(out.subject).isEqualTo(in.subject);
    assertThat(out.topic).isEqualTo(in.topic);
    assertThat(out.status).isEqualTo(in.status);
    assertThat(out.revisions).hasSize(1);
    Boolean draft = Iterables.getOnlyElement(out.revisions.values()).draft;
    assertThat(booleanToDraftStatus(draft)).isEqualTo(in.status);
  }

  private ChangeStatus booleanToDraftStatus(Boolean draft) {
    if (draft == null) {
      return ChangeStatus.NEW;
    }
    return draft ? ChangeStatus.DRAFT : ChangeStatus.NEW;
  }
}
