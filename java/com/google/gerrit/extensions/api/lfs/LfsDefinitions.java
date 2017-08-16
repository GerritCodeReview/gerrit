// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.extensions.api.lfs;

import com.google.common.base.Joiner;

public final class LfsDefinitions {
  public static final String CONTENTTYPE_VND_GIT_LFS_JSON =
      "application/vnd.git-lfs+json; charset=utf-8";

  public static final String LFS_OBJECTS_PATH = "objects/batch";
  public static final String LFS_LOCKS_PATH_REGEX = "locks(?:/(.*)(?:/unlock))?";
  public static final String LFS_VERIFICATION_PATH = "locks/verify";
  public static final String LFS_UNIFIED_PATHS_REGEX =
      Joiner.on('|').join(LFS_OBJECTS_PATH, LFS_LOCKS_PATH_REGEX, LFS_VERIFICATION_PATH);
  public static final String LFS_URL_WO_AUTH_REGEX_TEAMPLATE = "(?:/p/|/)(.+)(?:/info/lfs/)(?:%s)$";
  public static final String LFS_URL_WO_AUTH_REGEX =
      String.format(LFS_URL_WO_AUTH_REGEX_TEAMPLATE, LFS_UNIFIED_PATHS_REGEX);
  public static final String LFS_URL_REGEX_TEMPLATE = "^(?:/a)?" + LFS_URL_WO_AUTH_REGEX_TEAMPLATE;
  public static final String LFS_URL_REGEX =
      String.format(LFS_URL_REGEX_TEMPLATE, LFS_UNIFIED_PATHS_REGEX);

  private LfsDefinitions() {}
}
