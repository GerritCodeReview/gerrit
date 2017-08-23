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

package com.google.gerrit.httpd.resources;

import com.google.common.net.HttpHeaders;
import com.google.gerrit.common.Nullable;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class SmallResource extends Resource {
  private static final long serialVersionUID = 1L;
  private final byte[] data;
  private String contentType;
  private String characterEncoding;
  private long lastModified;

  public SmallResource(byte[] data) {
    this.data = data;
  }

  public SmallResource setLastModified(long when) {
    this.lastModified = when;
    return this;
  }

  public SmallResource setContentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  public SmallResource setCharacterEncoding(@Nullable String enc) {
    this.characterEncoding = enc;
    return this;
  }

  @Override
  public int weigh() {
    return contentType.length() * 2 + data.length;
  }

  @Override
  public void send(HttpServletRequest req, HttpServletResponse res) throws IOException {
    if (0 < lastModified) {
      long ifModifiedSince = req.getDateHeader(HttpHeaders.IF_MODIFIED_SINCE);
      if (ifModifiedSince > 0 && ifModifiedSince == lastModified) {
        res.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        return;
      }
      res.setDateHeader("Last-Modified", lastModified);
    }
    res.setContentType(contentType);
    if (characterEncoding != null) {
      res.setCharacterEncoding(characterEncoding);
    }
    res.setContentLength(data.length);
    res.getOutputStream().write(data);
  }

  @Override
  public boolean isUnchanged(long lastModified) {
    return this.lastModified == lastModified;
  }
}
