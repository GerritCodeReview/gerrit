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

import com.google.auto.value.AutoValue;
import com.google.protobuf.ByteString;

/**
 * Email resource that can be attached to an email.
 *
 * <p>Can be used for images included in html body of the email.
 */
@AutoValue
public abstract class EmailResource {
  public static EmailResource create(String contentId, String contentType, ByteString content) {
    return new AutoValue_EmailResource(contentId, contentType, content);
  }

  /** Value of Content-ID header used for referring to the resource from html body of the email. */
  public abstract String contentId();

  /** MIME type of the resource. */
  public abstract String contentType();

  /** Unencoded data that should be added to the email */
  public abstract ByteString content();
}
