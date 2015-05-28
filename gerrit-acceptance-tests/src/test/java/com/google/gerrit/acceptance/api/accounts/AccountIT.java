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

package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;

import org.junit.Test;

import java.util.List;

public class AccountIT extends AbstractDaemonTest {
  @Test
  public void get() throws Exception {
    AccountInfo info = gApi
        .accounts()
        .id("admin")
        .get();
    assertThat(info.name).isEqualTo("Administrator");
    assertThat(info.email).isEqualTo("admin@example.com");
    assertThat(info.username).isEqualTo("admin");
  }

  @Test
  public void self() throws Exception {
    AccountInfo info = gApi
        .accounts()
        .self()
        .get();
    assertThat(info.name).isEqualTo("Administrator");
    assertThat(info.email).isEqualTo("admin@example.com");
    assertThat(info.username).isEqualTo("admin");
  }

  @Test
  public void starUnstarChange() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();
    gApi.accounts()
        .self()
        .starChange(triplet);
    assertThat(info(triplet).starred).isTrue();
    gApi.accounts()
        .self()
        .unstarChange(triplet);
    assertThat(info(triplet).starred).isNull();
  }

  @Test
  public void suggestAccounts() throws Exception {
    String adminUsername = "admin";
    List<AccountInfo> result = gApi.accounts()
        .suggestAccounts().withQuery(adminUsername).get();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).username).isEqualTo(adminUsername);

    List<AccountInfo> resultShortcutApi = gApi.accounts()
        .suggestAccounts(adminUsername).get();
    assertThat(resultShortcutApi).hasSize(result.size());

    List<AccountInfo> emptyResult = gApi.accounts()
        .suggestAccounts("unknown").get();
    assertThat(emptyResult).isEmpty();
  }

  @Test
  public void addEmail() throws Exception {
    List<String> emails = ImmutableList.of(
        "new.email@example.com", "new.email@example.systems");
    for (String email : emails) {
      EmailInput input = new EmailInput();
      input.email = email;
      input.noConfirmation = true;
      gApi.accounts().self().addEmail(input);
    }
  }

  @Test
  public void addInvalidEmail() throws Exception {
    EmailInput input  = new EmailInput();
    input.email = "invalid@";
    input.noConfirmation = true;

    exception.expect(BadRequestException.class);
    exception.expectMessage("invalid email address");
    gApi.accounts().self().addEmail(input);
  }
}
