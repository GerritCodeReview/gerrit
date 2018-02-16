// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.AutoReplyMailFilter;
import com.google.gerrit.server.mail.receive.MailMessage;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.inject.Inject;
import java.time.Instant;
import org.junit.Test;

public class AutoReplyMailFilterTest extends GerritBaseTests {

  @Inject
  private AutoReplyMailFilter autoReplyMailFilter;

  @Test
  public void listFilterOff() throws Exception {
    MailMessage.Builder b = createChangeAndReplyByEmail();
    b.addCc(new Address("demo@demo.org"));
    assertThat(autoReplyMailFilter.shouldProcessMessage(b.build())).isFalse();
  }

  @Test
  public void discardsBulk() throws Exception {
    MailMessage.Builder b = createChangeAndReplyByEmail();
    b.addAdditionalHeader("Precedence: bulk");
    assertThat(autoReplyMailFilter.shouldProcessMessage(b.build())).isFalse();

    b = createChangeAndReplyByEmail();
    b.addAdditionalHeader("Precedence: list");
    assertThat(autoReplyMailFilter.shouldProcessMessage(b.build())).isFalse();

    b = createChangeAndReplyByEmail();
    b.addAdditionalHeader("Precedence: junk");
    assertThat(autoReplyMailFilter.shouldProcessMessage(b.build())).isFalse();
  }

  @Test
  public void discardsAutoSubmitted() throws Exception {
    MailMessage.Builder b = createChangeAndReplyByEmail();
    b.addAdditionalHeader("Auto-Submitted: yes");
    assertThat(autoReplyMailFilter.shouldProcessMessage(b.build())).isFalse();

    b = createChangeAndReplyByEmail();
    b.addAdditionalHeader("Auto-Submitted: no");
    assertThat(autoReplyMailFilter.shouldProcessMessage(b.build())).isTrue();
  }

  private MailMessage.Builder createChangeAndReplyByEmail() throws Exception {
    // Build Message
    MailMessage.Builder b = MailMessage.builder();
    b.id("some id");
    b.from(new Address("admim@example.com"));
    b.addTo(new Address("gerrit@my-company.com")); // Not evaluated
    b.subject("");
    b.dateReceived(Instant.now());
    b.textContent("I am currently out of office, please leave a code review after the beep.");
    return b;
  }
}
