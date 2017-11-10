package com.google.gerrit.index.query;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.index.FieldDef;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/** FieldsBundle is an abstraction that allows retrieval of raw values from different sources. */
public class FieldsBundle {

  // Map String => {Integer, Long, Timestamp, String, byte[]}
  private ImmutableMap<String, ImmutableList<Object>> fields;

  public FieldsBundle(Map<String, List<Object>> fields) {
    ImmutableMap.Builder<String, ImmutableList<Object>> mapBuilder = ImmutableMap.builder();
    for (Entry<String, List<Object>> entry : fields.entrySet()) {
      mapBuilder.put(entry.getKey(), ImmutableList.copyOf(entry.getValue()));
    }
    this.fields = mapBuilder.build();
  }

  /**
   * Get a field's value based on the field definition.
   *
   * @param fieldDef the definition of the field of which the value should be retrieved. The field
   *     must be stored and contained in the result set as specified by {@link
   *     com.google.gerrit.index.QueryOptions}.
   * @param <T> Data type of the returned object based on the field definition
   * @return Either a single element or an Iterable based on the field definition.
   */
  @SuppressWarnings("unchecked")
  public <T> T getValue(FieldDef<?, T> fieldDef) {
    checkArgument(fieldDef.isStored(), "Field must be stored");
    checkArgument(
        fields.containsKey(fieldDef.getName()),
        String.format("Field %s is not in result set", fieldDef.getName()));

    Iterable<Object> result = fields.get(fieldDef.getName());
    if (fieldDef.isRepeatable()) {
      return (T) result;
    } else {
      return (T) Iterables.getOnlyElement(result);
    }
  }
}
