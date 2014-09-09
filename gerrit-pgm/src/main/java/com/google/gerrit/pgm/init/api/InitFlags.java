// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm.init.api;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import java.io.IOException;
import java.util.List;

/** Global variables used by the 'init' command. */
@Singleton
public class InitFlags {
  /** Recursively delete the site path if initialization fails. */
  public boolean deleteOnFailure;

  /** Run the daemon (and open the web UI in a browser) after initialization. */
  public boolean autoStart;

  /** Skip plugins */
  public boolean skipPlugins;

  public final FileBasedConfig cfg;
  public final SecureStore sec;
  public final List<String> installPlugins;

  @VisibleForTesting
  @Inject
  public InitFlags(final SitePaths site,
      final SecureStore secureStore,
      final @InstallPlugins List<String> installPlugins) throws IOException,
      ConfigInvalidException {
    sec = secureStore;
    this.installPlugins = installPlugins;
    cfg = new FileBasedConfig(site.gerrit_config, FS.DETECTED);

    cfg.load();
  }
}
