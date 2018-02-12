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
public class ProjectCompatibilityJsonWriter extends JsonWriter {

  private final JsonWriter delegate;
  private String lastName;

  ProjectCompatibilityJsonWriter(Writer out) {
    super(new UnreadableWriter());
    this.delegate = new JsonWriter(out);
  }

  @Override
  public JsonWriter beginArray() throws IOException {
    delegate.beginArray();
    return this;
  }

  @Override
  public JsonWriter endArray() throws IOException {
    delegate.endArray();
    return this;
  }

  @Override
  public JsonWriter beginObject() throws IOException {
    delegate.beginObject();
    return this;
  }

  @Override
  public JsonWriter endObject() throws IOException {
    delegate.endObject();
    return this;
  }

  @Override
  public JsonWriter name(String name) throws IOException {
    delegate.name(name);
    lastName = name;
    return this;
  }

  @Override
  public JsonWriter value(String value) throws IOException {
    delegate.value(value);
    if ("repository".equals(lastName)) {
      name("project").value(value);
    }
    return this;
  }

  @Override
  public JsonWriter jsonValue(String value) throws IOException {
    delegate.jsonValue(value);
    return this;
  }

  @Override
  public JsonWriter nullValue() throws IOException {
    delegate.nullValue();
    return this;
  }

  @Override
  public JsonWriter value(boolean value) throws IOException {
    delegate.value(value);
    return this;
  }

  @Override
  public JsonWriter value(Boolean value) throws IOException {
    delegate.value(value);
    return this;
  }

  @Override
  public JsonWriter value(double value) throws IOException {
    delegate.value(value);
    return this;
  }

  @Override
  public JsonWriter value(long value) throws IOException {
    delegate.value(value);
    return this;
  }

  @Override
  public JsonWriter value(Number value) throws IOException {
    delegate.value(value);
    return this;
  }

  @Override
  public void flush() throws IOException {
    delegate.flush();
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }

  /** A Writer that throws for all methods. */
  private static final class UnreadableWriter extends Writer {

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
      throw new UnsupportedOperationException("UnreadableReader doesn't support any operations");
    }

    @Override
    public void flush() throws IOException {
      throw new UnsupportedOperationException("UnreadableReader doesn't support any operations");
    }

    @Override
    public void close() throws IOException {
      throw new UnsupportedOperationException("UnreadableReader doesn't support any operations");
    }
  }
}
