// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.extensions.registration;

import com.google.gerrit.common.Nullable;
import com.google.inject.Provider;

public class PluginEntry<T> {
  private final String pluginName;
  private final @Nullable String exportName;
  private final Provider<T> provider;

  protected PluginEntry(String pluginName, Provider<T> provider) {
    this(pluginName, null, provider);
  }

  protected PluginEntry(String pluginName, @Nullable String exportName, Provider<T> provider) {
    this.pluginName = pluginName;
    this.exportName = exportName;
    this.provider = provider;
  }

  public String getPluginName() {
    return pluginName;
  }

  public String getExportName() {
    return exportName;
  }

  public Provider<T> getProvider() {
    return provider;
  }

  public T get() {
    return provider.get();
  }
}
