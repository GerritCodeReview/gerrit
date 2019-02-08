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

package com.google.gerrit.common;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;
import com.google.gerrit.extensions.restapi.RawInput;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.servlet.http.HttpServletRequest;

@GwtIncompatible("Unemulated classes in java.io and javax.servlet")
public class RawInputUtil {
  public static RawInput create(String content) {
    return create(content.getBytes(UTF_8));
  }

  public static RawInput create(byte[] bytes, String contentType) {
    Preconditions.checkNotNull(bytes);
    Preconditions.checkArgument(bytes.length > 0);
    return new RawInput() {
      @Override
      public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(bytes);
      }

      @Override
      public String getContentType() {
        return contentType;
      }

      @Override
      public long getContentLength() {
        return bytes.length;
      }
    };
  }

  public static RawInput create(byte[] bytes) {
    return create(bytes, "application/octet-stream");
  }

  public static RawInput create(HttpServletRequest req) {
    return new RawInput() {
      @Override
      public String getContentType() {
        return req.getContentType();
      }

      @Override
      public long getContentLength() {
        return req.getContentLength();
      }

      @Override
      public InputStream getInputStream() throws IOException {
        return req.getInputStream();
      }
    };
  }
}
