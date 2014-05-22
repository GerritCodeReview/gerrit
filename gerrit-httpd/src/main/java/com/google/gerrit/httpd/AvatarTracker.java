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

import com.google.gerrit.server.avatar.AvatarProvider;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.gerrit.server.plugins.StopPluginListener;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import java.util.Map;

@Singleton
public class AvatarTracker implements StartPluginListener, StopPluginListener,
    ReloadPluginListener {

  private boolean avatarSupport;

  @Override
  public void onStartPlugin(Plugin plugin) {
    if (!avatarSupport && hasAvatarProvider(plugin)) {
      avatarSupport = true;
    }
  }

  @Override
  public void onStopPlugin(Plugin plugin) {
    if (avatarSupport && hasAvatarProvider(plugin)) {
      avatarSupport = false;
    }
  }

  @Override
  public void onReloadPlugin(Plugin oldPlugin, Plugin newPlugin) {
    if (hasAvatarProvider(newPlugin)) {
      avatarSupport = true;
    } else {
      if (avatarSupport && hasAvatarProvider(oldPlugin)) {
        avatarSupport = false;
      }
    }
  }

  private static boolean hasAvatarProvider(Plugin plugin) {
    Injector src = plugin.getSysInjector();
    for (Map.Entry<Key<?>, Binding<?>> e : src.getBindings().entrySet()) {
      TypeLiteral<?> type = e.getKey().getTypeLiteral();
      if (type.getRawType() == AvatarProvider.class) {
        return true;
      }
    }
    return false;
  }

  public boolean getAvatarSupport() {
    return avatarSupport;
  }
}
