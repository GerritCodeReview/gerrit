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

package com.google.gwtorm.client;

/** Common utility functions for {@link Key} implementors. */
public class KeyUtil {
  private static Encoder ENCODER_IMPL = new StandardKeyEncoder();

  /**
   * Set the encoder implementation to a valid implementation.
   *
   * <p>Server-side code needs to set the encoder to a {@link
   * com.google.gwtorm.server.StandardKeyEncoder} instance prior to invoking any methods in this
   * class. Typically this is done by the {@link com.google.gwtorm.server.SchemaFactory}
   * implementation's static initializer.
   */
  public static void setEncoderImpl(final Encoder e) {
    ENCODER_IMPL = e;
  }

  /**
   * Determine if two keys are equal, supporting null references.
   *
   * @param <T> type of the key entity.
   * @param a first key to test; may be null.
   * @param b second key to test; may be null.
   * @return true if both <code>a</code> and <code>b</code> are null, or if both are not-null and
   *     <code>a.equals(b)</code> is true. Otherwise false.
   */
  public static <T extends Key<?>> boolean eq(final T a, final T b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return a.equals(b);
  }

  /**
   * Encode a string to be safe for use within a URL like string.
   *
   * <p>The returned encoded string has URL component characters escaped with hex escapes (e.g. ' '
   * is '+' and '%' is '%25'). The special character '/' is left literal. The comma character (',')
   * is always encoded, permitting multiple encoded string values to be joined together safely.
   *
   * @param e the string to encode, must not be null.
   * @return the encoded string.
   */
  public static String encode(final String e) {
    return ENCODER_IMPL.encode(e);
  }

  /**
   * Decode a string previously encoded by {@link #encode(String)}.
   *
   * @param e the string to decode, must not be null.
   * @return the decoded string.
   */
  public static String decode(final String e) {
    return ENCODER_IMPL.decode(e);
  }

  /**
   * Split a string along the last comma and parse into the parent.
   *
   * @param parent parent key; <code>parent.fromString(in[0..comma])</code>.
   * @param in the input string.
   * @return text (if any) after the last comma in the input.
   */
  public static String parseFromString(final Key<?> parent, final String in) {
    final int comma = in.lastIndexOf(',');
    if (comma < 0 && parent == null) {
      return decode(in);
    }
    if (comma < 0 && parent != null) {
      throw new IllegalArgumentException("Not enough components: " + in);
    }
    assert (parent != null);
    parent.fromString(in.substring(0, comma));
    return decode(in.substring(comma + 1));
  }

  public abstract static class Encoder {
    public abstract String encode(String e);

    public abstract String decode(String e);
  }

  private KeyUtil() {}
}
