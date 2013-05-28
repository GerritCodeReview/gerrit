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

package com.google.gerrit.server.admin;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;

import java.io.File;
import java.io.IOException;

public class ConfigFilesCollection implements
    RestCollection<TopLevelResource, ConfigFileResource> {

  private final File site_path;
  private final File etc_dir;
  private final File static_dir;
  private final DynamicMap<RestView<ConfigFileResource>> views;

  @Inject
  public ConfigFilesCollection(
      SitePaths sitePaths,
      DynamicMap<RestView<ConfigFileResource>> views) {
    this.site_path = sitePaths.site_path;
    this.etc_dir = sitePaths.etc_dir;
    this.static_dir = sitePaths.static_dir;
    this.views = views;
  }

  @Override
  public RestView<TopLevelResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public ConfigFileResource parse(TopLevelResource root, IdString id)
      throws ResourceNotFoundException, IOException {
    File f = new File(site_path, id.get());
    if (!isAllowed(f)) {
      throw new ResourceNotFoundException(id);
    }
    return new ConfigFileResource(f);
  }

  @Override
  public DynamicMap<RestView<ConfigFileResource>> views() {
    return views;
  }

  private boolean isAllowed(File path) throws IOException {
    if (!path.isFile()) {
      return false;
    }
    return isParent(etc_dir, path) || isParent(static_dir, path);
  }

  private boolean isParent(File parent, File child) throws IOException {
    File p = parent.getCanonicalFile();
    File c = child.getCanonicalFile();
    for (;;) {
      c = c.getParentFile();
      if (c == null) {
        return false;
      }
      if (c.equals(p)) {
        return true;
      }
    }
  }
}
