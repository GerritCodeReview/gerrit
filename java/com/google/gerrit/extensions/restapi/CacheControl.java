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

import java.util.concurrent.TimeUnit;

public class CacheControl {

  public enum Type {
    @SuppressWarnings("hiding")
    NONE,
    PUBLIC,
    PRIVATE
  }

  public static final CacheControl NONE = new CacheControl(Type.NONE, 0, null);

  public static CacheControl PUBLIC(long age, TimeUnit unit) {
    return new CacheControl(Type.PUBLIC, age, unit);
  }

  public static CacheControl PRIVATE(long age, TimeUnit unit) {
    return new CacheControl(Type.PRIVATE, age, unit);
  }

  private final Type type;
  private final long age;
  private final TimeUnit unit;
  private boolean mustRevalidate;

  private CacheControl(Type type, long age, TimeUnit unit) {
    this.type = type;
    this.age = age;
    this.unit = unit;
  }

  public Type getType() {
    return type;
  }

  public long getAge() {
    return age;
  }

  public TimeUnit getUnit() {
    return unit;
  }

  public boolean isMustRevalidate() {
    return mustRevalidate;
  }

  public CacheControl setMustRevalidate() {
    mustRevalidate = true;
    return this;
  }
}
