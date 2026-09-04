// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.plugins;

import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.GerritIsReplica;
import com.google.gerrit.server.config.GerritIsReplicaProvider;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Copies critical objects from the {@code dbInjector} into a plugin.
 *
 * <p>Most explicit bindings are copied automatically from the cfgInjector and sysInjector to be
 * made available to a plugin's private world. This module is necessary to get things bound in the
 * dbInjector that are not otherwise easily available, but that a plugin author might expect to
 * exist.
 */
@Singleton
class CopyConfigModule extends AbstractModule {
  private final Path sitePath;
  private final SitePaths sitePaths;
  private final TrackingFooters trackingFooters;
  private final Config gerritServerConfig;
  private final GitRepositoryManager gitRepositoryManager;
  private final String anonymousCowardName;
  private final GerritPersonIdentProvider serverIdentProvider;
  private final SecureStore secureStore;
  private final GerritIsReplicaProvider isReplicaProvider;

  @Inject
  CopyConfigModule(
      @SitePath Path sitePath,
      SitePaths sitePaths,
      TrackingFooters trackingFooters,
      @GerritServerConfig Config gerritServerConfig,
      GitRepositoryManager gitRepositoryManager,
      @AnonymousCowardName String anonymousCowardName,
      GerritPersonIdentProvider serverIdentProvider,
      SecureStore secureStore,
      GerritIsReplicaProvider isReplicaProvider) {
    this.sitePath = sitePath;
    this.sitePaths = sitePaths;
    this.trackingFooters = trackingFooters;
    this.gerritServerConfig = gerritServerConfig;
    this.gitRepositoryManager = gitRepositoryManager;
    this.anonymousCowardName = anonymousCowardName;
    this.serverIdentProvider = serverIdentProvider;
    this.secureStore = secureStore;
    this.isReplicaProvider = isReplicaProvider;
  }

  @Override
  protected void configure() {
    if (sitePath != null) {
      bind(Path.class).annotatedWith(SitePath.class).toInstance(sitePath);
    }
    if (sitePaths != null) {
      bind(SitePaths.class).toInstance(sitePaths);
    }
    if (trackingFooters != null) {
      bind(TrackingFooters.class).toInstance(trackingFooters);
    }
    if (gerritServerConfig != null) {
      bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(gerritServerConfig);
    }
    if (gitRepositoryManager != null) {
      bind(GitRepositoryManager.class).toInstance(gitRepositoryManager);
    }
    if (anonymousCowardName != null) {
      bind(String.class).annotatedWith(AnonymousCowardName.class).toInstance(anonymousCowardName);
    }
    if (serverIdentProvider != null) {
      bind(PersonIdent.class)
          .annotatedWith(GerritPersonIdent.class)
          .toProvider(serverIdentProvider);
    }
    if (secureStore != null) {
      bind(SecureStore.class).toInstance(secureStore);
    }
    if (isReplicaProvider != null) {
      bind(Boolean.class).annotatedWith(GerritIsReplica.class).toProvider(isReplicaProvider);
    }
  }
}
