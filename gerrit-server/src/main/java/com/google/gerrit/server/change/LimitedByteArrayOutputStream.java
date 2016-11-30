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

public class LimitedByteArrayOutputStream extends ByteArrayOutputStream {

  private final int maxSize;


  public LimitedByteArrayOutputStream(int max, int initial) {
    super(initial);
    maxSize = max;
  }

  @Override
  public synchronized void write(int b) {
    if (size() + 1 > maxSize) {
      throw new IndexOutOfBoundsException();
    }

    super.write(b);
  }

  @Override
  public synchronized void write(byte[] b, int off, int len) {
    if (size() + len > maxSize) {
      throw new IndexOutOfBoundsException();
    }
    super.write(b, off, len);
  }
}
