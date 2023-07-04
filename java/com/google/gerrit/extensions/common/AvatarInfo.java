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

package com.google.gerrit.extensions.common;

import com.google.gerrit.proto.FieldDoc;
import java.util.Objects;

/**
 * Representation of an avatar in the REST API.
 *
 * <p>This class determines the JSON format of avatars in the REST API.
 *
 * <p>An avatar is the graphical representation of a user.
 */
public class AvatarInfo {
  /**
   * Size in pixels the UI prefers an avatar image to be.
   *
   * <p>The web UI prefers avatar images to be square, both the height and width of the image should
   * be this size. The height is the more important dimension to match than the width.
   */
  public static final int DEFAULT_SIZE = 32;

  /** The URL to the avatar image. */
  @FieldDoc(protoTag = 1)
  public String url;

  /** The height of the avatar image in pixels. */
  @FieldDoc(protoTag = 2)
  public Integer height;

  /** The width of the avatar image in pixels. */
  @FieldDoc(protoTag = 3)
  public Integer width;

  @Override
  public boolean equals(Object o) {
    if (o instanceof AvatarInfo) {
      AvatarInfo avatarInfo = (AvatarInfo) o;
      return Objects.equals(url, avatarInfo.url)
          && Objects.equals(height, avatarInfo.height)
          && Objects.equals(width, avatarInfo.width);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, height, width);
  }
}
