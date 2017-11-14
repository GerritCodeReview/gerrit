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

package com.google.gerrit.server.mail;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.mail.Address;
import com.google.gerrit.mail.MailMessage;
import com.google.gerrit.testing.GerritBaseTests;
import java.time.Instant;
import org.junit.Test;

public class AutoReplyMailFilterTest extends GerritBaseTests {

  private AutoReplyMailFilter autoReplyMailFilter = new AutoReplyMailFilter();

  @Test
  public void acceptsHumanReply() {
    MailMessage.Builder b = createChangeAndReplyByEmail();
    assertThat(autoReplyMailFilter.shouldProcessMessage(b.build())).isTrue();
  }

  @Test
  public void discardsBulk() {
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
  public void discardsAutoSubmitted() {
    MailMessage.Builder b = createChangeAndReplyByEmail();
    b.addAdditionalHeader("Auto-Submitted: yes");
    assertThat(autoReplyMailFilter.shouldProcessMessage(b.build())).isFalse();

    b = createChangeAndReplyByEmail();
    b.addAdditionalHeader("Auto-Submitted: no");
    assertThat(autoReplyMailFilter.shouldProcessMessage(b.build())).isTrue();
  }

  private MailMessage.Builder createChangeAndReplyByEmail() {
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
