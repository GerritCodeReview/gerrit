// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.extensions.restapi;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Implementation of {@link RawInput} wrapping a UTF-8 string. */
public class RawStringInput implements RawInput {
  private final byte[] bytes;
  private final String contentType;

  public RawStringInput(String in, String contentType) {
    this.bytes = in.getBytes(UTF_8);
    this.contentType = contentType;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public long getContentLength() {
    return bytes.length;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(bytes);
  }
}
