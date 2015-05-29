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

package com.google.gerrit.server.index;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gwtorm.server.OrmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/** Specific version of a secondary index schema. */
public class Schema<T> {
  private static final Logger log = LoggerFactory.getLogger(Schema.class);

  public static class Values<T> {
    private final FieldDef<T, ?> field;
    private final Iterable<?> values;

    private Values(FieldDef<T, ?> field, Iterable<?> values) {
      this.field = field;
      this.values = values;
    }

    public FieldDef<T, ?> getField() {
      return field;
    }

    public Iterable<?> getValues() {
      return values;
    }
  }

  private static <T> FieldDef<T, ?> checkSame(FieldDef<T, ?> f1,
      FieldDef<T, ?> f2) {
    checkState(f1 == f2, "Mismatched %s fields: %s != %s",
        f1.getName(), f1, f2);
    return f1;
  }

  private final ImmutableMap<String, FieldDef<T, ?>> fields;
  private int version;

  protected Schema(Iterable<FieldDef<T, ?>> fields) {
    this(0, fields);
  }

  @VisibleForTesting
  public Schema(int version, Iterable<FieldDef<T, ?>> fields) {
    this.version = version;
    ImmutableMap.Builder<String, FieldDef<T, ?>> b = ImmutableMap.builder();
    for (FieldDef<T, ?> f : fields) {
      b.put(f.getName(), f);
    }
    this.fields = b.build();
  }

  public final int getVersion() {
    return version;
  }

  /**
   * Get all fields in this schema.
   * <p>
   * This is primarily useful for iteration. Most callers should prefer one
   * of the helper methods {@link #getField(FieldDef, FieldDef...)} or {@link
   * #hasField(FieldDef)} to looking up fields by name
   *
   * @return all fields in this schema indexed by name.
   */
  public final ImmutableMap<String, FieldDef<T, ?>> getFields() {
    return fields;
  }

  /**
   * Look up fields in this schema.
   *
   * @param first the preferred field to look up.
   * @param rest additional fields to look up.
   * @return the first field in the schema matching {@code first} or {@code
   *     rest}, in order, or absent if no field matches.
   */
  @SafeVarargs
  public final Optional<FieldDef<T, ?>> getField(FieldDef<T, ?> first,
      FieldDef<T, ?>... rest) {
    FieldDef<T, ?> field = fields.get(first.getName());
    if (field != null) {
      return Optional.<FieldDef<T, ?>> of(checkSame(field, first));
    }
    for (FieldDef<T, ?> f : rest) {
      field = fields.get(f.getName());
      if (field != null) {
        return Optional.<FieldDef<T, ?>> of(checkSame(field, f));
      }
    }
    return Optional.absent();
  }

  /**
   * Check whether a field is present in this schema.
   *
   * @param field field to look up.
   * @return whether the field is present.
   */
  public final boolean hasField(FieldDef<T, ?> field) {
    FieldDef<T, ?> f = fields.get(field.getName());
    if (f == null) {
      return false;
    }
    checkSame(f, field);
    return true;
  }

  /**
   * Build all fields in the schema from an input object.
   * <p>
   * Null values are omitted, as are fields which cause errors, which are
   * logged.
   *
   * @param obj input object.
   * @param fillArgs arguments for filling fields.
   * @return all non-null field values from the object.
   */
  public final Iterable<Values<T>> buildFields(
      final T obj, final FillArgs fillArgs) {
    return FluentIterable.from(fields.values())
        .transform(new Function<FieldDef<T, ?>, Values<T>>() {
          @Override
          public Values<T> apply(FieldDef<T, ?> f) {
            Object v;
            try {
              v = f.get(obj, fillArgs);
            } catch (OrmException e) {
              log.error(String.format("error getting field %s of %s",
                  f.getName(), obj), e);
              return null;
            }
            if (v == null) {
              return null;
            } else if (f.isRepeatable()) {
              return new Values<>(f, (Iterable<?>) v);
            } else {
              return new Values<>(f, Collections.singleton(v));
            }
          }
        }).filter(Predicates.notNull());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .addValue(fields.keySet())
        .toString();
  }

  void setVersion(int version) {
    this.version = version;
  }
}
