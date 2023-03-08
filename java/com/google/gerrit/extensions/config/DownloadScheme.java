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

package com.google.gerrit.extensions.config;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

@ExtensionPoint
public abstract class DownloadScheme {
  /**
   * Returns the URL of this download scheme.
   *
   * @param project the name of the project for which the URL should be returned
   * @return URL of the download scheme
   */
  public abstract String getUrl(String project);

  /** Returns whether this scheme requires authentication */
  public abstract boolean isAuthRequired();

  /** Returns whether this scheme supports authentication */
  public abstract boolean isAuthSupported();

  /** Returns whether the download scheme is enabled */
  public abstract boolean isEnabled();

  /** Returns whether the download scheme is hidden in the UI */
  public abstract boolean isHidden();
}
