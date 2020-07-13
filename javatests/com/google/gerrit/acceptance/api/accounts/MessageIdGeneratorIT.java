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

package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.mail.MailMessage;
import com.google.gerrit.server.mail.send.MessageIdGenerator;
import com.google.gerrit.server.util.time.TimeUtil;
import java.time.Instant;
import javax.inject.Inject;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class MessageIdGeneratorIT extends AbstractDaemonTest {
  @Inject private MessageIdGenerator messageIdGenerator;

  @Test
  public void fromAccountUpdate() throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      String messageId = messageIdGenerator.fromAccountUpdate(admin.id()).id();
      String sha1 =
          repo.getRefDatabase().findRef(RefNames.refsUsers(admin.id())).getObjectId().getName();
      assertThat(sha1).isEqualTo(messageId);
    }
  }

  @Test
  public void fromChangeUpdate() throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      PushOneCommit.Result result = createChange();
      PatchSet.Id patchsetId = result.getChange().currentPatchSet().id();
      String messageId = messageIdGenerator.fromChangeUpdate(project, patchsetId).id();
      String sha1 =
          repo.getRefDatabase()
              .findRef(String.format("%smeta", patchsetId.changeId().toRefPrefix()))
              .getObjectId()
              .getName();
      assertThat(sha1).isEqualTo(messageId);
    }
  }

  @Test
  public void fromMailMessage() throws Exception {
    String id = "unique-id";
    MailMessage mailMessage =
        MailMessage.builder()
            .id(id)
            .from(Address.create("email@email.com"))
            .dateReceived(Instant.EPOCH)
            .subject("subject")
            .build();
    assertThat(messageIdGenerator.fromMailMessage(mailMessage).id()).isEqualTo(id + "-REJECTION");
  }

  @Test
  public void fromReasonAccountIdAndTimestamp() throws Exception {
    String reason = "reason";
    Instant timestamp = TimeUtil.now();
    assertThat(
            messageIdGenerator.fromReasonAccountIdAndTimestamp(reason, admin.id(), timestamp).id())
        .isEqualTo(reason + "-" + admin.id().toString() + "-" + timestamp.toString());
  }
}
