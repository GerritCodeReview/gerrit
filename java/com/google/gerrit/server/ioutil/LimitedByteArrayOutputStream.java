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

package com.google.gerrit.server.ioutil;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** A stream that throws an exception if it consumes data beyond a configured byte count. */
public class LimitedByteArrayOutputStream extends OutputStream {

  private final int maxSize;
  private final ByteArrayOutputStream buffer;

  /**
   * Constructs a LimitedByteArrayOutputStream, which stores output in memory up to a certain
   * specified size. When the output exceeds the specified size a LimitExceededException is thrown.
   *
   * @param max the maximum size in bytes which may be stored.
   * @param initial the initial size. It must be smaller than the max size.
   */
  public LimitedByteArrayOutputStream(int max, int initial) {
    checkArgument(initial <= max);
    maxSize = max;
    buffer = new ByteArrayOutputStream(initial);
  }

  private void checkOversize(int additionalSize) throws IOException {
    if (buffer.size() + additionalSize > maxSize) {
      throw new LimitExceededException();
    }
  }

  @Override
  public void write(int b) throws IOException {
    checkOversize(1);
    buffer.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    checkOversize(len);
    buffer.write(b, off, len);
  }

  /** @return a newly allocated byte array with contents of the buffer. */
  public byte[] toByteArray() {
    return buffer.toByteArray();
  }

  public static class LimitExceededException extends IOException {
    private static final long serialVersionUID = 1L;
  }
}
