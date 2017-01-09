// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.registration.ReloadableRegistrationHandle;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.PluginUser;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;

public abstract class Plugin {
  public enum ApiType {
    EXTENSION,
    PLUGIN,
    JS
  }

  /** Unique key that changes whenever a plugin reloads. */
  public static final class CacheKey {
    private final String name;

    CacheKey(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      int id = System.identityHashCode(this);
      return String.format("Plugin[%s@%x]", name, id);
    }
  }

  static ApiType getApiType(Manifest manifest) throws InvalidPluginException {
    Attributes main = manifest.getMainAttributes();
    String v = main.getValue("Gerrit-ApiType");
    if (Strings.isNullOrEmpty(v) || ApiType.EXTENSION.name().equalsIgnoreCase(v)) {
      return ApiType.EXTENSION;
    } else if (ApiType.PLUGIN.name().equalsIgnoreCase(v)) {
      return ApiType.PLUGIN;
    } else if (ApiType.JS.name().equalsIgnoreCase(v)) {
      return ApiType.JS;
    } else {
      throw new InvalidPluginException("Invalid Gerrit-ApiType: " + v);
    }
  }

  private final String name;
  private final Path srcFile;
  private final ApiType apiType;
  private final boolean disabled;
  private final CacheKey cacheKey;
  private final PluginUser pluginUser;
  private final FileSnapshot snapshot;
  private CleanupHandle cleanupHandle;

  protected LifecycleManager manager;

  private List<ReloadableRegistrationHandle<?>> reloadableHandles;

  public Plugin(
      String name, Path srcPath, PluginUser pluginUser, FileSnapshot snapshot, ApiType apiType) {
    this.name = name;
    this.srcFile = srcPath;
    this.apiType = apiType;
    this.snapshot = snapshot;
    this.pluginUser = pluginUser;
    this.cacheKey = new Plugin.CacheKey(name);
    this.disabled = srcPath != null && srcPath.getFileName().toString().endsWith(".disabled");
  }

  public CleanupHandle getCleanupHandle() {
    return cleanupHandle;
  }

  public void setCleanupHandle(CleanupHandle cleanupHandle) {
    this.cleanupHandle = cleanupHandle;
  }

  PluginUser getPluginUser() {
    return pluginUser;
  }

  public Path getSrcFile() {
    return srcFile;
  }

  public String getName() {
    return name;
  }

  @Nullable
  public abstract String getVersion();

  public ApiType getApiType() {
    return apiType;
  }

  public Plugin.CacheKey getCacheKey() {
    return cacheKey;
  }

  public boolean isDisabled() {
    return disabled;
  }

  protected abstract void start(PluginGuiceEnvironment env) throws Exception;

  protected abstract void stop(PluginGuiceEnvironment env);

  public abstract PluginContentScanner getContentScanner();

  public abstract Injector getSysInjector();

  @Nullable
  public abstract Injector getSshInjector();

  @Nullable
  public abstract Injector getHttpInjector();

  public void add(RegistrationHandle handle) {
    if (manager != null) {
      if (handle instanceof ReloadableRegistrationHandle) {
        if (reloadableHandles == null) {
          reloadableHandles = new ArrayList<>();
        }
        reloadableHandles.add((ReloadableRegistrationHandle<?>) handle);
      }
      manager.add(handle);
    }
  }

  List<ReloadableRegistrationHandle<?>> getReloadableHandles() {
    if (reloadableHandles != null) {
      return reloadableHandles;
    }
    return Collections.emptyList();
  }

  @Override
  public String toString() {
    return "Plugin [" + name + "]";
  }

  protected abstract boolean canReload();

  boolean isModified(Path jar) {
    return snapshot.isModified(jar.toFile());
  }
}
