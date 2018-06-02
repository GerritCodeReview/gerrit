// Copyright (C) 2018 The Android Open Source Project, 2009-2015 Elasticsearch
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

package com.google.gerrit.elasticsearch.builders;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.google.common.base.Charsets;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.Date;

/** A trimmed down and modified version of org.elasticsearch.common.xcontent.XContentBuilder. */
public final class XContentBuilder implements Closeable {

  private final JsonGenerator generator;

  private final ByteArrayOutputStream bos = new ByteArrayOutputStream();

  /**
   * Constructs a new builder. Make sure to call {@link #close()} when the builder is done with.
   * Inspired from org.elasticsearch.common.xcontent.json.JsonXContent static block.
   */
  public XContentBuilder() throws IOException {
    JsonFactory jsonFactory = new JsonFactory();
    jsonFactory.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
    jsonFactory.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, true);
    jsonFactory.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
    jsonFactory.configure(
        JsonFactory.Feature.FAIL_ON_SYMBOL_HASH_OVERFLOW,
        false); // this trips on many mappings now...
    this.generator = jsonFactory.createGenerator(bos, JsonEncoding.UTF8);
  }

  public XContentBuilder startObject(String name) throws IOException {
    field(name);
    startObject();
    return this;
  }

  public XContentBuilder startObject() throws IOException {
    generator.writeStartObject();
    return this;
  }

  public XContentBuilder endObject() throws IOException {
    generator.writeEndObject();
    return this;
  }

  public void startArray(String name) throws IOException {
    field(name);
    startArray();
  }

  private void startArray() throws IOException {
    generator.writeStartArray();
  }

  public void endArray() throws IOException {
    generator.writeEndArray();
  }

  public XContentBuilder field(String name) throws IOException {
    generator.writeFieldName(name);
    return this;
  }

  public XContentBuilder field(String name, String value) throws IOException {
    field(name);
    generator.writeString(value);
    return this;
  }

  public XContentBuilder field(String name, int value) throws IOException {
    field(name);
    generator.writeNumber(value);
    return this;
  }

  public XContentBuilder field(String name, Iterable<?> value) throws IOException {
    startArray(name);
    for (Object o : value) {
      value(o);
    }
    endArray();
    return this;
  }

  public XContentBuilder field(String name, Object value) throws IOException {
    field(name);
    writeValue(value);
    return this;
  }

  public XContentBuilder value(Object value) throws IOException {
    writeValue(value);
    return this;
  }

  public XContentBuilder field(String name, boolean value) throws IOException {
    field(name);
    generator.writeBoolean(value);
    return this;
  }

  public XContentBuilder value(String value) throws IOException {
    generator.writeString(value);
    return this;
  }

  @Override
  public void close() {
    try {
      generator.close();
    } catch (IOException e) {
      // ignore
    }
  }

  /** Returns a string representation of the builder (only applicable for text based xcontent). */
  public String string() {
    close();
    byte[] bytesArray = bos.toByteArray();
    return new String(bytesArray, Charsets.UTF_8);
  }

  private void writeValue(Object value) throws IOException {
    if (value == null) {
      generator.writeNull();
      return;
    }
    Class<?> type = value.getClass();
    if (type == String.class) {
      generator.writeString((String) value);
    } else if (type == Integer.class) {
      generator.writeNumber(((Integer) value));
    } else if (type == byte[].class) {
      generator.writeBinary((byte[]) value);
    } else if (value instanceof Date) {
      generator.writeString(ISO_INSTANT.format(((Date) value).toInstant()));
    } else {
      // if this is a "value" object, like enum, DistanceUnit, ..., just toString it
      // yea, it can be misleading when toString a Java class, but really, jackson should be used in
      // that case
      generator.writeString(value.toString());
      // throw new ElasticsearchIllegalArgumentException("type not supported for generic value
      // conversion: " + type);
    }
  }
}
