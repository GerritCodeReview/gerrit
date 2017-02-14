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
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailFormat;
import org.junit.Test;

public class NotificationMailFormatIT extends AbstractDaemonTest {

  @Test
  public void userReceivesPlaintextEmail() throws Exception {
    // Set user preference to receive only plaintext content
    GeneralPreferencesInfo i = new GeneralPreferencesInfo();
    i.emailFormat = EmailFormat.PLAINTEXT;
    gApi.accounts().id(admin.getId().toString()).setPreferences(i);

    // Create change as admin and review as user
    PushOneCommit.Result r = createChange();
    setApiUser(user);
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.recommend());

    // Check that admin has received only plaintext content
    assertThat(sender.getMessages()).hasSize(1);
    assertThat(sender.getMessages().get(0).body()).isNotNull();
    assertThat(sender.getMessages().get(0).htmlBody()).isNull();

    // Reset user preference
    setApiUser(admin);
    i.emailFormat = EmailFormat.HTML_PLAINTEXT;
    gApi.accounts().id(admin.getId().toString()).setPreferences(i);
  }

  @Test
  public void userReceivesHtmlAndPlaintextEmail() throws Exception {
    // Create change as admin and review as user
    PushOneCommit.Result r = createChange();
    setApiUser(user);
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.recommend());

    // Check that admin has received both HTML and plaintext content
    assertThat(sender.getMessages()).hasSize(1);
    assertThat(sender.getMessages().get(0).body()).isNotNull();
    assertThat(sender.getMessages().get(0).htmlBody()).isNotNull();
  }
}
