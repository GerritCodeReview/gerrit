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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

@Singleton
public class RulesCache {

  private final Config config;
  private final SitePaths site;

  @Inject
  protected RulesCache( @GerritServerConfig Config config, SitePaths site) {
    this.config = config;
    this.site = site;
  }

  /** @return URLClassLoader with precompiled rules jar from rules.pl if it exists,
   *  null otherwise
   */
  public URLClassLoader getClassLoader(ObjectId rulesId) {
    //get rules.pl's sha1, if it exists
    String filePath = "";
    File cacheFolder = site.resolve(
        config.getString("cache", null, "directory"));
    if (cacheFolder != null) {
      filePath = cacheFolder.getPath();
    }
    //read jar from (site)/cache/rules
    //the included jar file should be in format:
    //rules-(rules.pl's sha1).jar
    File jarFile = null;
    if (rulesId != null) {
      jarFile = new File(filePath + "/rules/rules-" + rulesId.getName() + ".jar");
    }
    ClassLoader defaultLoader = getClass().getClassLoader();
    if(cacheFolder != null && jarFile != null && jarFile.exists()) {
      URL url;
      try {
        url = jarFile.toURI().toURL();
      } catch (MalformedURLException e) {
        return null;
      }
      URL[] urls = new URL[]{url};
      return new URLClassLoader(urls, defaultLoader);
    } else {
      return null;
    }
  }
}