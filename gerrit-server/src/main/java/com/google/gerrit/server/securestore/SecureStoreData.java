package com.google.gerrit.server.securestore;

import com.google.common.base.Objects;
import com.google.gerrit.common.PluginData;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public class SecureStoreData extends PluginData {
  private final String className;
  private final String storeName;

  public SecureStoreData(String pluginName, String className, File jarFile, String storeName) {
    super(storeName, "", jarFile);
    this.className = className;
    this.storeName = storeName;
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
  public int hashCode() {
    return storeName.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof SecureStoreData && storeName.hashCode() == obj.hashCode();
  }
}