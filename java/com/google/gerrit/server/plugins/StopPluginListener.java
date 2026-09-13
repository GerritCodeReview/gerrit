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

package com.google.gerrit.server.plugins;

/** Broadcasts event indicating a plugin was unloaded. */
public interface StopPluginListener {

  /**
   * Called when the plugin is being stopped, but its GuiceEnvironment is still accessible.
   *
   * @param plugin {@link Plugin} about to be stopped
   */
  default void beforeStopPlugin(Plugin plugin) {}

  /**
   * Called when the plugin has been stopped, including its GuiceEnvironment.
   *
   * @param plugin {@link Plugin} been stopped
   * @deprecated use {@link StopPluginListener#beforeStopPlugin(Plugin)} instead
   */
  @Deprecated(forRemoval = true)
  default void onStopPlugin(Plugin plugin) {}
  ;
}
