// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

class EventSerializer implements JsonSerializer<Event> {
  @Override
  public JsonElement serialize(Event src, Type typeOfSrc, JsonSerializationContext context) {
    String type = src.getType();

    Class<?> cls = EventTypes.getClass(type);
    if (cls == null) {
      throw new JsonParseException("Unknown event type: " + type);
    }

    return context.serialize(src, cls);
  }
}
