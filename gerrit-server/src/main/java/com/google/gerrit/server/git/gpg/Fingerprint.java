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

import org.eclipse.jgit.util.NB;

import java.util.Arrays;

public class Fingerprint {
  private final byte[] fp;

  public static String toString(byte[] fp) {
    checkLength(fp);
    return String.format(
        "%04X %04X %04X %04X %04X  %04X %04X %04X %04X %04X",
        NB.decodeUInt16(fp, 0), NB.decodeUInt16(fp, 2), NB.decodeUInt16(fp, 4),
        NB.decodeUInt16(fp, 6), NB.decodeUInt16(fp, 8), NB.decodeUInt16(fp, 10),
        NB.decodeUInt16(fp, 12), NB.decodeUInt16(fp, 14),
        NB.decodeUInt16(fp, 16), NB.decodeUInt16(fp, 18));
  }

  private static byte[] checkLength(byte[] fp) {
    checkArgument(fp.length == 20,
        "fingerprint must be 20 bytes, got %s", fp.length);
    return fp;
  }

  /**
   * Wrap a fingerprint byte array.
   * <p>
   * The newly created Fingerprint object takes ownership of the byte array,
   * which must not be subsequently modified. (Most callers, such as hex
   * decoders and {@code
   * org.bouncycastle.openpgp.PGPPublicKey#getFingerprint()}, already produce
   * fresh byte arrays.)
   *
   * @param fp 20-byte fingerprint byte array to wrap.
   */
  public Fingerprint(byte[] fp) {
    this.fp = checkLength(fp);
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
    return NB.decodeInt32(fp, 4);
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof Fingerprint) && equalsBytes(((Fingerprint) o).fp);
  }

  @Override
  public String toString() {
    return toString(fp);
  }

  public long getId() {
    return NB.decodeInt64(fp, 12);
  }
}
