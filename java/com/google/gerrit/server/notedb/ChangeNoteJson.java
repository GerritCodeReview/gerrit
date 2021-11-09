// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.EntitiesAdapterFactory;
import com.google.gerrit.json.EnumTypeAdapterFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;

@Singleton
public class ChangeNoteJson {
  private final Gson gson = newGson();

  static Gson newGson() {
    return new GsonBuilder()
        .registerTypeAdapter(Timestamp.class, new CommentTimestampAdapter().nullSafe())
        .registerTypeAdapterFactory(new EnumTypeAdapterFactory())
        .registerTypeAdapterFactory(EntitiesAdapterFactory.create())
        .registerTypeAdapter(
            new TypeLiteral<ImmutableList<String>>() {}.getType(),
            new ImmutableListAdapter().nullSafe())
        .registerTypeAdapter(ObjectId.class, new ObjectIdAdapter())
        .setPrettyPrinting()
        .create();
  }

  public Gson getGson() {
    return gson;
  }

  /** Json serializer for the {@link ObjectId} class. */
  static class ObjectIdAdapter extends TypeAdapter<ObjectId> {
    private static final List<String> legacyFields = Arrays.asList("w1", "w2", "w3", "w4", "w5");

    @Override
    public void write(JsonWriter out, ObjectId value) throws IOException {
      out.value(value.name());
    }

    @Override
    public ObjectId read(JsonReader in) throws IOException {
      JsonElement parsed = new JsonParser().parse(in);
      if (parsed.isJsonObject() && isJGitFormat(parsed)) {
        // Some object IDs may have been serialized using the JGit format using the five integers
        // w1, w2, w3, w4, w5. Detect this case so that we can deserialize properly.
        int[] raw =
            legacyFields.stream()
                .mapToInt(field -> parsed.getAsJsonObject().get(field).getAsInt())
                .toArray();
        return ObjectId.fromRaw(raw);
      }
      return ObjectId.fromString(parsed.getAsString());
    }

    /** Return true if the json element contains the JGit serialized format of the Object ID. */
    private boolean isJGitFormat(JsonElement elem) {
      JsonObject asObj = elem.getAsJsonObject();
      return legacyFields.stream().allMatch(field -> asObj.has(field));
    }
  }

  static class ImmutableListAdapter extends TypeAdapter<ImmutableList<String>> {

    @Override
    public void write(JsonWriter out, ImmutableList<String> value) throws IOException {
      out.beginArray();
      for (String v : value) {
        out.value(v);
      }
      out.endArray();
    }

    @Override
    public ImmutableList<String> read(JsonReader in) throws IOException {
      ImmutableList.Builder<String> builder = ImmutableList.builder();
      in.beginArray();
      while (in.hasNext()) {
        builder.add(in.nextString());
      }
      in.endArray();
      return builder.build();
    }
  }
}
