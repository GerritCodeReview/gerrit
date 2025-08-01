// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.util;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Config;

@Singleton
public class MarkdownImagesUtil {
  public static final String BASE64_IMAGE_REGEX =
      "![^\\]]*\\]\\(data:image/(?:bmp|gif|x-icon|jpeg|jpg|png|tiff|webp);base64,[^)]*\\)";

  // This regex is adapted from the frontend's IMAGE_MIME_PATTERN.
  // It matches base64-encoded images in Markdown format: ![alt-text](data:image/...).
  private static final Pattern BASE64_IMAGE_PATTERN = Pattern.compile(BASE64_IMAGE_REGEX);

  private static final String IMAGE_PLACEHOLDER = "![base64-image]";

  private final boolean allowMarkdownBase64ImagesInComments;

  @Inject
  MarkdownImagesUtil(@GerritServerConfig Config serverConfig) {
    this.allowMarkdownBase64ImagesInComments =
        serverConfig.getBoolean("change", "allowMarkdownBase64ImagesInComments", false);
  }

  public String stripImages(String comment) {
    if (!allowMarkdownBase64ImagesInComments || comment == null) {
      return comment;
    }
    // Replace the image and its surrounding whitespace with a single space.
    String combinedText = BASE64_IMAGE_PATTERN.matcher(comment).replaceAll(" ");
    return combinedText;
  }

  public String replaceImagesWithPlaceholder(String comment) {
    if (!allowMarkdownBase64ImagesInComments || comment == null) {
      return comment;
    }
    return BASE64_IMAGE_PATTERN.matcher(comment).replaceAll(IMAGE_PLACEHOLDER);
  }
}
