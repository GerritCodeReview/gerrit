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

package com.google.gerrit.server.securestore;

import com.google.common.base.Objects;
import com.google.gerrit.common.PluginData;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public class SecureStoreData extends PluginData {
  private final String className;
  private final String storeName;

  public SecureStoreData(String pluginName, String className, File jarFile,
      String storeName) {
    super(pluginName, "", jarFile);
    this.className = className;
    this.storeName = String.format("%s/%s", pluginName, storeName);
  }

  public String getStoreName() {
    return storeName;
  }

  public Class<? extends SecureStore> load() {
    return load(pluginFile);
  }

  @SuppressWarnings("unchecked")
  public Class<? extends SecureStore> load(File pluginFile) {
    try {
      URL[] pluginJarUrls = new URL[] {pluginFile.toURI().toURL()};
      ClassLoader currentCL = Thread.currentThread().getContextClassLoader();
      final URLClassLoader newClassLoader =
          new URLClassLoader(pluginJarUrls, currentCL);
      Thread.currentThread().setContextClassLoader(newClassLoader);
      return (Class<? extends SecureStore>) newClassLoader.loadClass(className);
    } catch (Exception e) {
      throw new SecureStoreException(String.format(
          "Cannot load secure store implementation for %s", storeName), e);
    }
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).add("storeName", storeName)
        .add("className", className).add("file", pluginFile).toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof SecureStoreData) {
      SecureStoreData o = (SecureStoreData) obj;
      return Objects.equal(storeName, o.storeName);
    }
    return equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(storeName);
  }
}
