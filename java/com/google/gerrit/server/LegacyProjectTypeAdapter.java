package com.google.gerrit.server;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Type adapter that populates JSON {@code project} from {@code repository} fields for writing JSON
 * objects and vice-versa for reading from JSON objects.
 *
 * <p>This conversion is done to maintain backwards compatibility for callers that still rely on
 * {@code repositories} being referred to as {@code projects}.
 */
public class LegacyProjectTypeAdapter implements TypeAdapterFactory {

  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
    return new TypeAdapter<T>() {
      @Override
      public void write(JsonWriter out, T value) throws IOException {
        if (value == null) {
          delegate.write(out, value);
          return;
        }
        // Manually check if the field exists. This prevents the creation of a stack trace which
        // slow in case it there is no such field. intentionally don't use a stream since streams
        // are slower if the collection's size is small.
        boolean hasRepo = false;
        for (Field f : value.getClass().getFields()) {
          if ("repository".equals(f.getName())) {
            hasRepo = true;
            break;
          }
        }
        if (!hasRepo) {
          delegate.write(out, value);
          return;
        }

        try {
          Field field = value.getClass().getField("repository");
          Object repository = field.get(value);
          if (repository instanceof String) {
            out.name("project").value((String) repository);
          }
        } catch (NoSuchFieldException | IllegalAccessException e) {
          // Drop. Not all API entities have these fields. If the object in quest doesn't have the
          // desired field, we just delegate serialization.
        }

        delegate.write(out, value);
      }

      @Override
      public T read(JsonReader in) throws IOException {
        return delegate.read(in);
      }
    };
  }
}
