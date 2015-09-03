// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages Git repositories stored on the local filesystem. Allows fuzzy lookup of repo names */
@Singleton
public class MappingLocalDiskRepositoryManager extends LocalDiskRepositoryManager {
  private static final Logger log =
      LoggerFactory.getLogger(MappingLocalDiskRepositoryManager.class);

  private ProjectNameMappingCache projectNameMappingCache;

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      bind(GitRepositoryManager.class).to(MappingLocalDiskRepositoryManager.class);
      listener().to(MappingLocalDiskRepositoryManager.Lifecycle.class);
    }
  }

  @Inject
  MappingLocalDiskRepositoryManager(SitePaths site,
      @GerritServerConfig Config cfg) {
    super(site, cfg);
    projectNameMappingCache = new ProjectNameMappingCache(this);
  }

  public Project.NameKey getRealName(Project.NameKey name)
      throws RepositoryNotFoundException {
    if (name.toString().matches("^[a-z0-9-]+/mainline$")) {
      name = new Project.NameKey(name.toString().replaceFirst("/mainline$", ""));
    }
    if (!name.equals(getAlternativeProjectName(name))) {
      return name;
    }

    for (final Project.NameKey destName : projectNameMappingCache.get()) {
      Project.NameKey altDestName = getAlternativeProjectName(destName);
      if (altDestName.equals(name)) {
        log.debug(String.format("Alternative candidate for [%s] found: [%s]", name, destName));
        return destName;
      }
    }
    // No project found, throw exception for original request.
    throw new RepositoryNotFoundException("Invalid name: " + name);
  }

  @Override
  public Repository openRepository(Project.NameKey name)
      throws RepositoryNotFoundException {
    try {
      return super.openRepository(name);
    } catch (RepositoryNotFoundException e) {
      Project.NameKey altName = getAlternativeProjectName(name);
      if (!name.equals(altName)) {
        // If the original request is affected by the slug transformation,
        // it is not part of the old naming scheme.
        throw e;
      }
      Project.NameKey realName = getRealName(name);
      return super.openRepository(realName);
    }
  }

  private Project.NameKey getAlternativeProjectName(Project.NameKey name) {
    String altName = name.toString();
    // From Git/Axis.pm:
    //
    // $slug = lc($slug || '');
    altName = altName.toLowerCase();
    // $slug =~ s/[^a-z0-9_]+$//;
    altName = altName.replaceFirst("[^a-z0-9_]+$", "");
    // $slug =~ s/[^a-z0-9_]+/-/g;
    altName = altName.replaceAll("[^a-z0-9_]+", "-");
    // return $slug;
    return new Project.NameKey(altName);
  }
}
