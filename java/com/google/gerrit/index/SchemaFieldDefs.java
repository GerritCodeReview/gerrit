package com.google.gerrit.index;

import com.google.gerrit.common.Nullable;
import java.io.IOException;

public class SchemaFieldDefs {

  public interface SchemaField<T, I> {

    boolean isStored();

    boolean isRepeatable();

    I get(T obj);

    String getName();

    FieldType<?> getType();

    boolean setIfPossible(T object, StoredValue doc);
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
}
