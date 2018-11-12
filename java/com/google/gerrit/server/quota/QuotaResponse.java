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
import com.google.common.collect.ImmutableList;
import java.util.List;
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

    /** The quota requests failed. */
    ERROR;

    public boolean isOk() {
      return this == OK || this == NO_OP;
    }
  }

  public static QuotaResponse ok() {
    return new AutoValue_QuotaResponse.Builder()
        .status(Status.OK)
        .message(Optional.empty())
        .build();
  }

  public static QuotaResponse noOp() {
    return new AutoValue_QuotaResponse.Builder()
        .status(Status.NO_OP)
        .message(Optional.empty())
        .build();
  }

  public static QuotaResponse error(String message) {
    return new AutoValue_QuotaResponse.Builder()
        .status(Status.ERROR)
        .message(Optional.of(message))
        .build();
  }

  public abstract Status status();

  public abstract Optional<String> message();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract QuotaResponse.Builder status(Status status);

    public abstract QuotaResponse.Builder message(Optional<String> message);

    public abstract QuotaResponse build();
  }

  public static class Aggregated {
    private final ImmutableList<QuotaResponse> responses;

    Aggregated(List<QuotaResponse> responses) {
      this.responses = ImmutableList.copyOf(responses);
    }

    public boolean isOk() {
      return responses.stream().noneMatch(r -> !r.status().isOk());
    }

    public ImmutableList<QuotaResponse> all() {
      return responses;
    }

    public ImmutableList<QuotaResponse> ok() {
      return responses.stream().filter(r -> r.status().isOk()).collect(toImmutableList());
    }

    public ImmutableList<QuotaResponse> error() {
      return responses.stream().filter(r -> !r.status().isOk()).collect(toImmutableList());
    }

    public String errorMessages() {
      return error().stream().map(r -> r.message().get()).collect(Collectors.joining(", "));
    }
  }
}
