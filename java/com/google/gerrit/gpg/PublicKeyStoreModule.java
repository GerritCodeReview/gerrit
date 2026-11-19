// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.gpg;

import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import java.io.IOException;
import org.eclipse.jgit.lib.Repository;

/** Shared key-store bindings; does not initialize signed-push enforcement. */
class PublicKeyStoreModule extends AbstractModule {
  @Override
  protected void configure() {
    OptionalBinder.newOptionalBinder(binder(), PublicKeyStore.class)
        .setBinding()
        .toProvider(StoreProvider.class);
  }

  @Singleton
  private static class StoreProvider implements Provider<PublicKeyStore> {
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsers;

    @Inject
    StoreProvider(GitRepositoryManager repoManager, AllUsersName allUsers) {
      this.repoManager = repoManager;
      this.allUsers = allUsers;
    }

    @Override
    public PublicKeyStore get() {
      final Repository repo;
      try {
        repo = repoManager.openRepository(allUsers);
      } catch (IOException e) {
        throw new ProvisionException("Cannot open " + allUsers, e);
      }
      return new PublicKeyStore(repo) {
        @Override
        public void close() {
          try {
            super.close();
          } finally {
            repo.close();
          }
        }
      };
    }
  }
}
