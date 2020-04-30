// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.mail.Address;
import com.google.gerrit.testing.FakeEmailSender;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class OutgoingEmailTest extends AbstractDaemonTest {

  @Test
  public void messageIdHeaderAbandonRestore() throws Exception {
    Repository repository = repoManager.openRepository(project);
    PushOneCommit.Result result = createChange();
    AddReviewerInput addReviewerInput = new AddReviewerInput();
    addReviewerInput.reviewer = user.email();
    gApi.changes().id(result.getChangeId()).addReviewer(addReviewerInput);
    sender.clear();

    gApi.changes().id(result.getChangeId()).abandon();
    assertThat(
            Iterables.getOnlyElement(sender.getMessages()).headers().get("Message-ID").toString())
        .contains(
            repository
                .getRefDatabase()
                .exactRef(result.getChange().getId().toRefPrefix() + "meta")
                .getObjectId()
                .getName());
    sender.clear();

    gApi.changes().id(result.getChangeId()).restore();
    assertThat(
            Iterables.getOnlyElement(sender.getMessages()).headers().get("Message-ID").toString())
        .contains(
            repository
                .getRefDatabase()
                .exactRef(result.getChange().getId().toRefPrefix() + "meta")
                .getObjectId()
                .getName());
  }

  @Test
  @GerritConfig(
      name = "auth.registerEmailPrivateKey",
      value = "HsOc6l_2lhS9G7sE_RsnS7Z6GJjdRDX14co=")
  public void messageIdHeaderCreateEmail() throws Exception {
    Repository allUsersRepo = repoManager.openRepository(allUsers);
    String email = "new.email@example.com";
    EmailInput input = new EmailInput();
    input.email = email;
    sender.clear();
    gApi.accounts().self().addEmail(input);

    assertThat(sender.getMessages()).hasSize(1);
    FakeEmailSender.Message m = sender.getMessages().get(0);
    assertThat(m.rcpt()).containsExactly(Address.create(email));

    String prefixAccountId =
        (admin.id().get() % 100 > 9)
            ? String.valueOf(admin.id().get() % 100)
            : String.valueOf('0') + (admin.id().get() % 100);
    assertThat(
            Iterables.getOnlyElement(sender.getMessages()).headers().get("Message-ID").toString())
        .contains(
            allUsersRepo
                .getRefDatabase()
                .exactRef(String.format("refs/users/%s/%d", prefixAccountId, admin.id().get()))
                .getObjectId()
                .getName());
  }

  @Test
  public void htmlAndPlainTextSuffixAddedToMessageId() throws Exception {
    PushOneCommit.Result result = createChange();
    GeneralPreferencesInfo generalPreferencesInfo = new GeneralPreferencesInfo();
    generalPreferencesInfo.emailFormat = GeneralPreferencesInfo.EmailFormat.PLAINTEXT;
    gApi.accounts().id(user.id().get()).setPreferences(generalPreferencesInfo);
    AddReviewerInput addReviewerInput = new AddReviewerInput();
    addReviewerInput.reviewer = user.email();
    gApi.changes().id(result.getChangeId()).addReviewer(addReviewerInput);
    sender.clear();

    gApi.changes().id(result.getChangeId()).current().review(ReviewInput.approve());
    assertThat(
            Iterables.getOnlyElement(sender.getMessages()).headers().get("Message-ID").toString())
        .contains("-PLAIN_TEXT");
    sender.clear();

    generalPreferencesInfo.emailFormat = GeneralPreferencesInfo.EmailFormat.HTML_PLAINTEXT;
    gApi.accounts().id(user.id().get()).setPreferences(generalPreferencesInfo);
    sender.clear();

    gApi.changes().id(result.getChangeId()).current().review(ReviewInput.reject());
    assertThat(
            Iterables.getOnlyElement(sender.getMessages()).headers().get("Message-ID").toString())
        .contains("-HTML");
  }
}
