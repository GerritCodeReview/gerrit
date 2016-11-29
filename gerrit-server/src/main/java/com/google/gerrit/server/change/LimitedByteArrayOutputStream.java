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

package com.google.gerrit.server.change;

import java.io.ByteArrayOutputStream;

class LimitedByteArrayOutputStream extends ByteArrayOutputStream {

  private final int maxSize;
  static final String OVERSIZE_ERROR =
      "Output size exceeds maximum of LimitedByteArrayOutputStream";

  /**
   * Constructs a LimitedByteArrayOutputStream, which stores output
   * in memory up to a certain specified size. When the output exceeds
   * the specified size a RuntimeException is thrown.
   * As the super class is ByteArrayOutputStream, which only throws run
   * time exceptions (an IndexOutOfBoundsException) in case of a problem,
   * overriding the write methods doesn't allow throwing checked exceptions.
   *
   * @param max the maximum size in bytes which may be stored.
   * @param initial the initial size. It must be smaller than the max size
   */
  public LimitedByteArrayOutputStream(int max, int initial) {
    super(initial);
    maxSize = max;
    if (initial > max) {
      throw new RuntimeException();
    }
  }

  private void checkOversize(int additionalSize) {
    if (size() + additionalSize > maxSize) {
      throw new RuntimeException(OVERSIZE_ERROR);
    }
  }

  @Override
  public synchronized void write(int b) {
    checkOversize(1);
    super.write(b);
  }

  @Override
  public synchronized void write(byte[] b, int off, int len) {
    checkOversize(len);
    super.write(b, off, len);
  }
}
