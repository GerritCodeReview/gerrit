// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

/**
 * Defines an (optional) mapping between internal host names and external one.
 *
 * <p>Gerrit can be available from a different host inside the corporate (internal) network (due to
 * some internal policies). As a result all users within internal network see different ULRs in
 * browser which makes them harder to share.
 *
 * <p>The extension point allows make UI more user-friendly.
 */
@ExtensionPoint
public interface PrivateToPublicHostMapProvider {
  /**
   * Returns a mapping from a private host names to a public host names.
   *
   * <p>The returned mapping is passed to the gerrit FE and is used for exposing public url in the
   * UI (for example, by showing a button for copying public url).
   *
   * <p>This method is called when Gerrit is rendering index page and map can be customized
   * per-request (for example, map can be returned only if request is coming from the internal
   * host).
   *
   * <p>Example of map: {"gerrit-review.internal": "gerrit-review.googlesource.com"}
   */
  Optional<Map<String, String>> getMapForRequest(HttpServletRequest request);
}
