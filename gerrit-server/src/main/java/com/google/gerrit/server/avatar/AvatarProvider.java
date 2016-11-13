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

package com.google.gerrit.server.avatar;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.IdentifiedUser;

/**
 * Provide avatar URLs for specified user.
 *
 * <p>Invoked by Gerrit when Avatar image requests are made.
 */
@ExtensionPoint
public interface AvatarProvider {
  /**
   * Get avatar URL.
   *
   * @param forUser The user for which to load an avatar image
   * @param imageSize A requested image size, in pixels. An imageSize of 0 indicates to use whatever
   *     default size the provider determines. AvatarProviders may ignore the requested image size.
   *     The web interface will resize any image to match imageSize, so ideally the provider should
   *     return an image sized correctly.
   * @return a URL of an avatar image for the specified user. A return value of {@code null} is
   *     acceptable, and results in the server responding with a 404. This will hide the avatar
   *     image in the web UI.
   */
  String getUrl(IdentifiedUser forUser, int imageSize);

  /**
   * Gets a URL for a user to modify their avatar image.
   *
   * @param forUser The user wishing to change their avatar image
   * @return a URL the user should visit to modify their avatar, or null if modification is not
   *     possible.
   */
  String getChangeAvatarUrl(IdentifiedUser forUser);
}
