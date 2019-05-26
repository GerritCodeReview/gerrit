// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.DynamicOptions.DynamicBean;
import com.google.gerrit.server.change.ChangeAttributeFactory;
import com.google.gerrit.server.restapi.change.GetChange;
import com.google.gerrit.server.restapi.change.QueryChanges;
import com.google.gerrit.sshd.commands.Query;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import java.util.List;
import java.util.Objects;
import org.kohsuke.args4j.Option;

public class AbstractPluginFieldsTest extends AbstractDaemonTest {
  protected static class MyInfo extends PluginDefinedInfo {
    @Nullable String theAttribute;

    public MyInfo(@Nullable String theAttribute) {
      this.theAttribute = theAttribute;
    }

    MyInfo(String name, @Nullable String theAttribute) {
      this.name = requireNonNull(name);
      this.theAttribute = theAttribute;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof MyInfo)) {
        return false;
      }
      MyInfo i = (MyInfo) o;
      return Objects.equals(name, i.name) && Objects.equals(theAttribute, i.theAttribute);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, theAttribute);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("name", name)
          .add("theAttribute", theAttribute)
          .toString();
    }
  }

  protected static class NullAttributeModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangeAttributeFactory.class).toInstance((cd, bp, p) -> null);
    }
  }

  protected static class SimpleAttributeModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangeAttributeFactory.class)
          .toInstance((cd, bp, p) -> new MyInfo("change " + cd.getId()));
    }
  }

  private static class MyOptions implements DynamicBean {
    @Option(name = "--opt")
    private String opt;
  }

  protected static class OptionAttributeModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangeAttributeFactory.class)
          .toInstance(
              (cd, bp, p) -> {
                MyOptions opts = (MyOptions) bp.getDynamicBean(p);
                return opts != null ? new MyInfo("opt " + opts.opt) : null;
              });
      bind(DynamicBean.class).annotatedWith(Exports.named(Query.class)).to(MyOptions.class);
      bind(DynamicBean.class).annotatedWith(Exports.named(QueryChanges.class)).to(MyOptions.class);
      bind(DynamicBean.class).annotatedWith(Exports.named(GetChange.class)).to(MyOptions.class);
    }
  }

  protected void getChangeWithNullAttribute(PluginInfoGetter getter) throws Exception {
    Change.Id id = createChange().getChange().getId();
    assertThat(getter.call(id)).isNull();

    try (AutoCloseable ignored = installPlugin("my-plugin", NullAttributeModule.class)) {
      assertThat(getter.call(id)).isNull();
    }

    assertThat(getter.call(id)).isNull();
  }

  protected void getChangeWithSimpleAttribute(PluginInfoGetter getter) throws Exception {
    getChangeWithSimpleAttribute(getter, SimpleAttributeModule.class);
  }

  protected void getChangeWithSimpleAttribute(
      PluginInfoGetter getter, Class<? extends Module> moduleClass) throws Exception {
    Change.Id id = createChange().getChange().getId();
    assertThat(getter.call(id)).isNull();

    try (AutoCloseable ignored = installPlugin("my-plugin", moduleClass)) {
      assertThat(getter.call(id)).containsExactly(new MyInfo("my-plugin", "change " + id));
    }

    assertThat(getter.call(id)).isNull();
  }

  protected void getChangeWithOption(
      PluginInfoGetter getterWithoutOptions, PluginInfoGetterWithOptions getterWithOptions)
      throws Exception {
    Change.Id id = createChange().getChange().getId();
    assertThat(getterWithoutOptions.call(id)).isNull();

    try (AutoCloseable ignored = installPlugin("my-plugin", OptionAttributeModule.class)) {
      assertThat(getterWithoutOptions.call(id))
          .containsExactly(new MyInfo("my-plugin", "opt null"));
      assertThat(getterWithOptions.call(id, ImmutableListMultimap.of("my-plugin--opt", "foo")))
          .containsExactly(new MyInfo("my-plugin", "opt foo"));
    }

    assertThat(getterWithoutOptions.call(id)).isNull();
  }

  protected static List<MyInfo> pluginInfoFromSingletonList(List<ChangeInfo> changeInfos) {
    assertThat(changeInfos).hasSize(1);
    return pluginInfoFromChangeInfo(changeInfos.get(0));
  }

  protected static List<MyInfo> pluginInfoFromChangeInfo(ChangeInfo changeInfo) {
    List<PluginDefinedInfo> pluginInfo = changeInfo.plugins;
    if (pluginInfo == null) {
      return null;
    }
    return pluginInfo.stream().map(MyInfo.class::cast).collect(toImmutableList());
  }

  /**
   * Decode {@code MyInfo}s from a raw list of maps returned from Gson.
   *
   * <p>This method is used instead of decoding {@code ChangeInfo} or {@code ChangAttribute}, since
   * Gson would decode the {@code plugins} field as a {@code List<PluginDefinedInfo>}, which would
   * return the base type and silently ignore any fields that are defined only in the subclass.
   * Instead, decode the enclosing {@code ChangeInfo} or {@code ChangeAttribute} as a raw {@code
   * Map<String, Object>}, and pass the {@code "plugins"} value to this method.
   *
   * @param gson Gson converter.
   * @param plugins list of {@code MyInfo} objects, each as a raw map returned from Gson.
   * @return decoded list of {@code MyInfo}s.
   */
  protected static List<MyInfo> decodeRawPluginsList(Gson gson, @Nullable Object plugins) {
    if (plugins == null) {
      return null;
    }
    checkArgument(plugins instanceof List, "not a list: %s", plugins);
    return gson.fromJson(gson.toJson(plugins), new TypeToken<List<MyInfo>>() {}.getType());
  }

  @FunctionalInterface
  protected interface PluginInfoGetter {
    List<MyInfo> call(Change.Id id) throws Exception;
  }

  @FunctionalInterface
  protected interface PluginInfoGetterWithOptions {
    List<MyInfo> call(Change.Id id, ImmutableListMultimap<String, String> pluginOptions)
        throws Exception;
  }
}
