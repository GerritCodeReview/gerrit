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

package com.google.gerrit.acceptance.rest.util;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.Ignore;

/** Data container for test REST requests. */
@Ignore
@AutoValue
public abstract class RestCall {
  public enum Method {
    GET,
    PUT,
    POST,
    DELETE
  }

  public static RestCall get(String uriFormat) {
    return builder(Method.GET, uriFormat).build();
  }

  public static RestCall put(String uriFormat) {
    return builder(Method.PUT, uriFormat).build();
  }

  public static RestCall post(String uriFormat) {
    return builder(Method.POST, uriFormat).build();
  }

  public static RestCall delete(String uriFormat) {
    return builder(Method.DELETE, uriFormat).build();
  }

  public static Builder builder(Method httpMethod, String uriFormat) {
    return new AutoValue_RestCall.Builder().httpMethod(httpMethod).uriFormat(uriFormat);
  }

  public abstract Method httpMethod();

  public abstract String uriFormat();

  public abstract Optional<Integer> expectedResponseCode();

  public abstract Optional<String> expectedMessage();

  public String uri(String... args) {
    String uriFormat = uriFormat();
    int expectedArgNum = StringUtils.countMatches(uriFormat, "%s");
    checkState(
        args.length == expectedArgNum,
        "uriFormat %s needs %s arguments, got only %s: %s",
        uriFormat,
        expectedArgNum,
        args.length,
        args);
    return String.format(uriFormat, (Object[]) args);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder httpMethod(Method httpMethod);

    public abstract Builder uriFormat(String uriFormat);

    public abstract Builder expectedResponseCode(int expectedResponseCode);

    public abstract Builder expectedMessage(String expectedMessage);

    public abstract RestCall build();
  }
}
