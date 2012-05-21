// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.httpd.plugins;

import java.io.IOException;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

final class SmallResource extends Resource {
  private final byte[] data;
  private String contentType;
  private String characterEncoding;
  private long lastModified;

  SmallResource(byte[] data) {
    this.data = data;
  }

  SmallResource setLastModified(long when) {
    this.lastModified = when;
    return this;
  }

  SmallResource setContentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  SmallResource setCharacterEncoding(@Nullable String enc) {
    this.characterEncoding = enc;
    return this;
  }

  @Override
  int weigh() {
    return contentType.length() * 2 + data.length;
  }

  @Override
  void send(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    if (0 < lastModified) {
      res.setDateHeader("Last-Modified", lastModified);
    }
    res.setContentType(contentType);
    if (characterEncoding != null) {
     res.setCharacterEncoding(characterEncoding);
    }
    res.setContentLength(data.length);
    res.getOutputStream().write(data);
  }
}
