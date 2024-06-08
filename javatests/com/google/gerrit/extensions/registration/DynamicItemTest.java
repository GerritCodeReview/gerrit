// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.extensions.registration;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import java.util.function.Consumer;
import org.junit.Test;

public class DynamicItemTest {
  private static final String PLUGIN_NAME = "plugin-name";

  private static final String UNEXPECTED_PLUGIN_NAME = "unexpected-plugin";
  private static final String DYNAMIC_ITEM_1 = "item-1";
  private static final String DYNAMIC_ITEM_2 = "item-2";
  private static final TypeLiteral<String> STRING_TYPE_LITERAL = new TypeLiteral<>() {};
  private static final TypeLiteral<FinalItemApi> FINAL_ITEM_API_TYPE_LITERAL =
      new TypeLiteral<>() {};
  private static final TypeLiteral<FinalItemApiForPlugin>
      FINAL_TARGET_PLUGIN_ITEM_API_TYPE_LITERAL = new TypeLiteral<>() {};

  @DynamicItem.Final
  private interface FinalItemApi {}

  private static class FinalItemImpl implements FinalItemApi {
    private static final FinalItemApi INSTANCE = new FinalItemImpl();
  }

  @DynamicItem.Final(implementedByPlugin = PLUGIN_NAME)
  private interface FinalItemApiForPlugin {}

  private static class FinalItemImplByPlugin implements FinalItemApiForPlugin {
    private static final FinalItemApiForPlugin INSTANCE = new FinalItemImplByPlugin();
  }

  @Test
  public void shouldAssignDynamicItemTwice() {
    ImmutableMap<TypeLiteral<?>, DynamicItem<?>> bindings =
        ImmutableMap.of(STRING_TYPE_LITERAL, DynamicItem.itemOf(String.class, null));

    ImmutableList<RegistrationHandle> gerritRegistrations =
        PrivateInternals_DynamicTypes.attachItems(
            newInjector(
                (binder) -> {
                  DynamicItem.itemOf(binder, String.class);
                  DynamicItem.bind(binder, String.class).toInstance(DYNAMIC_ITEM_1);
                }),
            PluginName.GERRIT,
            bindings);
    assertThat(gerritRegistrations).hasSize(1);
    assertDynamicItem(bindings.get(STRING_TYPE_LITERAL), DYNAMIC_ITEM_1, PluginName.GERRIT);

    ImmutableList<RegistrationHandle> pluginRegistrations =
        PrivateInternals_DynamicTypes.attachItems(
            newInjector(
                (binder) -> DynamicItem.bind(binder, String.class).toInstance(DYNAMIC_ITEM_2)),
            PLUGIN_NAME,
            bindings);
    assertThat(pluginRegistrations).hasSize(1);
    assertDynamicItem(bindings.get(STRING_TYPE_LITERAL), DYNAMIC_ITEM_2, PLUGIN_NAME);
  }

  @Test
  public void shouldFailToAssignFinalDynamicItemTwice() {
    ImmutableMap<TypeLiteral<?>, DynamicItem<?>> bindings =
        ImmutableMap.of(FINAL_ITEM_API_TYPE_LITERAL, DynamicItem.itemOf(FinalItemApi.class, null));

    ImmutableList<RegistrationHandle> baseInjectorRegistrations =
        PrivateInternals_DynamicTypes.attachItems(
            newInjector(
                (binder) -> {
                  DynamicItem.itemOf(binder, FinalItemApi.class);
                  DynamicItem.bind(binder, FinalItemApi.class).toInstance(FinalItemImpl.INSTANCE);
                }),
            PluginName.GERRIT,
            bindings);
    assertThat(baseInjectorRegistrations).hasSize(1);

    ProvisionException ignored =
        assertThrows(
            ProvisionException.class,
            () -> {
              ImmutableList<RegistrationHandle> unused =
                  PrivateInternals_DynamicTypes.attachItems(
                      newInjector(
                          (binder) ->
                              DynamicItem.bind(binder, FinalItemApi.class)
                                  .toInstance(FinalItemImpl.INSTANCE)),
                      PluginName.GERRIT,
                      bindings);
            });
  }

  @Test
  public void shouldFailToAssignFinalDynamicItemToDifferentPlugin() {
    ImmutableMap<TypeLiteral<?>, DynamicItem<?>> bindings =
        ImmutableMap.of(
            FINAL_TARGET_PLUGIN_ITEM_API_TYPE_LITERAL,
            DynamicItem.itemOf(FinalItemApi.class, null));

    assertThrows(
        ProvisionException.class,
        () -> {
          ImmutableList<RegistrationHandle> unused =
              PrivateInternals_DynamicTypes.attachItems(
                  newInjector(
                      (binder) ->
                          DynamicItem.bind(binder, FinalItemApiForPlugin.class)
                              .toInstance(FinalItemImplByPlugin.INSTANCE)),
                  UNEXPECTED_PLUGIN_NAME,
                  bindings);
        });
  }

  @Test
  public void shouldAssignFinalDynamicItemToExpectedPlugin() {
    ImmutableMap<TypeLiteral<?>, DynamicItem<?>> bindings =
        ImmutableMap.of(
            FINAL_TARGET_PLUGIN_ITEM_API_TYPE_LITERAL,
            DynamicItem.itemOf(FinalItemApi.class, null));

    ImmutableList<RegistrationHandle> pluginRegistrations =
        PrivateInternals_DynamicTypes.attachItems(
            newInjector(
                (binder) ->
                    DynamicItem.bind(binder, FinalItemApiForPlugin.class)
                        .toInstance(FinalItemImplByPlugin.INSTANCE)),
            PLUGIN_NAME,
            bindings);
    assertThat(pluginRegistrations).hasSize(1);
    assertDynamicItem(
        bindings.get(FINAL_TARGET_PLUGIN_ITEM_API_TYPE_LITERAL),
        FinalItemImplByPlugin.INSTANCE,
        PLUGIN_NAME);
  }

  private static Injector newInjector(Consumer<Binder> binding) {
    return Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            binding.accept(binder());
          }
        });
  }

  private static <T> void assertDynamicItem(
      @Nullable DynamicItem<?> item, T itemVal, String pluginName) {
    assertThat(item).isNotNull();
    assertThat(item.get()).isEqualTo(itemVal);
    assertThat(item.getPluginName()).isEqualTo(pluginName);
  }
}
