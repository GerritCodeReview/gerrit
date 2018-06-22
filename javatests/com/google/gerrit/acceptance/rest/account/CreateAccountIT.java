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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth8.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import org.junit.Test;

public class CreateAccountIT extends AbstractDaemonTest {
  @Test
  public void createAccountRestApi() throws Exception {
    AccountInput input = new AccountInput();
    input.username = "foo";
    assertThat(accountCache.getByUsername(input.username)).isEmpty();
    RestResponse r = adminRestSession.put("/accounts/" + input.username, input);
    r.assertCreated();
    assertThat(accountCache.getByUsername(input.username)).isPresent();
  }
}
