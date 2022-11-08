package com.google.gerrit.server.change;

import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.common.Nullable;
import java.util.Map;

/** Utilities for validation options parsing. */
public final class ValidationOptionsUtil {
  public static ImmutableListMultimap<String, String> getValidateOptionsAsMultimap(
      @Nullable Map<String, String> validationOptions) {
    if (validationOptions == null) {
      return ImmutableListMultimap.of();
    }

    ImmutableListMultimap.Builder<String, String> validationOptionsBuilder =
        ImmutableListMultimap.builder();
    validationOptions
        .entrySet()
        .forEach(e -> validationOptionsBuilder.put(e.getKey(), e.getValue()));
    return validationOptionsBuilder.build();
  }

  private ValidationOptionsUtil() {}
}
