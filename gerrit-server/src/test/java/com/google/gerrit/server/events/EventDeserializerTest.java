package com.google.gerrit.server.events;

//Copyright (C) 2016 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.Test;

public class EventDeserializerTest {

  @Test
  public void test() {
    RefUpdatedEvent refUpdatedEvent = new RefUpdatedEvent();
    final RefUpdateAttribute refUpdatedAttribute = new RefUpdateAttribute();
    refUpdatedAttribute.refName = "refs/heads/master";
    refUpdatedEvent.refUpdate =
        Suppliers.memoize(new Supplier<RefUpdateAttribute>() {
          @Override
          public RefUpdateAttribute get() {
            return refUpdatedAttribute;
          }
        });
    Gson gsonSerializer = new GsonBuilder()
        .registerTypeAdapter(Supplier.class, new SupplierSerializer())
        .create();
    String serializedEvent = gsonSerializer.toJson(refUpdatedEvent);

    Gson gsonDeserializer = new GsonBuilder()
        .registerTypeAdapter(Event.class, new EventDeserializer()).create();
    gsonDeserializer.fromJson(serializedEvent, Event.class);
  }

}
