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

package com.google.gerrit.httpd;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.avatar.AvatarProvider;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.gerrit.server.plugins.StopPluginListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class AvatarTracker implements StartPluginListener, StopPluginListener,
    ReloadPluginListener {

  private boolean avatarSupport;
  private final DynamicItem<AvatarProvider> avatar;
  private final ConfigTracker configTracker;

  @Inject
  AvatarTracker(DynamicItem<AvatarProvider> avatar, ConfigTracker configTracker) {
    this.avatar = avatar;
    this.configTracker = configTracker;
  }

  @Override
  public void onStartPlugin(Plugin plugin) {
    update();
  }

  @Override
  public void onStopPlugin(Plugin plugin) {
    update();
  }

  @Override
  public void onReloadPlugin(Plugin oldPlugin, Plugin newPlugin) {
    update();
  }

  private void update() {
    boolean newAvatarSupport = avatar.get() != null;
    if (avatarSupport != newAvatarSupport) {
      avatarSupport = newAvatarSupport;
      configTracker.setUpdated();
    }
  }
}
