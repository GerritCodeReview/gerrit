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
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.TestTimeUtil;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

@NoHttpd
public class CreateChangeIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config allowDraftsDisabled() {
    return allowDraftsDisabledConfig();
  }

  @BeforeClass
  public static void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @AfterClass
  public static void restoreTime() {
    TestTimeUtil.useSystemTime();
  }


  @Test
  public void createEmptyChange_MissingBranch() throws Exception {
    ChangeInput ci = new ChangeInput();
    ci.project = project.get();
    assertCreateFails(ci, BadRequestException.class,
        "branch must be non-empty");
  }

  @Test
  public void createEmptyChange_MissingMessage() throws Exception {
    ChangeInput ci = new ChangeInput();
    ci.project = project.get();
    ci.branch = "master";
    assertCreateFails(ci, BadRequestException.class,
        "commit message must be non-empty");
  }

  @Test
  public void createEmptyChange_InvalidStatus() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.MERGED);
    assertCreateFails(ci, BadRequestException.class,
        "unsupported change status");
  }

  @Test
  public void createNewChange() throws Exception {
    assertCreateSucceeds(newChangeInput(ChangeStatus.NEW));
  }

  @Test
  public void createDraftChange() throws Exception {
    assume().that(isAllowDrafts()).isTrue();
    assertCreateSucceeds(newChangeInput(ChangeStatus.DRAFT));
  }

  @Test
  public void createDraftChangeNotAllowed() throws Exception {
    assume().that(isAllowDrafts()).isFalse();
    ChangeInput ci = newChangeInput(ChangeStatus.DRAFT);
    assertCreateFails(ci, MethodNotAllowedException.class,
        "draft workflow is disabled");
  }

  @Test
  public void notedbCommit() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    ChangeInfo c = assertCreateSucceeds(newChangeInput(ChangeStatus.NEW));
    try (Repository repo = repoManager.openMetadataRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit commit = rw.parseCommit(
          repo.exactRef(ChangeNoteUtil.changeRefName(new Change.Id(c._number)))
              .getObjectId());

      assertThat(commit.getShortMessage()).isEqualTo("Create change");

      PersonIdent expectedAuthor = ChangeNoteUtil.newIdent(
          accountCache.get(admin.id).getAccount(), c.created, serverIdent.get(),
          AnonymousCowardNameProvider.DEFAULT);
      assertThat(commit.getAuthorIdent()).isEqualTo(expectedAuthor);

      assertThat(commit.getCommitterIdent())
          .isEqualTo(new PersonIdent(serverIdent.get(), c.created));
      assertThat(commit.getParentCount()).isEqualTo(0);
    }
  }

  private ChangeInput newChangeInput(ChangeStatus status) {
    ChangeInput in = new ChangeInput();
    in.project = project.get();
    in.branch = "master";
    in.subject = "Empty change";
    in.topic = "support-gerrit-workflow-in-browser";
    in.status = status;
    return in;
  }

  private ChangeInfo assertCreateSucceeds(ChangeInput in) throws Exception {
    ChangeInfo out = gApi.changes().create(in).get();
    assertThat(out.branch).isEqualTo(in.branch);
    assertThat(out.subject).isEqualTo(in.subject);
    assertThat(out.topic).isEqualTo(in.topic);
    assertThat(out.status).isEqualTo(in.status);
    assertThat(out.revisions).hasSize(1);
    assertThat(out.submitted).isNull();
    Boolean draft = Iterables.getOnlyElement(out.revisions.values()).draft;
    assertThat(booleanToDraftStatus(draft)).isEqualTo(in.status);
    return out;
  }

  private void assertCreateFails(ChangeInput in,
      Class<? extends RestApiException> errType, String errSubstring)
      throws Exception {
    exception.expect(errType);
    exception.expectMessage(errSubstring);
    gApi.changes().create(in);
  }

  private ChangeStatus booleanToDraftStatus(Boolean draft) {
    if (draft == null) {
      return ChangeStatus.NEW;
    }
    return draft ? ChangeStatus.DRAFT : ChangeStatus.NEW;
  }
}
