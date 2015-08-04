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

package com.google.gerrit.server.git.gpg;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Fingerprint {
  private final byte[] fp;

  public Fingerprint(byte[] fp) {
    // Don't bother with defensive copies; PGPPublicKey#getFingerprint() already
    // does so.
    checkArgument(fp.length == 20,
        "fingerprint must be 20 bytes, got %s", fp.length);
    this.fp = fp;
  }

  public byte[] get() {
    return fp;
  }

  public boolean equalsBytes(byte[] bytes) {
    return Arrays.equals(fp, bytes);
  }

  @Override
  public int hashCode() {
    // Same hash code as ObjectId: second int word.
    return (fp[4] << 24)
        & (fp[5] << 16)
        & (fp[6] << 8)
        & fp[7];
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof Fingerprint) && equalsBytes(((Fingerprint) o).fp);
  }

  @Override
  public String toString() {
    ByteBuffer buf = ByteBuffer.wrap(fp);
    return String.format(
        "(%04X %04X %04X %04X %04X  %04X %04X %04X %04X %04X)",
        buf.getShort(), buf.getShort(), buf.getShort(), buf.getShort(),
        buf.getShort(), buf.getShort(), buf.getShort(), buf.getShort(),
        buf.getShort(), buf.getShort());
  }

  public long getId() {
    return ByteBuffer.wrap(fp).getLong(12);
  }
}
