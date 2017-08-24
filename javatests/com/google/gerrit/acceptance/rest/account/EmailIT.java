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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toSet;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.common.EmailInfo;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class EmailIT extends AbstractDaemonTest {

  @Test
  public void addEmail() throws Exception {
    String email = "foo.bar@example.com";
    assertThat(getEmails()).doesNotContain(email);

    createEmail(email);
    assertThat(getEmails()).contains(email);
  }

  @Test
  public void addUrlEncodedEmail() throws Exception {
    String email = "foo.bar2@example.com";
    assertThat(getEmails()).doesNotContain(email);

    createEmail(email.replace("@", "%40"));
    assertThat(getEmails()).contains(email);
  }

  @Test
  public void deleteEmail() throws Exception {
    String email = "foo.baz@example.com";
    assertThat(getEmails()).doesNotContain(email);

    createEmail(email);
    assertThat(getEmails()).contains(email);

    RestResponse r = adminRestSession.delete("/accounts/self/emails/" + email);
    r.assertNoContent();
    assertThat(getEmails()).doesNotContain(email);
  }

  @Test
  public void deleteUrlEncodedEmail() throws Exception {
    String email = "foo.baz2@example.com";
    assertThat(getEmails()).doesNotContain(email);

    createEmail(email);
    assertThat(getEmails()).contains(email);

    RestResponse r = adminRestSession.delete("/accounts/self/emails/" + email.replace("@", "%40"));
    r.assertNoContent();
    assertThat(getEmails()).doesNotContain(email);
  }

  private Set<String> getEmails() throws Exception {
    RestResponse r = adminRestSession.get("/accounts/self/emails");
    r.assertOK();
    List<EmailInfo> emails =
        newGson().fromJson(r.getReader(), new TypeToken<List<EmailInfo>>() {}.getType());
    return emails.stream().map(e -> e.email).collect(toSet());
  }

  private void createEmail(String email) throws Exception {
    EmailInput input = new EmailInput();
    input.noConfirmation = true;
    RestResponse r = adminRestSession.put("/accounts/self/emails/" + email, input);
    r.assertCreated();
  }
}
