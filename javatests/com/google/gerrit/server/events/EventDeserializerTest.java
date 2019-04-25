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

import java.sql.Timestamp;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;


public class EventDeserializerTest extends GerritBaseTests {

  Gson gsonSerializer =
      new GsonBuilder().registerTypeAdapter(Supplier.class, new SupplierSerializer()).create();

  Gson gsonDeserializer =
      new GsonBuilder()
          .registerTypeAdapter(Event.class, new EventDeserializer())
          .registerTypeAdapter(Supplier.class, new SupplierDeserializer())
          .create();
  
  @Test
  public void refUpdatedEvent() {
    RefUpdatedEvent refUpdatedEvent = new RefUpdatedEvent();

    RefUpdateAttribute refUpdatedAttribute = new RefUpdateAttribute();
    refUpdatedAttribute.refName = "refs/heads/master";
    refUpdatedEvent.refUpdate = createSupplier(refUpdatedAttribute);

    AccountAttribute accountAttribute = new AccountAttribute();
    accountAttribute.email = "some.user@domain.com";
    refUpdatedEvent.submitter = createSupplier(accountAttribute);

    String serializedEvent = gsonSerializer.toJson(refUpdatedEvent);
    RefUpdatedEvent e = (RefUpdatedEvent) gsonDeserializer.fromJson(serializedEvent, Event.class);

    assertThat(e).isNotNull();
    assertThat(e.refUpdate).isInstanceOf(Supplier.class);
    assertThat(e.refUpdate.get().refName).isEqualTo(refUpdatedAttribute.refName);
    assertThat(e.submitter).isInstanceOf(Supplier.class);
    assertThat(e.submitter.get().email).isEqualTo(accountAttribute.email);
  }
  
  @Test
  public void patchSetCreatedEvent() {
    Change change = new Change(
        Change.key("Iabcdef"),
        Change.id(1000),
        Account.id(1000),
        Branch.nameKey(Project.nameKey("myproject"), "mybranch"),
        new Timestamp(System.currentTimeMillis()));
    PatchSetCreatedEvent orig = new PatchSetCreatedEvent(change);
    
    String serialized = gsonSerializer.toJson(orig);
    PatchSetCreatedEvent e = (PatchSetCreatedEvent) gsonDeserializer.fromJson(serialized, Event.class);
    
    assertThat(e).isNotNull();
    assertThat(e.change).isInstanceOf(Supplier.class);
    assertThat(e.change.get()).isEqualTo(change);
  }

  private <T> Supplier<T> createSupplier(T value) {
    return Suppliers.memoize(() -> value);
  }
}
