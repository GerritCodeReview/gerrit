// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import org.junit.Test;

@UseSsh
public class CreateAccountIT extends AbstractDaemonTest {
  @Test
  @GerritConfig(name = "auth.duplicatesProhibited", value = "false")
  public void duplicateAccountsWithDuplicatesProhibitedFalse() throws Exception {
    String newAccountName = "johndoe";
    assertThat(accountCache.getByUsername(newAccountName)).isEmpty();
    adminSshSession.exec("gerrit create-account " + newAccountName);
    adminSshSession.assertSuccess();
    assertThat(accountCache.getByUsername(newAccountName)).isPresent();

    String newDuplicateAccountName = "JohnDoe";
    assertThat(accountCache.getByUsername(newDuplicateAccountName)).isEmpty();
    adminSshSession.exec("gerrit create-account " + newDuplicateAccountName);
    adminSshSession.assertSuccess();
    assertThat(accountCache.getByUsername(newDuplicateAccountName)).isPresent();
  }

  @Test
  @GerritConfig(name = "auth.duplicatesProhibited", value = "true")
  public void noDuplicateAccountsWithDuplicatesProhibitedTrue() throws Exception {
    String newAccountName = "johndoe";
    assertThat(accountCache.getByUsername(newAccountName)).isEmpty();
    adminSshSession.exec("gerrit create-account " + newAccountName);
    adminSshSession.assertSuccess();
    assertThat(accountCache.getByUsername(newAccountName)).isPresent();

    String newDuplicateAccountName = "JohnDoe";
    assertThat(accountCache.getByUsername(newDuplicateAccountName)).isEmpty();
    adminSshSession.exec("gerrit create-account " + newDuplicateAccountName);
    adminSshSession.assertFailure();
    assertThat(accountCache.getByUsername(newDuplicateAccountName)).isEmpty();
  }
}
