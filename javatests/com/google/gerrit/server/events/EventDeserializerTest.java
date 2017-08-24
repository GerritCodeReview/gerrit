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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;

public class EventDeserializerTest {

  @Test
  public void refUpdatedEvent() {
    RefUpdatedEvent refUpdatedEvent = new RefUpdatedEvent();

    RefUpdateAttribute refUpdatedAttribute = new RefUpdateAttribute();
    refUpdatedAttribute.refName = "refs/heads/master";
    refUpdatedEvent.refUpdate = createSupplier(refUpdatedAttribute);

    AccountAttribute accountAttribute = new AccountAttribute();
    accountAttribute.email = "some.user@domain.com";
    refUpdatedEvent.submitter = createSupplier(accountAttribute);

    Gson gsonSerializer =
        new GsonBuilder().registerTypeAdapter(Supplier.class, new SupplierSerializer()).create();
    String serializedEvent = gsonSerializer.toJson(refUpdatedEvent);

    Gson gsonDeserializer =
        new GsonBuilder()
            .registerTypeAdapter(Event.class, new EventDeserializer())
            .registerTypeAdapter(Supplier.class, new SupplierDeserializer())
            .create();

    RefUpdatedEvent e = (RefUpdatedEvent) gsonDeserializer.fromJson(serializedEvent, Event.class);

    assertThat(e).isNotNull();
    assertThat(e.refUpdate).isInstanceOf(Supplier.class);
    assertThat(e.refUpdate.get().refName).isEqualTo(refUpdatedAttribute.refName);
    assertThat(e.submitter).isInstanceOf(Supplier.class);
    assertThat(e.submitter.get().email).isEqualTo(accountAttribute.email);
  }

  private <T> Supplier<T> createSupplier(T value) {
    return Suppliers.memoize(
        new Supplier<T>() {
          @Override
          public T get() {
            return value;
          }
        });
  }
}
