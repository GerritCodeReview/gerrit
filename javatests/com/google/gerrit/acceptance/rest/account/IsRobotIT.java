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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.inject.Inject;
import org.junit.Test;

@NoHttpd
public class IsRobotIT extends AbstractDaemonTest {
  @Inject private AccountOperations accountOperations;

  @Test
  public void isHumanByDefault() throws Exception {
    assertThat(gApi.accounts().self().detail().isRobot).isNull();
  }

  @Test
  public void getIsRobot() throws Exception {
    accountOperations.account(user.id()).forUpdate().isRobot(true).update();
    assertThat(gApi.accounts().id(user.id().get()).getIsRobot()).isTrue();
    accountOperations.account(user.id()).forUpdate().isRobot(false).update();
    assertThat(gApi.accounts().id(user.id().get()).getIsRobot()).isFalse();
  }

  @Test
  public void setIsRobot() throws Exception {
    gApi.accounts().self().setIsRobot(true);
    assertThat(gApi.accounts().self().getIsRobot()).isTrue();
    gApi.accounts().self().setIsRobot(false);
    assertThat(gApi.accounts().self().getIsRobot()).isFalse();
  }

  @Test
  public void createRobot() throws Exception {
    AccountInfo robot = createAccount(true);
    assertThat(gApi.accounts().id(robot._accountId).getIsRobot()).isTrue();
  }

  @Test
  public void createHuman() throws Exception {
    AccountInfo user = createAccount(null);
    assertThat(gApi.accounts().id(user._accountId).getIsRobot()).isFalse();
  }

  private AccountInfo createAccount(Boolean isRobot) throws Exception {
    AccountInput in = new AccountInput();
    in.isRobot = isRobot;
    in.username = Long.toString(System.nanoTime());
    return gApi.accounts().create(in).get();
  }
}
