// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.plugins.actions;

import com.google.common.base.Preconditions;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.binder.LinkedBindingBuilder;

public abstract class ActionPluginModule extends AbstractModule {
  private String pluginName;

  @Inject
  void setPluginName(@PluginName String name) {
    this.pluginName = name;
  }

  @Override
  protected final void configure() {
    Preconditions
        .checkState(pluginName != null, "@PluginName must be provided");
    configureActions();
  }

  protected abstract void configureActions();

  protected LinkedBindingBuilder<Action> action(String action) {
    return bind(Actions.key(action));
  }
}
