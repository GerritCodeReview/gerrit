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

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;

/** Replaces {@code project} with {@code repository}. */
public class ProjectCompatibilityJsonReader extends JsonReader {

  private final JsonReader delegate;

  ProjectCompatibilityJsonReader(Reader reader) {
    super(new UnreadableReader());
    delegate = new JsonReader(reader);
  }

  @Override
  public void beginArray() throws IOException {
    delegate.beginArray();
  }

  @Override
  public void endArray() throws IOException {
    delegate.endArray();
  }

  @Override
  public void beginObject() throws IOException {
    delegate.beginObject();
  }

  @Override
  public void endObject() throws IOException {
    delegate.endObject();
  }

  @Override
  public boolean hasNext() throws IOException {
    return delegate.hasNext();
  }

  @Override
  public JsonToken peek() throws IOException {
    return delegate.peek();
  }

  public void promoteNameToValue() throws IOException {
    // expect(JsonToken.NAME);
    // Iterator<?> i = (Iterator<?>) peekStack();
    // Map.Entry<?, ?> entry = (Map.Entry<?, ?>) i.next();
    // push(entry.getValue());
    // push(new JsonPrimitive((String) entry.getKey()));
  }

  @Override
  public String nextName() throws IOException {
    String nextName = delegate.nextName();
    if ("project".equals(nextName)) {
      return "repository";
    }
    return nextName;
  }

  @Override
  public String nextString() throws IOException {
    return delegate.nextString();
  }

  @Override
  public boolean nextBoolean() throws IOException {
    return delegate.nextBoolean();
  }

  @Override
  public void nextNull() throws IOException {
    delegate.nextNull();
  }

  @Override
  public double nextDouble() throws IOException {
    return delegate.nextDouble();
  }

  @Override
  public long nextLong() throws IOException {
    return delegate.nextLong();
  }

  @Override
  public int nextInt() throws IOException {
    return delegate.nextInt();
  }

  @Override
  public void skipValue() throws IOException {
    delegate.skipValue();
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }

  /** A Reader that throws for all methods. */
  private static final class UnreadableReader extends Reader {
    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
      throw new UnsupportedOperationException("UnreadableReader doesn't support any operations");
    }

    @Override
    public void close() throws IOException {
      throw new UnsupportedOperationException("UnreadableReader doesn't support any operations");
    }
  }
}
