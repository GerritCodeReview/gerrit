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

/** BrowseUrls defines all URLs we provide to users as messages. */
public interface BrowseUrls {
  String webUrl();

  default String reviewUrl(
      Project.NameKey project, Change.Id id, @Nullable String file, @Nullable Integer lineNumber) {
    // TODO(hanwen): something with URL escaping for the project string?

    // PolyGerrit puts a c/ before the change ID, but this also works with a redirect.
    return webUrl()
        + "c/"
        + (project != null ? project.get() + "/+/" : "")
        + id.get()
        + (file == null ? "" : "/" + file + (lineNumber == null ? "" : "#" + lineNumber));
  }

  default String settingsUrl(String section) {
    String u = webUrl() + "/settings/";
    if (!Strings.isNullOrEmpty(section)) {
      u += "#" + section;
    }

    return u;
  }
}
