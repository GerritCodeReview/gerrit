// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.events;

import com.google.gerrit.entities.Project;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class ProjectNameKeyAdapter
    implements JsonSerializer<Project.NameKey>, JsonDeserializer<Project.NameKey> {
  @Override
  public JsonElement serialize(
      Project.NameKey project, Type typeOfSrc, JsonSerializationContext context) {
    return new JsonPrimitive(project.get());
  }

  @Override
  public Project.NameKey deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
      throw new JsonParseException("Key is not a string: " + json);
    }
    return Project.nameKey(json.getAsString());
  }
}
