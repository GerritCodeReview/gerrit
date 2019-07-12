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

package com.google.gerrit.server.quota;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@AutoValue
public abstract class QuotaResponse {
  public enum Status {
    /** The quota requests succeeded. */
    OK,

    /**
     * The quota succeeded, but was a no-op because the plugin does not enforce this quota group
     * (equivalent to OK, but relevant for debugging).
     */
    NO_OP,

    /**
     * The requested quota could not be allocated. This status code is not used to indicate
     * processing failures as these are propagated as {@code RuntimeException}s.
     */
    ERROR;

    public boolean isOk() {
      return this == OK;
    }

    public boolean isError() {
      return this == ERROR;
    }
  }

  public static QuotaResponse ok() {
    return new AutoValue_QuotaResponse.Builder().status(Status.OK).build();
  }

  public static QuotaResponse noOp() {
    return new AutoValue_QuotaResponse.Builder().status(Status.NO_OP).build();
  }

  public static QuotaResponse error(String message) {
    return new AutoValue_QuotaResponse.Builder().status(Status.ERROR).message(message).build();
  }

  public abstract Status status();

  public abstract Optional<String> message();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract QuotaResponse.Builder status(Status status);

    public abstract QuotaResponse.Builder message(String message);

    public abstract QuotaResponse build();
  }

  @AutoValue
  public abstract static class Aggregated {
    public static Aggregated create(Collection<QuotaResponse> responses) {
      return new AutoValue_QuotaResponse_Aggregated(ImmutableList.copyOf(responses));
    }

    protected abstract ImmutableList<QuotaResponse> responses();

    public boolean hasError() {
      return responses().stream().anyMatch(r -> r.status().isError());
    }

    public ImmutableList<QuotaResponse> all() {
      return responses();
    }

    public ImmutableList<QuotaResponse> ok() {
      return responses().stream().filter(r -> r.status().isOk()).collect(toImmutableList());
    }

    public ImmutableList<QuotaResponse> error() {
      return responses().stream().filter(r -> r.status().isError()).collect(toImmutableList());
    }

    public String errorMessage() {
      return error()
          .stream()
          .map(QuotaResponse::message)
          .flatMap(Streams::stream)
          .collect(Collectors.joining(", "));
    }

    public void throwOnError() throws QuotaException {
      String errorMessage = errorMessage();
      if (!Strings.isNullOrEmpty(errorMessage)) {
        throw new QuotaException(errorMessage);
      }
    }
  }
}
