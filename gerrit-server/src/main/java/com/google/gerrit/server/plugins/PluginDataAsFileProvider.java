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

package com.google.gerrit.server.plugins;

import com.google.gerrit.extensions.annotations.PluginData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.File;
import java.nio.file.Path;

@Singleton
class PluginDataAsFileProvider implements Provider<File> {
  private final Provider<Path> pathProvider;

  @Inject
  PluginDataAsFileProvider(@PluginData Provider<Path> pathProvider) {
    this.pathProvider = pathProvider;
  }

  @Override
  public File get() {
    return pathProvider.get().toFile();
  }
}
