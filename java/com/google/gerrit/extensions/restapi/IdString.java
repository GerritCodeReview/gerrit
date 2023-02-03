// Copyright (C) 2013 The Android Open Source Project
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

/**
 * Resource identifier split out from a URL.
 *
 * <p>Identifiers are URL encoded and usually need to be decoded.
 */
public class IdString {
  /** Construct an identifier from an already encoded string. */
  public static IdString fromUrl(String id) {
    return new IdString(id);
  }

  /** Construct an identifier from an already decoded string. */
  public static IdString fromDecoded(String id) {
    return new IdString(Url.encode(id));
  }

  private final String urlEncoded;

  private IdString(String s) {
    urlEncoded = s;
  }

  /** Returns the decoded value of the string. */
  public String get() {
    String data = urlEncoded;

    // URLs use percentage encoding which replaces unsafe ASCII characters with a '%' followed by
    // two hexadecimal digits. If there is '%' that is not followed by two hexadecimal digits
    // Url.decode(String) fails with an IllegalArgumentException. To prevent this replace any '%'
    // hat is not followed by two hexadecimal digits by "%25", which is the URL encoding for '%',
    // before calling Url.decode(String).
    data = data.replaceAll("%(?![0-9a-fA-F]{2})", "%25");

    return Url.decode(data);
  }

  /** Returns true if the string is the empty string. */
  public boolean isEmpty() {
    return urlEncoded.isEmpty();
  }

  /** Returns the original URL encoding supplied by the client. */
  public String encoded() {
    return urlEncoded;
  }

  @Override
  public int hashCode() {
    return urlEncoded.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof IdString) {
      return urlEncoded.equals(((IdString) other).urlEncoded);
    }
    return false;
  }

  @Override
  public String toString() {
    return encoded();
  }
}
