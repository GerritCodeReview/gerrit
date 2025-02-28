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

package com.google.gerrit.server.mail.send;

import static java.util.Objects.requireNonNull;

import com.google.protobuf.ByteString;

/**
 * Email resource that can be attached to an email.
 *
 * <p>Can be used for images included in html body of the email.
 *
 * @param contentId Value of Content-ID header used for referring to the resource from html body of
 *     the email.
 * @param contentType MIME type of the resource.
 * @param content Unencoded data that should be added to the email
 */
public record EmailResource(String contentId, String contentType, ByteString content) {
  public EmailResource {
    requireNonNull(contentId, "contentId");
    requireNonNull(contentType, "contentType");
    requireNonNull(content, "content");
  }

  public static EmailResource create(String contentId, String contentType, ByteString content) {
    return new EmailResource(contentId, contentType, content);
  }
}
