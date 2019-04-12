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

package com.google.gerrit.acceptance.server.mail;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailFormat;
import com.google.gerrit.testing.FakeEmailSender;
import com.google.inject.Inject;
import org.junit.Test;

public class NotificationMailFormatIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void userReceivesPlaintextEmail() throws Exception {
    // Set user preference to receive only plaintext content
    GeneralPreferencesInfo i = new GeneralPreferencesInfo();
    i.emailFormat = EmailFormat.PLAINTEXT;
    gApi.accounts().id(admin.id().toString()).setPreferences(i);

    // Create change as admin and review as user
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.recommend());

    // Check that admin has received only plaintext content
    assertThat(sender.getMessages()).hasSize(1);
    FakeEmailSender.Message m = sender.getMessages().get(0);
    assertThat(m.body()).isNotNull();
    assertThat(m.htmlBody()).isNull();
    assertMailReplyTo(m, admin.email());
    assertMailReplyTo(m, user.email());

    // Reset user preference
    requestScopeOperations.setApiUser(admin.id());
    i.emailFormat = EmailFormat.HTML_PLAINTEXT;
    gApi.accounts().id(admin.id().toString()).setPreferences(i);
  }

  @Test
  public void userReceivesHtmlAndPlaintextEmail() throws Exception {
    // Create change as admin and review as user
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.recommend());

    // Check that admin has received both HTML and plaintext content
    assertThat(sender.getMessages()).hasSize(1);
    FakeEmailSender.Message m = sender.getMessages().get(0);
    assertThat(m.body()).isNotNull();
    assertThat(m.htmlBody()).isNotNull();
    assertMailReplyTo(m, admin.email());
    assertMailReplyTo(m, user.email());
  }
}
