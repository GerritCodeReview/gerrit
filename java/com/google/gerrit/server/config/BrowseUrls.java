// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.config;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import java.util.Optional;

/**
 * Formats URLs to different parts of the Gerrit API and UI.
 *
 * <p>By default, these gerrit URLs are formed by adding suffixes to the web URL. The interface
 * centralizes these conventions, and also allows introducing different, custom URL schemes.
 *
 * <p>Unfortunately, Gerrit operates in modes for which there is no canonical URL. This can be in
 * standalone utilities that have no HTTP server (eg. index upgrade commands), in servers that run
 * SSH only, or in a HTTP/SSH server that is accessed over SSH without canonical web URL configured.
 */
public interface BrowseUrls {

  /**
   * The canonical base URL where this Gerrit installation can be reached.
   *
   * <p>For the default implementations below to work, it must ends in "/".
   */
  Optional<String> webUrl();

  /** Returns the URL for viewing a change. */
  default Optional<String> changeViewUrl(@Nullable Project.NameKey project, Change.Id id) {
    Optional<String> url = webUrl();
    if (!url.isPresent()) {
      return Optional.empty();
    }

    // In the PolyGerrit URL (contrary to REST URLs) there is no need to URL-escape strings, since
    // the /+/ separator unambiguously defines how to parse the path.
    return Optional.of(url + "c/" + (project != null ? project.get() + "/+/" : "") + id.get());
  }
}
