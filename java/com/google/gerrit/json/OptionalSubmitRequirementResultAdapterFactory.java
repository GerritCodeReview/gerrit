package com.google.gerrit.json;

import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.bind.TypeAdapters;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.util.Optional;

public class OptionalSubmitRequirementResultAdapterFactory implements TypeAdapterFactory {

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
    if (!typeToken.equals(TypeToken.get(new TypeLiteral<Optional<SubmitRequirementResult>>() {
    }.getType())) && !typeToken.equals(TypeToken.get(SubmitRequirementResult.class))) {
      // Not an SubmitRequirementResult.
      return null;
    }
    TypeAdapter<SubmitRequirementResult> submitRequirementResultAdapter = SubmitRequirementResult.typeAdapter(
        gson);

    return (TypeAdapter<T>) new OptionalSubmitRequirementResultTypeAdapter(
        submitRequirementResultAdapter);
  }

  private static class OptionalSubmitRequirementResultTypeAdapter extends
      TypeAdapter<Optional<SubmitRequirementResult>> {

    private final TypeAdapter<SubmitRequirementResult> submitRequirementResultAdapter;

    public OptionalSubmitRequirementResultTypeAdapter(
        TypeAdapter<SubmitRequirementResult> submitRequirementResultAdapter) {
      this.submitRequirementResultAdapter = submitRequirementResultAdapter;
    }


    @Override
    public Optional<SubmitRequirementResult> read(JsonReader in) throws IOException {
      JsonElement parsed = JsonParser.parseReader(in);
      if (parsed == null) {
        return Optional.empty();
      }
      if (parsed.isJsonObject()) {
        // If it's not a JSON object, then the boolean value is available directly in the Json
        // element.
        parsed = parsed.getAsJsonObject().get("value");
      }
      if (parsed == null || parsed.isJsonNull()) {
        return Optional.empty();
      }
      return Optional.of(submitRequirementResultAdapter.fromJsonTree(parsed));
    }


    @Override
    public void write(JsonWriter out, Optional<SubmitRequirementResult> value) throws IOException {
      // Serialize the field using the same format used by the AutoValue's default Gson serializer.
      out.beginObject();
      out.name("value");
      if (value.isPresent()) {
        out.value(submitRequirementResultAdapter.toJson(value.get()));
      } else {
        out.nullValue();
      }
      out.endObject();
    }
  }
}
