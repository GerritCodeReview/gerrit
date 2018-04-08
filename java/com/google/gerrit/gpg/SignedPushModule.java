// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.EnableSignedPush;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReceivePackInitializer;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.PreReceiveHookChain;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.SignedPushConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SignedPushModule extends AbstractModule {
  private static final Logger log = LoggerFactory.getLogger(SignedPushModule.class);

  @Override
  protected void configure() {
    if (!BouncyCastleUtil.havePGP()) {
      throw new ProvisionException("Bouncy Castle PGP not installed");
    }
    bind(PublicKeyStore.class).toProvider(StoreProvider.class);
    DynamicSet.bind(binder(), ReceivePackInitializer.class).to(Initializer.class);
  }

  @Singleton
  private static class Initializer implements ReceivePackInitializer {
    private final SignedPushConfig signedPushConfig;
    private final SignedPushPreReceiveHook hook;
    private final ProjectCache projectCache;

    @Inject
    Initializer(
        @GerritServerConfig Config cfg,
        @EnableSignedPush boolean enableSignedPush,
        SignedPushPreReceiveHook hook,
        ProjectCache projectCache) {
      this.hook = hook;
      this.projectCache = projectCache;

      if (enableSignedPush) {
        String seed = cfg.getString("receive", null, "certNonceSeed");
        if (Strings.isNullOrEmpty(seed)) {
          seed = randomString(64);
        }
        signedPushConfig = new SignedPushConfig();
        signedPushConfig.setCertNonceSeed(seed);
        signedPushConfig.setCertNonceSlopLimit(
            cfg.getInt("receive", null, "certNonceSlop", 5 * 60));
      } else {
        signedPushConfig = null;
      }
    }

    @Override
    public void init(Project.NameKey project, ReceivePack rp) {
      ProjectState ps = projectCache.get(project);
      if (!ps.is(BooleanProjectConfig.ENABLE_SIGNED_PUSH)) {
        rp.setSignedPushConfig(null);
        return;
      } else if (signedPushConfig == null) {
        log.error(
            "receive.enableSignedPush is true for project {} but"
                + " false in gerrit.config, so signed push verification is"
                + " disabled",
            project.get());
        rp.setSignedPushConfig(null);
        return;
      }
      rp.setSignedPushConfig(signedPushConfig);

      List<PreReceiveHook> hooks = new ArrayList<>(3);
      if (ps.is(BooleanProjectConfig.REQUIRE_SIGNED_PUSH)) {
        hooks.add(SignedPushPreReceiveHook.Required.INSTANCE);
      }
      hooks.add(hook);
      hooks.add(rp.getPreReceiveHook());
      rp.setPreReceiveHook(PreReceiveHookChain.newChain(hooks));
    }
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

  private static String randomString(int len) {
    Random random;
    try {
      random = SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append((char) random.nextInt());
    }
    return sb.toString();
  }
}
