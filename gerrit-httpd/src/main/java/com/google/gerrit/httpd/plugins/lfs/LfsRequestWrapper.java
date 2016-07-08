// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.httpd.plugins.lfs;

import com.google.common.io.ByteStreams;
import com.google.gerrit.extensions.restapi.NotImplementedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class LfsRequestWrapper extends HttpServletRequestWrapper {
  private final byte[] body;

  public LfsRequestWrapper(HttpServletRequest request) throws IOException {
    super(request);
    InputStream is = super.getInputStream();
    body = ByteStreams.toByteArray(is);
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    final ByteArrayInputStream buffer = new ByteArrayInputStream(body);

    return new ServletInputStream() {
      @Override
      public int read() throws IOException {
        return buffer.read();
      }
      @Override
      public boolean isFinished() {
        return buffer.available() == 0;
      }
      @Override
      public boolean isReady() {
        return true;
      }
      @Override
      public void setReadListener(ReadListener arg0) {
        throw new NotImplementedException();
      }
    };
  }
}