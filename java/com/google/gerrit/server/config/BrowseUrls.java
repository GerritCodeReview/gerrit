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
import com.google.gwt.thirdparty.guava.common.base.Strings;

/**
 * Formats URLs to different parts of the Gerrit API and UI.
 *
 * <p>By default, these gerrit URLs are formed by adding suffixes to the web URL. The interface
 * centralizes these conventions, and also allows introducing different, custom URL schemes.
 */
public interface BrowseUrls {

  /**
   * Returns true if this instance has a URL defined.
   *
   * <p>It is possible for Gerrit instances to not have URLs in the following circumstances, for
   * example if the code is run in a context (eg. index upgrade) that doesn't have HTTP enabled, or
   * as a SSH only server. Arguably, in both of these cases, it would be possible for Gerrit to
   * return either a placeholder, based on hostname or IP-address. Gerrit does not do that
   * currently, and some code changes behavior if the URL is not available.
   */
  boolean hasUrl();

  /** The canonical base URL where this Gerrit installation can be reached. It should end in "/". */
  String webUrl();

  default String changeViewUrl(@Nullable Project.NameKey project, Change.Id id) {
    // In the PolyGerrit URL (contrary to REST URLs) there is no need to URL-escape strings, since
    // the
    // /+/ separator unambiguously defines how to parse the path.
    return webUrl() + "c/" + (project != null ? project.get() + "/+/" : "") + id.get();
  }

  default String settingsUrl(String section) {
    String u = webUrl() + "/settings/";
    if (!Strings.isNullOrEmpty(section)) {
      u += "#" + section;
    }

    return u;
  }

  default String docUrl(String page, String anchor) {
    return webUrl() + "Documentation/" + page + "#" + anchor;
  }
}
