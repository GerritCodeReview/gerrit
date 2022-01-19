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

public class SubmitRequirementResultAdapterFactory implements TypeAdapterFactory {

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
    TypeToken.get(new TypeLiteral<SubmitRequirementResult>() {
    }.getType());
    if (!typeToken.equals(TypeToken.get(new TypeLiteral<SubmitRequirementResult>() {
    }.getType()))) {
      // Not an SubmitRequirementResult.
      return null;
    }
    TypeAdapter<SubmitRequirementResult> submitRequirementResultAdapter = SubmitRequirementResult.typeAdapter(
        gson);

    return (TypeAdapter<T>) new SubmitRequirementResultTypeAdapter(
        submitRequirementResultAdapter);
  }

  private static class SubmitRequirementResultTypeAdapter extends
      TypeAdapter<SubmitRequirementResult> {

    private final TypeAdapter<SubmitRequirementResult> submitRequirementResultAdapter;

    public SubmitRequirementResultTypeAdapter(
        TypeAdapter<SubmitRequirementResult> submitRequirementResultAdapter) {
      this.submitRequirementResultAdapter = submitRequirementResultAdapter;
    }


    @Override
    public SubmitRequirementResult read(JsonReader in) throws IOException {
      JsonElement parsed = JsonParser.parseReader(in);
      if (parsed == null) {
        return null;
      }
      if (parsed.isJsonObject()) {
        // If it's not a JSON object, then the boolean value is available directly in the Json
        // element.
        parsed = parsed.getAsJsonObject().get("value");
      }
      if (parsed == null || parsed.isJsonNull()) {
        return null;
      }
      return submitRequirementResultAdapter.fromJsonTree(parsed);
    }


    @Override
    public void write(JsonWriter out, SubmitRequirementResult value) throws IOException {
      // Serialize the field using the same format used by the AutoValue's default Gson serializer.
      out.beginObject();
      out.name("value");
      if (value != null) {
        out.value(submitRequirementResultAdapter.toJson(value));
      } else {
        out.nullValue();
      }
      out.endObject();
    }
  }
}
