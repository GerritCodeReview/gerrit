// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.ssh;

import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;

@Singleton
public class NoSshKeyCache implements SshKeyCache, SshKeyCreator {

  public static Module module() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(SshKeyCache.class).to(NoSshKeyCache.class);
        bind(SshKeyCreator.class).to(NoSshKeyCache.class);
      }
    };
  }

  @Override
  public void evict(String username) {}

  @Override
  public AccountSshKey create(AccountSshKey.Id id, String encoded) throws InvalidSshKeyException {
    throw new InvalidSshKeyException();
  }
}
