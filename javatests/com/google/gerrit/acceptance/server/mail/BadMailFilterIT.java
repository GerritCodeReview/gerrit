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

import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.BadMailFilter;
import com.google.gerrit.server.mail.receive.MailMessage;
import com.google.inject.Inject;
import java.time.Instant;
import org.junit.Test;

@NoHttpd
public class BadMailFilterIT extends AbstractMailIT {

  @Inject private BadMailFilter badMailFilter;

  @Test
  // @TODO: fix config
  @GerritConfig(name = "receiveemail.filter.mode", value = "OFF")
  @GerritConfig(
    name = "receiveemail.filter.patterns",
    values = {".+@example\\.com", "a@b\\.com"}
  )
  public void listFilterOff() throws Exception {
    MailMessage.Builder b = createChangeAndReplyByEmail();
    b.addCc(new Address("demo@demo.org"));
    assertThat(badMailFilter.shouldProcessMessage(b.build())).isFalse();
  }

  @Test
  // @TODO: fix config
  @GerritConfig(name = "receiveemail.filter.mode", value = "OFF")
  @GerritConfig(
    name = "receiveemail.filter.patterns",
    values = {".+@example\\.com", "a@b\\.com"}
  )
  public void discardsBulk() throws Exception {
    MailMessage.Builder b = createChangeAndReplyByEmail();
    b.addAdditionalHeader("Precedence: bulk");
    assertThat(badMailFilter.shouldProcessMessage(b.build())).isFalse();

    b = createChangeAndReplyByEmail();
    b.addAdditionalHeader("Precedence: list");
    assertThat(badMailFilter.shouldProcessMessage(b.build())).isFalse();

    b = createChangeAndReplyByEmail();
    b.addAdditionalHeader("Precedence: junk");
    assertThat(badMailFilter.shouldProcessMessage(b.build())).isFalse();
  }

  @Test
  // @TODO: fix config
  @GerritConfig(name = "receiveemail.filter.mode", value = "OFF")
  @GerritConfig(
    name = "receiveemail.filter.patterns",
    values = {".+@example\\.com", "a@b\\.com"}
  )
  public void discardsAutoSubmitted() throws Exception {
    MailMessage.Builder b = createChangeAndReplyByEmail();
    b.addAdditionalHeader("Auto-Submitted: yes");
    assertThat(badMailFilter.shouldProcessMessage(b.build())).isFalse();

    b = createChangeAndReplyByEmail();
    b.addAdditionalHeader("Auto-Submitted: no");
    assertThat(badMailFilter.shouldProcessMessage(b.build())).isTrue();
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
