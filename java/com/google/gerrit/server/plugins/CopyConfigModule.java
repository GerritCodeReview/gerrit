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

import com.google.gerrit.config.AnonymousCowardName;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePath;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.config.TrackingFooters;
import com.google.gerrit.extensions.securestore.SecureStore;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
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
  @Inject @SitePath private Path sitePath;

  @Provides
  @SitePath
  Path getSitePath() {
    return sitePath;
  }

  @Inject private SitePaths sitePaths;

  @Provides
  SitePaths getSitePaths() {
    return sitePaths;
  }

  @Inject private TrackingFooters trackingFooters;

  @Provides
  TrackingFooters getTrackingFooters() {
    return trackingFooters;
  }

  @Inject @GerritServerConfig private Config gerritServerConfig;

  @Provides
  @GerritServerConfig
  Config getGerritServerConfig() {
    return gerritServerConfig;
  }

  @Inject private SchemaFactory<ReviewDb> schemaFactory;

  @Provides
  SchemaFactory<ReviewDb> getSchemaFactory() {
    return schemaFactory;
  }

  @Inject private GitRepositoryManager gitRepositoryManager;

  @Provides
  GitRepositoryManager getGitRepositoryManager() {
    return gitRepositoryManager;
  }

  @Inject @AnonymousCowardName private String anonymousCowardName;

  @Provides
  @AnonymousCowardName
  String getAnonymousCowardName() {
    return anonymousCowardName;
  }

  @Inject private GerritPersonIdentProvider serverIdentProvider;

  @Provides
  @GerritPersonIdent
  PersonIdent getServerIdent() {
    return serverIdentProvider.get();
  }

  @Inject private SecureStore secureStore;

  @Provides
  SecureStore getSecureStore() {
    return secureStore;
  }

  @Inject
  CopyConfigModule() {}

  @Override
  protected void configure() {}
}
