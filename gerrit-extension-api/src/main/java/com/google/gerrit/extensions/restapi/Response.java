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

/** Special return value to mean specific HTTP status codes in a REST API. */
public abstract class Response<T> {
  @SuppressWarnings({"rawtypes"})
  private static final Response NONE = new None();

  /** HTTP 200 OK: pointless wrapper for type safety. */
  public static <T> Response<T> ok(T value) {
    return new Impl<T>(200, value);
  }

  /** HTTP 201 Created: typically used when a new resource is made. */
  public static <T> Response<T> created(T value) {
    return new Impl<T>(201, value);
  }

  /** HTTP 204 No Content: typically used when the resource is deleted. */
  @SuppressWarnings("unchecked")
  public static <T> Response<T> none() {
    return NONE;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <T> T unwrap(T obj) {
    while (obj instanceof Response) {
      obj = (T) ((Response) obj).value();
    }
    return obj;
  }

  public abstract int statusCode();
  public abstract T value();
  public abstract String toString();

  private static final class Impl<T> extends Response<T> {
    private final int statusCode;
    private final T value;

    private Impl(int sc, T val) {
      statusCode = sc;
      value = val;
    }

    @Override
    public int statusCode() {
      return statusCode;
    }

    @Override
    public T value() {
      return value;
    }

    @Override
    public String toString() {
      return "[" + statusCode() + "] " + value();
    }
  }

  private static final class None extends Response<Object> {
    private None() {
    }

    @Override
    public int statusCode() {
      return 204;
    }

    public Object value() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "[204 No Content] None";
    }
  }
}
