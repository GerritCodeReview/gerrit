// Copyright (C) 2023 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;

/**
 * Various constants related to {@link StaticModule}
 *
 * <p>Methods of the {@link StaticModule} are not used internally in google, so moving public
 * constants into the {@link StaticModuleConstants} allows to exclude {@link StaticModule} from the
 * google-hosted gerrit hosts.
 */
public final class StaticModuleConstants {
  public static final String CACHE = "static_content";

  /**
   * Paths at which we should serve the main PolyGerrit application {@code index.html}.
   *
   * <p>Supports {@code "/*"} as a trailing wildcard.
   */
  public static final ImmutableList<String> POLYGERRIT_INDEX_PATHS =
      ImmutableList.of(
          "/",
          "/c/*",
          "/id/*",
          "/p/*",
          "/q/*",
          "/x/*",
          "/admin/*",
          "/dashboard/*",
          "/profile/*",
          "/groups/self",
          "/settings/*",
          "/Documentation/q/*");

  private StaticModuleConstants() {};
}
