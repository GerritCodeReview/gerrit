package com.google.gerrit.index;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Definition of a field stored in the secondary index.
 *
 * @param <I> input type from which documents are created and search results are returned.
 * @param <T> type that should be extracted from the input object when converting to an index
 *     document.
 */
@AutoValue
public abstract class  Field<I, T> {

  public static <I, T> Builder<I, T> builder(String name, Class<T> fieldType) {
    return new AutoValue_Field.Builder<I, T>().name(name).fieldType(fieldType);
  }

  public class FieldSpec<I, T> {
    private final String name;
    private final SearchOptions searchOptions;

    public FieldSpec(String name, SearchOptions searchOptions) {
      this.name = name;
      this.searchOptions = searchOptions;
    }
    public String getName() {
      return name;
    }
    public SearchOptions getSearchOptions() {
      return searchOptions;
    }

    public <I, T> Field<I, T> getField(){
      return (Field<I, T>) Field.this;
    }

    public boolean setIfPossible(I object, StoredValue doc) {
      return getField().setIfPossible(object, doc);
    }
  }


  public FieldSpec addFieldSpec(String name, SearchOptions searchOptions){
    FieldSpec fieldSpec = new FieldSpec(name, searchOptions);
    fieldSpecs.add(fieldSpec);
    return fieldSpec;
  }

  @FunctionalInterface
  public interface Getter<I, T> {
    @Nullable
    T get(I input) throws IOException;
  }

  @FunctionalInterface
  public interface Setter<I, T> {
    void set(I object, T value);
  }


  @AutoValue.Builder
  public abstract static class Builder<I, T> {

    public abstract Field.Builder<I, T> name(String name);
    public abstract Builder<I, T> type(FieldType<?> type);
    /** Allow reading the actual data from the index. */
    public abstract Builder<I, T> stored(boolean stored);
    public abstract Builder<I, T> repeatable(boolean repeatable);
    public abstract Builder<I, T> getter(Field.Getter<I, T> getter);
    public abstract Builder<I, T> fieldSetter(Optional<Field.Setter<I, T>> setter);
    public abstract Builder<I, T> fieldType(Class<T> value);


    abstract Field autoBuild();  // not public

    public final Field build() {
      Field field = autoBuild();
      checkName(field.name());
      return field;
    }

    public final Field build(Field.Getter<I, T> getter, Field.Setter<I, T> setter) {
      return this.getter(getter).fieldSetter(Optional.of(setter)).build();
    }
    public final Field build(Field.Getter<I, T> getter) {
      return this.getter(getter).fieldSetter(Optional.empty()).build();
    }
  }

  public abstract String name();
  public abstract FieldType<?> type();

  /** Allow reading the actual data from the index. */
  public abstract boolean stored();

  public abstract boolean repeatable();

  public abstract Getter<I, T> getter();

  public abstract Optional<Setter<I, T>> fieldSetter();

  public abstract Class<T> fieldType();

  public ImmutableList<FieldSpec> getFieldSpecs() {
    return ImmutableList.copyOf(fieldSpecs);
  }

  public void setFieldSpecs(
      ImmutableList<FieldSpec> fieldSpecs) {
    this.fieldSpecs = fieldSpecs;
  }

  private List<FieldSpec> fieldSpecs = new ArrayList<>();



  private static String checkName(String name) {
    CharMatcher m = CharMatcher.anyOf("abcdefghijklmnopqrstuvwxyz0123456789_");
    checkArgument(name != null && m.matchesAllOf(name), "illegal field name: %s", name);
    return name;
  }

  /**
   * Get the field contents from the input object.
   *
   * @param input input object.
   * @return the field value(s) to index.
   */
  @Nullable
  public T get(I input) {
    try {
      return getter().get(input);
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  /**
   * Set the field contents back to an object. Used to reconstruct fields from indexed values. No-op
   * if the field can't be reconstructed.
   *
   * @param object input object.
   * @param doc indexed document
   * @return {@code true} if the field was set, {@code false} otherwise
   */
  @SuppressWarnings("unchecked")
  public boolean setIfPossible(I object, StoredValue doc) {
    if (!fieldSetter().isPresent()) {
      return false;
    }

    if (this.fieldType().equals(String.class)) {
      fieldSetter().get().set(object, (T) (repeatable() ? doc.asStrings() : doc.asString()));
      return true;
    } else if (this.fieldType().equals(Integer.class)) {
      fieldSetter().get().set(object, (T) (repeatable() ? doc.asIntegers() : doc.asInteger()));
      return true;
    } else if (this.fieldType().equals(Long.class)) {
      fieldSetter().get().set(object, (T) (repeatable() ? doc.asLongs() : doc.asLong()));
      return true;
    } else if (FieldType.STORED_ONLY.getName().equals(type().getName())) {
      fieldSetter().get().set(object, (T) (repeatable() ? doc.asByteArrays() : doc.asByteArray()));
      return true;
    } else if (this.fieldType().equals(Timestamp.class)) {
      checkState(!repeatable(), "can't repeat timestamp values");
      fieldSetter().get().set(object, (T) doc.asTimestamp());
      return true;
    }
    return false;
  }
}
