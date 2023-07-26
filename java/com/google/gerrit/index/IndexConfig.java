// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.index;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.eclipse.jgit.lib.Config;

/**
 * Implementation-specific configuration for secondary indexes.
 *
 * <p>Contains configuration that is tied to a specific index implementation but is otherwise
 * global, i.e. not tied to a specific {@link Index} and schema version.
 */
@AutoValue
public abstract class IndexConfig {
  private static final int DEFAULT_MAX_TERMS = 1024;
  private static final int DEFAULT_PAGE_SIZE_MULTIPLIER = 1;

  public static IndexConfig createDefault() {
    return builder().build();
  }

  public static Builder fromConfig(Config cfg) {
    Builder b = builder();
    setIfPresent(cfg, "defaultLimit", b::defaultLimit);
    setIfPresent(cfg, "maxLimit", b::maxLimit);
    setIfPresent(cfg, "maxPages", b::maxPages);
    setIfPresent(cfg, "maxTerms", b::maxTerms);
    setIfPresent(cfg, "pageSizeMultiplier", b::pageSizeMultiplier);
    setIfPresent(cfg, "maxPageSize", b::maxPageSize);
    setTypeOrDefault(cfg, b::type);
    setPaginationTypeOrDefault(cfg, b::paginationType);
    return b;
  }

  private static void setIfPresent(Config cfg, String name, IntConsumer setter) {
    int n = cfg.getInt("index", null, name, 0);
    if (n != 0) {
      setter.accept(n);
    }
  }

  private static void setTypeOrDefault(Config cfg, Consumer<String> setter) {
    String type = cfg != null ? cfg.getString("index", null, "type") : null;
    setter.accept(new IndexType(type).toString());
  }

  private static void setPaginationTypeOrDefault(Config cfg, Consumer<PaginationType> setter) {
    setter.accept(
        cfg != null ? cfg.getEnum("index", null, "paginationType", PaginationType.OFFSET) : null);
  }

  public static Builder builder() {
    return new AutoValue_IndexConfig.Builder()
        .defaultLimit(Integer.MAX_VALUE)
        .maxLimit(Integer.MAX_VALUE)
        .maxPages(Integer.MAX_VALUE)
        .maxTerms(DEFAULT_MAX_TERMS)
        .pageSizeMultiplier(DEFAULT_PAGE_SIZE_MULTIPLIER)
        .maxPageSize(Integer.MAX_VALUE)
        .type(IndexType.getDefault())
        .separateChangeSubIndexes(false)
        .paginationType(PaginationType.OFFSET);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder defaultLimit(int defaultLimit);

    public abstract int defaultLimit();

    public abstract Builder maxLimit(int maxLimit);

    public abstract int maxLimit();

    public abstract Builder maxPages(int maxPages);

    public abstract int maxPages();

    public abstract Builder maxTerms(int maxTerms);

    public abstract int maxTerms();

    public abstract Builder type(String type);

    public abstract String type();

    public abstract Builder separateChangeSubIndexes(boolean separate);

    public abstract Builder paginationType(PaginationType type);

    public abstract Builder pageSizeMultiplier(int pageSizeMultiplier);

    public abstract Builder maxPageSize(int maxPageSize);

    abstract IndexConfig autoBuild();

    public IndexConfig build() {
      IndexConfig cfg = autoBuild();
      checkLimit(cfg.defaultLimit(), "defaultLimit");
      checkLimit(cfg.maxLimit(), "maxLimit");
      checkLimit(cfg.maxPages(), "maxPages");
      checkLimit(cfg.maxTerms(), "maxTerms");
      checkLimit(cfg.pageSizeMultiplier(), "pageSizeMultiplier");
      checkLimit(cfg.maxPageSize(), "maxPageSize");
      return cfg;
    }
  }

  private static void checkLimit(int limit, String name) {
    checkArgument(limit > 0, "%s must be positive: %s", name, limit);
  }

  /**
   * Returns default limit for index queries, if the user does not provide one. If this is not set,
   * then the max permitted limit for each user is used, which might be much higher than intended.
   */
  public abstract int defaultLimit();

  /**
   * Returns maximum limit supported by the underlying index, or limited for performance reasons.
   */
  public abstract int maxLimit();

  /**
   * Returns maximum number of pages (limit / start) supported by the underlying index, or limited
   * for performance reasons.
   */
  public abstract int maxPages();

  /**
   * Returns maximum number of total index query terms supported by the underlying index, or limited
   * for performance reasons.
   */
  public abstract int maxTerms();

  /** Returns index type. */
  public abstract String type();

  /**
   * Returns whether different subsets of changes may be stored in different physical sub-indexes.
   */
  public abstract boolean separateChangeSubIndexes();

  /**
   * Returns pagination type to use when index queries are repeated to obtain the next set of
   * results.
   */
  public abstract PaginationType paginationType();

  /**
   * Returns multiplier to be used to determine the limit when queries are repeated to obtain the
   * next set of results.
   */
  public abstract int pageSizeMultiplier();

  /**
   * Returns maximum allowed limit when repeating index queries to obtain the next set of results.
   */
  public abstract int maxPageSize();
}
