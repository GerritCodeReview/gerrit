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

  public static class Target{
    public final static String BLANK = "_blank";
    public final static String SELF = "_self";
    public final static String PARENT = "_parent";
    public final static String TOP = "_top";
  }
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

  /**
   * Target window in which the link should be opened (e.g. "_blank", "_self".).
   *
   * @return link target, if null the link is opened in the current window
   */
  String getTarget();
}
