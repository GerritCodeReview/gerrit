// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.git.DelegateSystemReader;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

@Singleton
public class SystemReaderInstaller implements LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SitePaths site;

  @Inject
  SystemReaderInstaller(SitePaths site) {
    this.site = site;
  }

  @Override
  public void start() {
    SystemReader.setInstance(customReader());
    logger.atInfo().log("Set JGit's SystemReader to read system config from %s", site.jgit_config);
  }

  @Override
  public void stop() {}

  private SystemReader customReader() {
    SystemReader current = SystemReader.getInstance();

    return new DelegateSystemReader(current) {
      @Override
      public FileBasedConfig openSystemConfig(Config parent, FS fs) {
        return new FileBasedConfig(parent, site.jgit_config.toFile(), FS.DETECTED);
      }
    };
  }
}
