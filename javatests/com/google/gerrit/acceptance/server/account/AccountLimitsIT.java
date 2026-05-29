// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountLimits;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.junit.Test;

public class AccountLimitsIT extends AbstractDaemonTest {

  @Inject private AccountLimits.Factory accountLimitsFactory;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private Provider<CurrentUser> currentUserProvider;

  @Test
  public void shouldIgnoreQueryLimitForInternalUser() throws Exception {
    requestScopeOperations.setApiUserInternal();
    AccountLimits objectUnderTest = accountLimitsFactory.create(currentUserProvider.get());

    assertThat(objectUnderTest.getQueryLimit()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  public void shouldDefaultValueForReqularUser() {
    AccountLimits objectUnderTest = accountLimitsFactory.create(currentUserProvider.get());

    assertThat(objectUnderTest.getQueryLimit()).isEqualTo(GlobalCapability.DEFAULT_MAX_QUERY_LIMIT);
  }
}
