// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.sshd;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.InvalidSshKeyException;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.ssh.SshKeyCreator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;

public class SshKeyCreatorImpl implements SshKeyCreator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public AccountSshKey create(Account.Id accountId, int seq, String encoded)
      throws InvalidSshKeyException {
    try {
      AccountSshKey key = AccountSshKey.create(accountId, seq, SshUtil.toOpenSshPublicKey(encoded));
      SshUtil.parse(key);
      return key;
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new InvalidSshKeyException();

    } catch (NoSuchProviderException e) {
      logger.atSevere().withCause(e).log("Cannot parse SSH key");
      throw new InvalidSshKeyException();
    }
  }
}
