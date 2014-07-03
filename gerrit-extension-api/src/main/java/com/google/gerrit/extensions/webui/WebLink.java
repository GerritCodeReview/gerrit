// Copyright (C) 2014 The Android Open Source Project
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
package com.google.gerrit.extensions.webui;

public interface WebLink {

  /**
   * The link-name displayed in UI.
   *
   * @return name of link or title of the link if image URL is available.
   */
  String getLinkName();

  /**
   * URL of image to be displayed
   *
   * @return URL to image for link or null for using a text-only link.
   * Recommended image size is 16x16.
   */
  String getImageUrl();
}
