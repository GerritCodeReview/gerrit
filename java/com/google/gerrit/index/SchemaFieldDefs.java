package com.google.gerrit.index;

import com.google.gerrit.common.Nullable;
import java.io.IOException;

/** Interfaces that define properties of fields in {@link Schema}. */
public class SchemaFieldDefs {

  /**
   * Definition of a field stored in the secondary index.
   *
   * @param <I> input type from which documents are created and search results are returned.
   * @param <T> type that should be extracted from the input object when converting to an index
   *     document.
   */
  public interface SchemaField<T, I> {

    /** Returns whether the field should be stored in the index. */
    boolean isStored();

    /** Returns whether the field is repeatable. */
    boolean isRepeatable();

    /**
     * Get the field contents from the input object.
     *
     * @param input input object.
     * @return the field value(s) to index.
     */
    I get(T input);

    /** Returns the name of the field. */
    String getName();

    /**
     * Returns type of the field; for repeatable fields, the inner type, not the iterable type.
     * TODO(mariasavtchuk): remove after migrating to the new field formats
     */
    FieldType<?> getType();

    /**
     * Set the field contents back to an object. Used to reconstruct fields from indexed values.
     * No-op if the field can't be reconstructed.
     *
     * @param object input object.
     * @param doc indexed document
     * @return {@code true} if the field was set, {@code false} otherwise
     */
    boolean setIfPossible(T object, StoredValue doc);
  }

  /**
   * Getter to extract value that should be stored in index from the input object.
   *
   * @param <I> type from which documents are created and search results are returned.
   * @param <T> type that should be extracted from the input object to an index field.
   */
  @FunctionalInterface
  public interface Getter<I, T> {
    @Nullable
    T get(I input) throws IOException;
  }

  /**
   * Setter to reconstruct fields from indexed values back to an object.
   *
   * @param <I> type from which documents are created and search results are returned.
   * @param <T> type that should be extracted from the input object when converting toto an index
   *     field.
   */
  @FunctionalInterface
  public interface Setter<I, T> {
    void set(I object, T value);
  }
}
