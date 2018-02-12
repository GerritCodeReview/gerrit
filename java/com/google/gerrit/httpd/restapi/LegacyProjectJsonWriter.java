// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.httpd.restapi;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * Adds an additional field named {@code project} and populates it with the value of {@code
 * repository} whenever a {@code repository} field is written.
 */
public class LegacyProjectJsonWriter extends JsonWriter {

  private String lastName;

  LegacyProjectJsonWriter(Writer out) {
    super(out);
  }

  @Override
  public JsonWriter name(String name) throws IOException {
    super.name(name);
    lastName = name;
    return this;
  }

  @Override
  public JsonWriter value(String value) throws IOException {
    super.value(value);
    if ("repository".equals(lastName)) {
      name("project").value(value);
    }
    return this;
  }
}
