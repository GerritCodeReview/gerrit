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

package com.google.gerrit.server.extensions.webui;

import com.google.common.hash.Hasher;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.webui.UiAction;

/** Extension of {@link UiAction} contributing to an ETag. */
public interface HasETag<R extends RestResource> extends UiAction<R> {
  /**
   * Contribute to the current ETag computation.
   * <p>
   * This method only needs to add unique data that is action specific.
   * Resource information such as the change number or revision name are
   * already accounted for through the resource URL.
   * <p>
   * The caller as already included the providing plugin and the
   * action's exported name before calling this helper method.
   *
   * @param h hasher to add action specific state into.
   * @param rsrc the current resource.
   */
  void buildETag(Hasher h, R rsrc);
}
