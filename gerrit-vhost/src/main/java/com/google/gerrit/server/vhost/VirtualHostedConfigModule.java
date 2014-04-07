// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.vhost;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.DisabledChangeHooks;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.httpd.GerritUiOptions;
import com.google.gerrit.httpd.HttpCanonicalWebUrlProvider;
import com.google.gerrit.server.config.AuthConfigModule;
import com.google.gerrit.server.config.CanonicalWebUrlModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.config.TrackingFootersProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.schema.SchemaModule;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.IO;

import java.io.File;
import java.io.IOException;

class VirtualHostedConfigModule extends AbstractModule {
  private final String siteName;

  VirtualHostedConfigModule(String siteName) {
    this.siteName = checkNotNull(siteName);
  }

  @Override
  protected void configure() {
    bind(SitePaths.class);
    bind(GitRepositoryManager.class).to(LocalDiskRepositoryManager.class);

    // Core modules used by Gerrit Code Review
    install(new SchemaModule());
    install(new AuthConfigModule());
    bind(TrackingFooters.class).toProvider(TrackingFootersProvider.class).in(SINGLETON);
    bind(ChangeHooks.class).to(DisabledChangeHooks.class);

    // We always have the HttpServletRequest on hand for URL generation.
    install(new CanonicalWebUrlModule() {
      @Override
      protected Class<? extends Provider<String>> provider() {
        return HttpCanonicalWebUrlProvider.class;
      }
    });
  }

  @Provides
  @SiteName
  String getSiteName() {
    return siteName;
  }

  @Provides
  ServerInformation createServerInformation() {
    return new ServerInformation() {
      @Override
      public ServerInformation.State getState() {
        return ServerInformation.State.RUNNING;
      }
    };
  }

  @Provides
  @Singleton
  @SitePath
  File findSitePath(GlobalDataModule globals) {
    return globals.getSitePath(siteName);
  }

  @Provides
  @Singleton
  @GerritServerConfig
  Config getServerConfig(GlobalDataModule globals, SitePaths paths) {
    Config cfg = globals.createSiteConfig(siteName);
    if (paths.gerrit_config.isFile()) {
      try {
        cfg = new Config(cfg);
        cfg.fromText(new String(IO.readFully(paths.gerrit_config), "UTF-8"));
      } catch (ConfigInvalidException err) {
        throw new ProvisionException(String.format(
            "Cannot read %s", paths.gerrit_config.getPath()), err);
      } catch (IOException err) {
        throw new ProvisionException(String.format(
            "Cannot read %s", paths.gerrit_config.getPath()), err);
      }
    }
    return cfg;
  }

  @Provides
  GerritUiOptions getGerritUiOptions() {
    return new GerritUiOptions(false);
  }
}
