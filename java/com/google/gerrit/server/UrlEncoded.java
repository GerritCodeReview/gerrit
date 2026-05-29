// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.extensions.restapi.Url;
import java.util.LinkedHashMap;
import java.util.Map;

public class UrlEncoded extends LinkedHashMap<String, String> {
  private static final long serialVersionUID = 1L;

  private String url;

  public UrlEncoded() {}

  public UrlEncoded(String url) {
    this.url = url;
  }

  @Override
  public String toString() {
    final StringBuilder buffer = new StringBuilder();
    char separator = 0;
    if (url != null) {
      separator = '?';
      buffer.append(url);
    }
    for (Map.Entry<String, String> entry : entrySet()) {
      final String key = entry.getKey();
      final String val = entry.getValue();
      if (separator != 0) {
        buffer.append(separator);
      }
      buffer.append(Url.encode(key));
      buffer.append('=');
      if (val != null) {
        buffer.append(Url.encode(val));
      }
      separator = '&';
    }
    return buffer.toString();
  }
}
