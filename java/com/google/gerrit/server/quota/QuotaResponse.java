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
import java.util.OptionalLong;
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

  public static QuotaResponse ok(long tokens) {
    return new AutoValue_QuotaResponse.Builder().status(Status.OK).availableTokens(tokens).build();
  }

  /**
   * Creates a successful quota response indicating that {@code tokens} are available.
   *
   * <p>The returned response has status {@link Status#OK}: the caller has not exceeded the quota.
   *
   * <p>{@code exceededQuotaMessage} specifies the client-facing message that should be used if a
   * future request attempts to consume more than the {@code tokens} reported here and must
   * therefore be rejected as exceeding quota. This message is not an error for the current request;
   * it is metadata supplied by the enforcer for potential future quota failures.
   *
   * @param tokens the number of quota tokens currently available for this request context
   * @param exceededQuotaMessage the message to surface to clients if a later request would exceed
   *     the available {@code tokens}
   * @return a {@code QuotaResponse} representing a successful quota check with the given metadata
   */
  public static QuotaResponse ok(long tokens, String exceededQuotaMessage) {
    return new AutoValue_QuotaResponse.Builder()
        .status(Status.OK)
        .availableTokens(tokens)
        .message(exceededQuotaMessage)
        .build();
  }

  public static QuotaResponse noOp() {
    return new AutoValue_QuotaResponse.Builder().status(Status.NO_OP).build();
  }

  public static QuotaResponse error(String message) {
    return new AutoValue_QuotaResponse.Builder().status(Status.ERROR).message(message).build();
  }

  public abstract Status status();

  public abstract Optional<Long> availableTokens();

  public abstract Optional<String> message();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract QuotaResponse.Builder status(Status status);

    public abstract QuotaResponse.Builder availableTokens(Long tokens);

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

    public OptionalLong availableTokens() {
      return responses().stream()
          .filter(r -> r.status().isOk() && r.availableTokens().isPresent())
          .mapToLong(r -> r.availableTokens().get())
          .min();
    }

    public ImmutableList<QuotaResponse> error() {
      return responses().stream().filter(r -> r.status().isError()).collect(toImmutableList());
    }

    /**
     * Returns the quota-exceeded message provided by the response with the lowest number of
     * available tokens.
     *
     * <p>This message is intended to be shown to clients when a request would exceed the aggregated
     * available quota. It allows quota enforcers to supply custom, client-facing explanations for
     * quota-rejection scenarios.
     *
     * <p>Only responses that report {@link #availableTokens() available tokens} are considered.
     * Among these, the response with the smallest token count (i.e. the most restrictive enforcer)
     * is selected. If multiple such responses exist, their messages are joined using a comma.
     *
     * @return an {@code Optional} containing the quota-exceeded message (or comma-joined messages)
     *     from the most restrictive available-tokens responses, or an empty {@code Optional} if
     *     none of the considered responses provides such a message.
     */
    public Optional<String> mostRestrictiveQuotaExceededMessage() {
      return availableTokens().stream()
          .mapToObj(
              minAvailableTokens ->
                  responses().stream()
                      .filter(
                          r ->
                              r.availableTokens().isPresent()
                                  && r.availableTokens().get() == minAvailableTokens)
                      .map(QuotaResponse::message)
                      .flatMap(Optional::stream)
                      .collect(Collectors.joining(",")))
          .filter(s -> !s.isEmpty())
          .findFirst();
    }

    public String errorMessage() {
      return error().stream()
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
