// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.common;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

/** Helpers for resolving a {@link Charset} from a possibly-null, possibly-invalid charset name. */
public final class CharsetUtil {
  /**
   * Resolves a charset by name, falling back to UTF-8 when {@code name} is null.
   *
   * <p>An invalid or unsupported name is reported as a checked {@link IOException} so that callers
   * decoding untrusted input (e.g. a request charset) do not leak an unchecked charset exception.
   *
   * @throws IOException if {@code name} is not a valid or supported charset
   */
  public static Charset forNameOrUtf8(@Nullable String name) throws IOException {
    if (name == null) {
      return UTF_8;
    }
    try {
      return Charset.forName(name);
    } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
      throw new IOException("Unsupported charset: " + name, e);
    }
  }

  private CharsetUtil() {}
}
