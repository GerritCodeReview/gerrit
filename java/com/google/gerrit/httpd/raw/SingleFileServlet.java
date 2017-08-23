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

package com.google.gerrit.httpd.raw;

import com.google.common.cache.Cache;
import java.nio.file.Path;

/** Serve a single static file, regardless of path. */
class SingleFileServlet extends ResourceServlet {
  private static final long serialVersionUID = 1L;

  private final Path path;

  SingleFileServlet(Cache<Path, Resource> cache, Path path, boolean refresh) {
    super(cache, refresh);
    this.path = path;
  }

  SingleFileServlet(
      Cache<Path, Resource> cache, Path path, boolean refresh, boolean cacheOnClient) {
    super(cache, refresh, cacheOnClient);
    this.path = path;
  }

  @Override
  protected Path getResourcePath(String pathInfo) {
    return path;
  }
}
