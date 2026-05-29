// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.util.git;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

public class DelegateSystemReader extends SystemReader {
  private final SystemReader delegate;

  public DelegateSystemReader(SystemReader delegate) {
    this.delegate = delegate;
  }

  @Override
  public String getHostname() {
    return delegate.getHostname();
  }

  @Override
  public String getenv(String variable) {
    return delegate.getenv(variable);
  }

  @Override
  public String getProperty(String key) {
    return delegate.getProperty(key);
  }

  @Override
  public FileBasedConfig openUserConfig(Config parent, FS fs) {
    return delegate.openUserConfig(parent, fs);
  }

  @Override
  public FileBasedConfig openSystemConfig(Config parent, FS fs) {
    return delegate.openSystemConfig(parent, fs);
  }

  @Override
  public FileBasedConfig openJGitConfig(Config parent, FS fs) {
    return delegate.openJGitConfig(parent, fs);
  }

  @Override
  public long getCurrentTime() {
    return delegate.getCurrentTime();
  }

  @Override
  public int getTimezone(long when) {
    return delegate.getTimezone(when);
  }
}
