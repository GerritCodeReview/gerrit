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

package com.google.gerrit.acceptance;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import org.apache.http.Header;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

public class HttpResponse {

  protected org.apache.http.HttpResponse response;
  protected Reader reader;

  HttpResponse(org.apache.http.HttpResponse response) {
    this.response = response;
  }

  public Reader getReader() throws IllegalStateException, IOException {
    if (reader == null && response.getEntity() != null) {
      reader = new InputStreamReader(response.getEntity().getContent());
    }
    return reader;
  }

  public void consume() throws IllegalStateException, IOException {
    Reader reader = getReader();
    if (reader != null) {
      while (reader.read() != -1) {}
    }
  }

  public int getStatusCode() {
    return response.getStatusLine().getStatusCode();
  }

  public String getContentType() {
    return getHeader("X-FYI-Content-Type");
  }

  public String getHeader(String name) {
    Header hdr = response.getFirstHeader(name);
    return hdr != null ? hdr.getValue() : null;
  }

  public boolean hasContent() {
    Preconditions.checkNotNull(response, "Response is not initialized.");
    return response.getEntity() != null;
  }

  public String getEntityContent() throws IOException {
    Preconditions.checkNotNull(response, "Response is not initialized.");
    Preconditions.checkNotNull(response.getEntity(), "Response.Entity is not initialized.");
    ByteBuffer buf = IO.readWholeStream(response.getEntity().getContent(), 1024);
    return RawParseUtils.decode(buf.array(), buf.arrayOffset(), buf.limit()).trim();
  }
}
