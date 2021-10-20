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
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.EmailHeader.StringEmailHeader;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.testing.FakeEmailSender;
import java.net.URL;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class OutgoingEmailIT extends AbstractDaemonTest {

  @Test
  public void messageIdHeaderFromChangeUpdate() throws Exception {
    Repository repository = repoManager.openRepository(project);
    PushOneCommit.Result result = createChange();
    ReviewerInput reviewerInput = new ReviewerInput();
    reviewerInput.reviewer = user.email();
    gApi.changes().id(result.getChangeId()).addReviewer(reviewerInput);
    sender.clear();

    gApi.changes().id(result.getChangeId()).abandon();
    assertThat(getMessageId(sender))
        .isEqualTo(
            withPrefixAndSuffixForMessageId(
                repository
                        .getRefDatabase()
                        .exactRef(result.getChange().getId().toRefPrefix() + "meta")
                        .getObjectId()
                        .getName()
                    + "-HTML"));
    sender.clear();

    gApi.changes().id(result.getChangeId()).restore();
    assertThat(getMessageId(sender))
        .isEqualTo(
            withPrefixAndSuffixForMessageId(
                repository
                        .getRefDatabase()
                        .exactRef(result.getChange().getId().toRefPrefix() + "meta")
                        .getObjectId()
                        .getName()
                    + "-HTML"));
  }

  @Test
  @GerritConfig(
      name = "auth.registerEmailPrivateKey",
      value = "HsOc6l_2lhS9G7sE_RsnS7Z6GJjdRDX14co=")
  public void messageIdHeaderFromAccountUpdate() throws Exception {
    Repository allUsersRepo = repoManager.openRepository(allUsers);
    String email = "new.email@example.com";
    EmailInput input = new EmailInput();
    input.email = email;
    sender.clear();
    gApi.accounts().self().addEmail(input);

    assertThat(sender.getMessages()).hasSize(1);
    FakeEmailSender.Message m = sender.getMessages().get(0);
    assertThat(m.rcpt()).containsExactly(Address.create(email));

    assertThat(getMessageId(sender))
        .isEqualTo(
            withPrefixAndSuffixForMessageId(
                allUsersRepo
                        .getRefDatabase()
                        .exactRef(RefNames.refsUsers(admin.id()))
                        .getObjectId()
                        .getName()
                    + "-HTML"));
  }

  @Test
  public void messageIdHeaderFromPasswordUpdate() throws Exception {
    sender.clear();
    String newPassword = gApi.accounts().self().generateHttpPassword();
    assertThat(newPassword).isNotNull();
    assertThat(getMessageId(sender))
        .containsMatch("<HTTP_password_change-" + admin.id().toString() + ".*@.*>");
  }

  @Test
  public void htmlAndPlainTextSuffixAddedToMessageId() throws Exception {
    PushOneCommit.Result result = createChange();
    GeneralPreferencesInfo generalPreferencesInfo = new GeneralPreferencesInfo();
    generalPreferencesInfo.emailFormat = GeneralPreferencesInfo.EmailFormat.PLAINTEXT;
    gApi.accounts().id(user.id().get()).setPreferences(generalPreferencesInfo);
    ReviewerInput reviewerInput = new ReviewerInput();
    reviewerInput.reviewer = user.email();
    gApi.changes().id(result.getChangeId()).addReviewer(reviewerInput);
    sender.clear();

    gApi.changes().id(result.getChangeId()).current().review(ReviewInput.approve());
    assertThat(getMessageId(sender)).contains("-PLAIN");
    sender.clear();

    generalPreferencesInfo.emailFormat = GeneralPreferencesInfo.EmailFormat.HTML_PLAINTEXT;
    gApi.accounts().id(user.id().get()).setPreferences(generalPreferencesInfo);
    sender.clear();

    gApi.changes().id(result.getChangeId()).current().review(ReviewInput.reject());
    assertThat(getMessageId(sender)).contains("-HTML");
  }

  private static String getMessageId(FakeEmailSender sender) {
    return ((StringEmailHeader)
            (Iterables.getOnlyElement(sender.getMessages()).headers().get("Message-ID")))
        .getString();
  }

  // Each message-id must start with '<' and end with '>'. Also, it must contain no spaces and it
  // must contain a '@'.
  private String withPrefixAndSuffixForMessageId(String id) throws Exception {
    return "<" + id + "@" + new URL(canonicalWebUrl.get()).getHost() + ">";
  }
}
