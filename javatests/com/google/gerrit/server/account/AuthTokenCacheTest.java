// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.cache.CacheBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.inject.Provider;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AuthTokenCacheTest {
  private static final Account.Id ACCOUNT_ID = Account.id(1);
  private static final AllUsersName ALL_USERS = new AllUsersName(AllUsersNameProvider.DEFAULT);
  private static final String USER_REF = RefNames.refsUsers(ACCOUNT_ID);
  private static final String PWD = "secret";

  private TestRepository<InMemoryRepositoryManager.Repo> repo;
  private AuthTokenCache.Loader cacheLoader;
  private AuthTokenCache cache;

  @Mock private AuthConfig authConfig;

  @Before
  public void setUp() throws Exception {
    InMemoryRepositoryManager repoManager = new InMemoryRepositoryManager();
    repo = new TestRepository<>(repoManager.createRepository(ALL_USERS));
    Provider<Repository> allUsersRepository =
        () -> {
          Repository repository = repo.getRepository();
          repository.incrementOpen();
          return repository;
        };
    DirectAuthTokenAccessor directAccessor =
        new DirectAuthTokenAccessor(
            ALL_USERS,
            accountId -> new VersionedAuthTokens(repoManager, ALL_USERS, authConfig, accountId),
            allUsersRepository,
            null,
            null);
    cacheLoader = new AuthTokenCache.Loader(directAccessor);
    cache = new AuthTokenCache(CacheBuilder.newBuilder().build(cacheLoader), allUsersRepository);
  }

  @After
  public void tearDown() {
    repo.close();
  }

  @Test
  public void loadTokenFromAccount() throws Exception {
    AuthToken token = AuthToken.createWithPlainToken("token", PWD);
    ObjectId revision = writeTokens(token);
    writeTokens();

    assertThat(cacheLoader.load(new AuthTokenCache.Key(ACCOUNT_ID, revision)))
        .containsExactly(token);
  }

  @Test
  public void seesTokenAddedAfterCachingEmptyList() throws Exception {
    writeTokens();
    assertThat(cache.get(ACCOUNT_ID)).isEmpty();

    AuthToken token = AuthToken.createWithPlainToken("token", PWD);
    writeTokens(token);

    assertThat(cache.get(ACCOUNT_ID)).containsExactly(token);
  }

  @Test
  public void rejectsTokenDeletedInGit() throws Exception {
    writeTokens(AuthToken.createWithPlainToken("token", PWD));
    AuthTokenVerifier verifier = new AuthTokenVerifier(new CachingAuthTokenAccessor(cache, null));
    assertThat(verifier.checkToken(ACCOUNT_ID, PWD)).isTrue();

    writeTokens();

    assertThat(verifier.checkToken(ACCOUNT_ID, PWD)).isFalse();
  }

  @CanIgnoreReturnValue
  private ObjectId writeTokens(AuthToken... tokens) throws Exception {
    Config config = new Config();
    for (AuthToken token : tokens) {
      config.setString("token", token.id(), "hash", token.hashedToken());
    }
    return repo.branch(USER_REF)
        .commit()
        .add(VersionedAuthTokens.FILE_NAME, config.toText())
        .create();
  }
}
