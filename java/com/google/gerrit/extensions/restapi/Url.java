// Copyright (C) 2012 The Android Open Source Project
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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/** URL related utility functions. */
public final class Url {
  /**
   * Encode a path segment, escaping characters not valid for a URL.
   *
   * <p>The following characters are not escaped:
   *
   * <ul>
   *   <li>{@code a..z, A..Z, 0..9}
   *   <li>{@code . - * _}
   * </ul>
   *
   * <p>' ' (space) is encoded as '+'.
   *
   * <p>All other characters (including '/') are converted to the triplet "%xy" where "xy" is the
   * hex representation of the character in UTF-8.
   *
   * @param component a string containing text to encode.
   * @return a string with all invalid URL characters escaped.
   */
  public static String encode(String component) {
    if (component != null) {
      try {
        return URLEncoder.encode(component, UTF_8.name());
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException("JVM must support UTF-8", e);
      }
    }
    return null;
  }

  /** Decode a URL encoded string, e.g. from {@code "%2F"} to {@code "/"}. */
  public static String decode(String str) {
    if (str != null) {
      try {
        return URLDecoder.decode(str, UTF_8.name());
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException("JVM must support UTF-8", e);
      }
    }
    return null;
  }

  private Url() {}
}
