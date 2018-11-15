// Copyright (C) 2009 The Android Open Source Project
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

package org.eclipse.jgit.diff;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class EditDeserializer implements JsonDeserializer<Edit>, JsonSerializer<Edit> {
  @Override
  public Edit deserialize(final JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (json.isJsonNull()) {
      return null;
    }
    if (!json.isJsonArray()) {
      throw new JsonParseException("Expected array for Edit type");
    }

    final JsonArray o = (JsonArray) json;
    final int cnt = o.size();
    if (cnt < 4 || cnt % 4 != 0) {
      throw new JsonParseException("Expected array of 4 for Edit type");
    }

    if (4 == cnt) {
      return new Edit(get(o, 0), get(o, 1), get(o, 2), get(o, 3));
    }

    List<Edit> l = new ArrayList<>((cnt / 4) - 1);
    for (int i = 4; i < cnt; ) {
      int as = get(o, i++);
      int ae = get(o, i++);
      int bs = get(o, i++);
      int be = get(o, i++);
      l.add(new Edit(as, ae, bs, be));
    }
    return new ReplaceEdit(get(o, 0), get(o, 1), get(o, 2), get(o, 3), l);
  }

  private static int get(JsonArray a, int idx) throws JsonParseException {
    final JsonElement v = a.get(idx);
    if (!v.isJsonPrimitive()) {
      throw new JsonParseException("Expected array of 4 for Edit type");
    }
    final JsonPrimitive p = (JsonPrimitive) v;
    if (!p.isNumber()) {
      throw new JsonParseException("Expected array of 4 for Edit type");
    }
    return p.getAsInt();
  }

  @Override
  public JsonElement serialize(final Edit src, Type typeOfSrc, JsonSerializationContext context) {
    if (src == null) {
      return JsonNull.INSTANCE;
    }
    final JsonArray a = new JsonArray();
    add(a, src);
    if (src instanceof ReplaceEdit) {
      for (Edit e : ((ReplaceEdit) src).getInternalEdits()) {
        add(a, e);
      }
    }
    return a;
  }

  private void add(JsonArray a, Edit src) {
    a.add(new JsonPrimitive(src.getBeginA()));
    a.add(new JsonPrimitive(src.getEndA()));
    a.add(new JsonPrimitive(src.getBeginB()));
    a.add(new JsonPrimitive(src.getEndB()));
  }
}
