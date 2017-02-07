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

import java.util.concurrent.TimeUnit;

/** Special return value to mean specific HTTP status codes in a REST API. */
public abstract class Response<T> {
  @SuppressWarnings({"rawtypes"})
  private static final Response NONE = new None();

  /** HTTP 200 OK: pointless wrapper for type safety. */
  public static <T> Response<T> ok(T value) {
    return new Impl<>(200, value);
  }

  public static <T> Response<T> withMustRevalidate(T value) {
    return ok(value).caching(CacheControl.PRIVATE(0, TimeUnit.SECONDS).setMustRevalidate());
  }

  /** HTTP 201 Created: typically used when a new resource is made. */
  public static <T> Response<T> created(T value) {
    return new Impl<>(201, value);
  }

  /** HTTP 202 Accepted: accepted as background task. */
  public static Accepted accepted(String location) {
    return new Accepted(location);
  }

  /** HTTP 204 No Content: typically used when the resource is deleted. */
  @SuppressWarnings("unchecked")
  public static <T> Response<T> none() {
    return NONE;
  }

  /** HTTP 302 Found: temporary redirect to another URL. */
  public static Redirect redirect(String location) {
    return new Redirect(location);
  }

  /** Arbitrary status code with wrapped result. */
  public static <T> Response<T> withStatusCode(int statusCode, T value) {
    return new Impl<>(statusCode, value);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <T> T unwrap(T obj) {
    while (obj instanceof Response) {
      obj = (T) ((Response) obj).value();
    }
    return obj;
  }

  public abstract boolean isNone();

  public abstract int statusCode();

  public abstract T value();

  public abstract CacheControl caching();

  public abstract Response<T> caching(CacheControl c);

  @Override
  public abstract String toString();

  private static final class Impl<T> extends Response<T> {
    private final int statusCode;
    private final T value;
    private CacheControl caching = CacheControl.NONE;

    private Impl(int sc, T val) {
      statusCode = sc;
      value = val;
    }

    @Override
    public boolean isNone() {
      return false;
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
    public CacheControl caching() {
      return caching;
    }

    @Override
    public Response<T> caching(CacheControl c) {
      caching = c;
      return this;
    }

    @Override
    public String toString() {
      return "[" + statusCode() + "] " + value();
    }
  }

  private static final class None extends Response<Object> {
    private None() {}

    @Override
    public boolean isNone() {
      return true;
    }

    @Override
    public int statusCode() {
      return 204;
    }

    @Override
    public Object value() {
      throw new UnsupportedOperationException();
    }

    @Override
    public CacheControl caching() {
      return CacheControl.NONE;
    }

    @Override
    public Response<Object> caching(CacheControl c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "[204 No Content] None";
    }
  }

  /** An HTTP redirect to another location. */
  public static final class Redirect {
    private final String location;

    private Redirect(String url) {
      this.location = url;
    }

    public String location() {
      return location;
    }

    @Override
    public int hashCode() {
      return location.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Redirect && ((Redirect) o).location.equals(location);
    }

    @Override
    public String toString() {
      return String.format("[302 Redirect] %s", location);
    }
  }

  /** Accepted as task for asynchronous execution. */
  public static final class Accepted {
    private final String location;

    private Accepted(String url) {
      this.location = url;
    }

    public String location() {
      return location;
    }

    @Override
    public int hashCode() {
      return location.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Accepted && ((Accepted) o).location.equals(location);
    }

    @Override
    public String toString() {
      return String.format("[202 Accepted] %s", location);
    }
  }
}
