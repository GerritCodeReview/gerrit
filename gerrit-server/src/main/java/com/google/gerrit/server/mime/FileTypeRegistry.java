// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.mime;

import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil2;

public interface FileTypeRegistry {
  /**
   * Get the most specific MIME type available for a file.
   *
   * @param path name of the file. The base name (component after the last '/') may be used to help
   *     determine the MIME type, such as by examining the extension (portion after the last '.' if
   *     present).
   * @param content the complete file content. If non-null the content may be used to guess the MIME
   *     type by examining the beginning for common file headers.
   * @return the MIME type for this content. If the MIME type is not recognized or cannot be
   *     determined, {@link MimeUtil2#UNKNOWN_MIME_TYPE} which is an alias for {@code
   *     application/octet-stream}.
   */
  MimeType getMimeType(final String path, final byte[] content);

  /**
   * Is this content type safe to transmit to a browser directly?
   *
   * @param type the MIME type of the file content.
   * @return true if the Gerrit administrator wants to permit this content to be served as-is; false
   *     if the administrator does not trust this content type and wants it to be protected
   *     (typically by wrapping the data in a ZIP archive).
   */
  boolean isSafeInline(final MimeType type);
}
