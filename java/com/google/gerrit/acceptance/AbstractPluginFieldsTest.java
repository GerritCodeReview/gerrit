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
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.DynamicOptions.DynamicBean;
import com.google.gerrit.server.change.ChangeAttributeFactory;
import com.google.gerrit.server.change.ChangePluginDefinedInfoFactory;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.change.GetChange;
import com.google.gerrit.server.restapi.change.QueryChanges;
import com.google.gerrit.sshd.commands.Query;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

  protected static class PluginDefinedNullAttributeModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangePluginDefinedInfoFactory.class)
          .toInstance((cds, bp, p) -> null);
    }
  }

  protected static class SimpleAttributeModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangeAttributeFactory.class)
          .toInstance((cd, bp, p) -> new MyInfo("change " + cd.getId()));
    }
  }

  protected static class PluginDefinedSimpleAttributeModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangePluginDefinedInfoFactory.class)
          .toInstance(
              (cds, bp, p) ->
                  Collections.singletonMap(
                      cds.get(0).getId(), new MyInfo("change " + cds.get(0).getId())));
    }
  }

  protected static class PluginDefinedBulkAttributeModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangePluginDefinedInfoFactory.class)
          .toInstance(
              (cds, bp, p) -> {
                Map<Change.Id, PluginDefinedInfo> out = new HashMap<>();
                cds.forEach(cd -> out.put(cd.getId(), new MyInfo("change " + cd.getId())));
                return out;
              });
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

  protected static class PluginDefinedOptionAttributeModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangePluginDefinedInfoFactory.class)
          .to(BulkAttributeFactory.class);
      bind(DynamicBean.class).annotatedWith(Exports.named(Query.class)).to(MyOptions.class);
      bind(DynamicBean.class).annotatedWith(Exports.named(QueryChanges.class)).to(MyOptions.class);
      bind(DynamicBean.class).annotatedWith(Exports.named(GetChange.class)).to(MyOptions.class);
    }
  }

  public static class BulkAttributeFactory implements ChangePluginDefinedInfoFactory {
    protected MyOptions opts;

    @Override
    public Map<Change.Id, PluginDefinedInfo> createPluginDefinedInfos(
        List<ChangeData> cds, DynamicOptions.BeanProvider beanProvider, String plugin) {
      if (opts == null) {
        opts = (MyOptions) beanProvider.getDynamicBean(plugin);
      }
      Map out = new HashMap<Change.Id, PluginDefinedInfo>();
      cds.forEach(cd -> out.put(cd.getId(), new MyInfo("opt " + opts.opt)));
      return out;
    }
  }

  protected void getChangeWithNullAttribute(PluginInfoGetter getter) throws Exception {
    getChangeWithNullAttribute(getter, NullAttributeModule.class);
  }

  protected void getChangeWithPluginDefinedNullAttribute(PluginInfoGetter getter) throws Exception {
    getChangeWithNullAttribute(getter, PluginDefinedNullAttributeModule.class);
  }

  protected void getChangeWithNullAttribute(
      PluginInfoGetter getter, Class<? extends Module> moduleClass) throws Exception {
    Change.Id id = createChange().getChange().getId();
    assertThat(getter.call(id)).isNull();

    try (AutoCloseable ignored = installPlugin("my-plugin", moduleClass)) {
      assertThat(getter.call(id)).isNull();
    }

    assertThat(getter.call(id)).isNull();
  }

  protected void getChangeWithSimpleAttribute(PluginInfoGetter getter) throws Exception {
    getChangeWithSimpleAttribute(getter, SimpleAttributeModule.class);
  }

  protected void getChangeWithPluginDefinedSimpleAttribute(PluginInfoGetter getter)
      throws Exception {
    getChangeWithSimpleAttribute(getter, PluginDefinedSimpleAttributeModule.class);
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

  protected void getChangeWithPluginDefinedBulkAttribute(BulkPluginInfoGetter getter)
      throws Exception {
    Change.Id id1 = createChange().getChange().getId();
    Change.Id id2 = createChange().getChange().getId();
    assertThat(getter.call()).hasSize(0);

    try (AutoCloseable ignored =
        installPlugin("my-plugin", PluginDefinedBulkAttributeModule.class)) {
      List<MyInfo> pluginInfos = getter.call();
      assertThat(pluginInfos).contains(new MyInfo("my-plugin", "change " + id1));
      assertThat(pluginInfos).contains(new MyInfo("my-plugin", "change " + id2));
    }

    assertThat(getter.call()).hasSize(0);
  }

  protected void getChangeWithPluginDefinedAttributeOption(
      PluginInfoGetter getterWithoutOptions, PluginInfoGetterWithOptions getterWithOptions)
      throws Exception {
    Change.Id id = createChange().getChange().getId();
    assertThat(getterWithoutOptions.call(id)).isNull();

    try (AutoCloseable ignored =
        installPlugin("my-plugin", PluginDefinedOptionAttributeModule.class)) {
      assertThat(getterWithoutOptions.call(id))
          .containsExactly(new MyInfo("my-plugin", "opt null"));
      assertThat(getterWithOptions.call(id, ImmutableListMultimap.of("my-plugin--opt", "foo")))
          .containsExactly(new MyInfo("my-plugin", "opt foo"));
    }

    assertThat(getterWithoutOptions.call(id)).isNull();
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

  protected static List<MyInfo> pluginInfosFromChangeInfos(List<ChangeInfo> changeInfos) {
    Set<MyInfo> out = new HashSet<>();
    changeInfos.forEach(
        ci -> {
          if (ci.plugins != null) {
            out.addAll(ci.plugins.stream().map(MyInfo.class::cast).collect(toImmutableList()));
          }
        });
    return new ArrayList<MyInfo>(out);
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

  protected static List<MyInfo> getPluginInfosFromChangeInfos(
      Gson gson, List<Map<String, Object>> changeInfos) {
    List<MyInfo> out = new ArrayList<>();
    changeInfos.forEach(
        change -> {
          Object pluginInfo = change.get("plugins");
          if (pluginInfo != null) {
            out.add(decodeRawPluginsList(gson, pluginInfo).get(0));
          }
        });
    return out;
  }

  @FunctionalInterface
  protected interface PluginInfoGetter {
    List<MyInfo> call(Change.Id id) throws Exception;
  }

  @FunctionalInterface
  protected interface BulkPluginInfoGetter {
    List<MyInfo> call() throws Exception;
  }

  @FunctionalInterface
  protected interface PluginInfoGetterWithOptions {
    List<MyInfo> call(Change.Id id, ImmutableListMultimap<String, String> pluginOptions)
        throws Exception;
  }
}
