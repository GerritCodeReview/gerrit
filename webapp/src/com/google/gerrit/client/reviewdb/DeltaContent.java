// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;

/** Compressed file content referenced by a {@link Patch}. */
public final class DeltaContent {
  public static enum Type {
    PATCH('p'),

    CONTENT('c');

    private final char code;

    private Type(final char c) {
      code = c;
    }

    public char getCode() {
      return code;
    }

    public static Type forCode(final char c) {
      for (final Type s : Type.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }
  }

  public static class Key implements
      com.google.gwtorm.client.Key<com.google.gwtorm.client.Key<?>> {
    /** See {@link DeltaContent.Type}. */
    @Column
    protected char type;

    @Column(length = 40)
    protected String hash;

    protected Key() {
    }

    public Key(final DeltaContent.Type type, final String hash) {
      this.type = type.getCode();
      this.hash = hash;
    }

    public com.google.gwtorm.client.Key<?> getParentKey() {
      return null;
    }

    @Override
    public int hashCode() {
      return hash.hashCode() + type;
    }

    @Override
    public boolean equals(final Object o) {
      return o instanceof Key && ((Key) o).type == type
          && ((Key) o).hash.equals(hash);
    }
  }

  @Column(name = "content")
  protected DeltaContent.Key key;

  /**
   * Depth (how many times you can recurse until {@link #base} is null).
   * <p>
   * This property determines how {@link #data} is treated.
   */
  @Column
  protected short chainDepth;

  @Column(notNull = false)
  protected DeltaContent.Key base;

  /**
   * Raw compressed binary data; {@link java.util.zip.DeflaterOutputStream}
   * <p>
   * If {@link #chainDepth} = 0 this field has the full content of the file.
   * <p>
   * If {@link #chainDepth} &lt; 0 this field has a unified format patch which
   * must be applied to the full content of {@link #base} in order to get the
   * full content of this object.
   */
  @Column
  protected byte[] data;

  protected DeltaContent() {
  }

  public DeltaContent(final DeltaContent.Key newKey, final short newDepth,
      final DeltaContent.Key newBase, final byte[] newData) {
    key = newKey;
    chainDepth = newDepth;
    base = newBase;
    data = newData;
  }

  public DeltaContent.Key getKey() {
    return key;
  }

  public Type getType() {
    return Type.forCode(key.type);
  }

  public DeltaContent.Key getBase() {
    return base;
  }

  public short getChainDepth() {
    return chainDepth;
  }

  /** Get the data array <b>(note this is not a copy!)</b> */
  public byte[] getData() {
    return data;
  }
}
