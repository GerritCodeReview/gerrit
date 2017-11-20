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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SystemReaderInstaller implements LifecycleListener {
  Logger log = LoggerFactory.getLogger(SystemReaderInstaller.class);

  private final JGitConfigProvider jgitConfig;
  private final SitePaths site;

  @Inject
  SystemReaderInstaller(JGitConfigProvider jgitConfig, SitePaths site) {
    this.jgitConfig = jgitConfig;
    this.site = site;
  }

  @Override
  public void start() {
    SystemReader.setInstance(customReader());
    log.info("Set JGit's SystemReader to read system config from {}", site.jgit_config);
  }

  @Override
  public void stop() {}

  private SystemReader customReader() {
    SystemReader current = SystemReader.getInstance();

    return new SystemReader() {
      @Override
      public String getHostname() {
        return current.getHostname();
      }

      @Override
      public String getenv(String variable) {
        return current.getenv(variable);
      }

      @Override
      public String getProperty(String key) {
        return current.getProperty(key);
      }

      @Override
      public FileBasedConfig openUserConfig(Config parent, FS fs) {
        return current.openSystemConfig(parent, fs);
      }

      @Override
      public FileBasedConfig openSystemConfig(Config parent, FS fs) {
        return jgitConfig.get();
      }

      @Override
      public long getCurrentTime() {
        return current.getCurrentTime();
      }

      @Override
      public int getTimezone(long when) {
        return current.getTimezone(when);
      }
    };
  }
}
