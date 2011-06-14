// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class RulesCache {
  private static final Logger log = LoggerFactory.getLogger(RulesCache.class);

  private final Map<ObjectId, LoaderRef> classLoaderCache =
    new HashMap<ObjectId, LoaderRef>();

  private final ReferenceQueue<ClassLoader> DEAD = new ReferenceQueue<ClassLoader>();

  private final File cacheDir;
  private final File rulesDir;

  private final class LoaderRef extends WeakReference<ClassLoader> {
    final ObjectId key;

    LoaderRef(ObjectId key, ClassLoader loader) {
      super(loader, DEAD);
      this.key = key;
    }
  }

  @Inject
  protected RulesCache (@GerritServerConfig Config config, SitePaths site) {
    cacheDir = site.resolve(
        config.getString("cache", null, "directory"));
    if (cacheDir != null) {
      rulesDir = new File(cacheDir, "rules");
    } else {
      rulesDir = null;
    }
  }

  /** @return URLClassLoader with precompiled rules jar from rules.pl if it exists,
   *  null otherwise
   */
  public synchronized ClassLoader getClassLoader(ObjectId rulesId) {
    if (rulesId == null || rulesDir == null) {
      return null;
    }

    Reference<? extends ClassLoader> ref = classLoaderCache.get(rulesId);
    if (ref != null) {
      ClassLoader cl = ref.get();
      if (cl != null) {
        return cl;
      }
      classLoaderCache.remove(rulesId);
      ref.enqueue();
    }

    cleanCache();

    //read jar from (site)/cache/rules
    //the included jar file should be in format:
    //rules-(rules.pl's sha1).jar
    File jarFile = new File(rulesDir, "rules-" + rulesId.getName() + ".jar");
    if (!jarFile.isFile()) {
      return null;
    }

    ClassLoader defaultLoader = getClass().getClassLoader();
    URL url;
    try {
      url = jarFile.toURI().toURL();
    } catch (MalformedURLException e) {
      log.error("Path to rules jar is broken", e);
      return null;
    }
    URL[] urls = new URL[]{url};

    ClassLoader urlLoader = new URLClassLoader(urls, defaultLoader);

    LoaderRef lRef = new LoaderRef(rulesId, urlLoader);
    classLoaderCache.put(rulesId, lRef);
    return urlLoader;
  }

  private void cleanCache() {
    Reference<? extends ClassLoader> ref;
    while ((ref = DEAD.poll()) != null) {
      classLoaderCache.remove(((LoaderRef) ref).key);
    }
  }
}