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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.DynamicOptions.DynamicBean;
import com.google.gerrit.server.change.ChangePluginDefinedInfoFactory;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.change.GetChange;
import com.google.gerrit.server.restapi.change.QueryChanges;
import com.google.gerrit.sshd.commands.Query;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.kohsuke.args4j.Option;

public class AbstractPluginFieldsTest extends AbstractDaemonTest {
  @Inject private ChangeOperations changeOperations;

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

  protected static class PluginDefinedSimpleAttributeModule extends AbstractModule {
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

  protected static class PluginDefinedBulkExceptionModule extends AbstractModule {
    @Override
    protected void configure() {
      DynamicSet.bind(binder(), ChangePluginDefinedInfoFactory.class)
          .toInstance(
              (cds, bp, p) -> {
                throw new RuntimeException("Sample Exception");
              });
    }
  }

  protected static class PluginDefinedChangesByCommitBulkAttributeModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangePluginDefinedInfoFactory.class)
          .toInstance(
              (cds, bp, p) -> {
                Map<Change.Id, PluginDefinedInfo> out = new HashMap<>();
                cds.forEach(
                    cd ->
                        out.put(
                            cd.getId(),
                            !cd.commitMessage().contains("no-info")
                                ? new MyInfo("change " + cd.getId())
                                : null));
                return out;
              });
    }
  }

  protected static class PluginDefinedSingleCallBulkAttributeModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangePluginDefinedInfoFactory.class)
          .to(SingleCallBulkFactoryAttribute.class);
    }
  }

  protected static class SingleCallBulkFactoryAttribute implements ChangePluginDefinedInfoFactory {
    public static int timesCreateCalled = 0;

    @Override
    public Map<Change.Id, PluginDefinedInfo> createPluginDefinedInfos(
        Collection<ChangeData> cds, DynamicOptions.BeanProvider beanProvider, String plugin) {
      timesCreateCalled++;
      Map<Change.Id, PluginDefinedInfo> out = new HashMap<>();
      cds.forEach(cd -> out.put(cd.getId(), new MyInfo("change " + cd.getId())));
      return out;
    }
  }

  private static class MyOptions implements DynamicBean {
    @Option(name = "--opt")
    private String opt;
  }

  public static class BulkAttributeFactoryWithOption implements ChangePluginDefinedInfoFactory {
    protected MyOptions opts;

    @Override
    public Map<Change.Id, PluginDefinedInfo> createPluginDefinedInfos(
        Collection<ChangeData> cds, DynamicOptions.BeanProvider beanProvider, String plugin) {
      if (opts == null) {
        opts = (MyOptions) beanProvider.getDynamicBean(plugin);
      }
      Map<Change.Id, PluginDefinedInfo> out = new HashMap<>();
      cds.forEach(cd -> out.put(cd.getId(), new MyInfo("opt " + opts.opt)));
      return out;
    }
  }

  protected static class PluginDefinedOptionAttributeModule extends AbstractModule {
    @Override
    public void configure() {
      DynamicSet.bind(binder(), ChangePluginDefinedInfoFactory.class)
          .to(BulkAttributeFactoryWithOption.class);
      bind(DynamicBean.class).annotatedWith(Exports.named(Query.class)).to(MyOptions.class);
      bind(DynamicBean.class).annotatedWith(Exports.named(QueryChanges.class)).to(MyOptions.class);
      bind(DynamicBean.class).annotatedWith(Exports.named(GetChange.class)).to(MyOptions.class);
    }
  }

  protected void getSingleChangeWithPluginDefinedBulkAttribute(BulkPluginInfoGetterWithId getter)
      throws Exception {
    Change.Id id = createChange().getChange().getId();

    Map<Change.Id, List<PluginDefinedInfo>> pluginInfos = getter.call(id);
    assertThat(pluginInfos.get(id)).isNull();

    try (AutoCloseable ignored =
        installPlugin("my-plugin", PluginDefinedSimpleAttributeModule.class)) {
      pluginInfos = getter.call(id);
      assertThat(pluginInfos.get(id)).containsExactly(new MyInfo("my-plugin", "change " + id));
    }

    pluginInfos = getter.call(id);
    assertThat(pluginInfos.get(id)).isNull();
  }

  protected void getMultipleChangesWithPluginDefinedBulkAttribute(BulkPluginInfoGetter getter)
      throws Exception {
    Change.Id id1 = createChange().getChange().getId();
    Change.Id id2 = createChange().getChange().getId();

    Map<Change.Id, List<PluginDefinedInfo>> pluginInfos = getter.call();
    assertThat(pluginInfos.get(id1)).isNull();
    assertThat(pluginInfos.get(id2)).isNull();

    try (AutoCloseable ignored =
        installPlugin("my-plugin", PluginDefinedSimpleAttributeModule.class)) {
      pluginInfos = getter.call();
      assertThat(pluginInfos.get(id1)).containsExactly(new MyInfo("my-plugin", "change " + id1));
      assertThat(pluginInfos.get(id2)).containsExactly(new MyInfo("my-plugin", "change " + id2));
    }

    pluginInfos = getter.call();
    assertThat(pluginInfos.get(id1)).isNull();
    assertThat(pluginInfos.get(id2)).isNull();
  }

  protected void getChangesByCommitMessageWithPluginDefinedBulkAttribute(
      BulkPluginInfoGetter getter) throws Exception {
    Change.Id changeWithNoInfo = changeOperations.newChange().commitMessage("no-info").createV1();
    Change.Id changeWithInfo = changeOperations.newChange().commitMessage("info").createV1();

    Map<Change.Id, List<PluginDefinedInfo>> pluginInfos = getter.call();
    assertThat(pluginInfos.get(changeWithNoInfo)).isNull();
    assertThat(pluginInfos.get(changeWithInfo)).isNull();

    try (AutoCloseable ignored =
        installPlugin("my-plugin", PluginDefinedChangesByCommitBulkAttributeModule.class)) {
      pluginInfos = getter.call();
      assertThat(pluginInfos.get(changeWithNoInfo)).isNull();
      assertThat(pluginInfos.get(changeWithInfo))
          .containsExactly(new MyInfo("my-plugin", "change " + changeWithInfo));
    }

    pluginInfos = getter.call();
    assertThat(pluginInfos.get(changeWithNoInfo)).isNull();
    assertThat(pluginInfos.get(changeWithInfo)).isNull();
  }

  protected void getMultipleChangesWithPluginDefinedBulkAttributeInSingleCall(
      BulkPluginInfoGetter getter) throws Exception {
    Change.Id id1 = createChange().getChange().getId();
    Change.Id id2 = createChange().getChange().getId();
    int timesCalled = SingleCallBulkFactoryAttribute.timesCreateCalled;

    Map<Change.Id, List<PluginDefinedInfo>> pluginInfos = getter.call();
    assertThat(pluginInfos.get(id1)).isNull();
    assertThat(pluginInfos.get(id2)).isNull();

    try (AutoCloseable ignored =
        installPlugin("my-plugin", PluginDefinedSingleCallBulkAttributeModule.class)) {
      pluginInfos = getter.call();
      assertThat(pluginInfos.get(id1)).containsExactly(new MyInfo("my-plugin", "change " + id1));
      assertThat(pluginInfos.get(id2)).containsExactly(new MyInfo("my-plugin", "change " + id2));
      assertThat(SingleCallBulkFactoryAttribute.timesCreateCalled).isEqualTo(timesCalled + 1);
    }

    pluginInfos = getter.call();
    assertThat(pluginInfos.get(id1)).isNull();
    assertThat(pluginInfos.get(id2)).isNull();
  }

  protected void getChangeWithPluginDefinedBulkAttributeOption(
      BulkPluginInfoGetterWithId getterWithoutOptions,
      BulkPluginInfoGetterWithIdAndOptions getterWithOptions)
      throws Exception {
    Change.Id id = createChange().getChange().getId();
    assertThat(getterWithoutOptions.call(id).get(id)).isNull();

    try (AutoCloseable ignored =
        installPlugin("my-plugin", PluginDefinedOptionAttributeModule.class)) {
      assertThat(getterWithoutOptions.call(id).get(id))
          .containsExactly(new MyInfo("my-plugin", "opt null"));
      assertThat(
              getterWithOptions.call(id, ImmutableListMultimap.of("my-plugin--opt", "foo")).get(id))
          .containsExactly(new MyInfo("my-plugin", "opt foo"));
    }

    assertThat(getterWithoutOptions.call(id).get(id)).isNull();
  }

  protected void getChangeWithPluginDefinedBulkAttributeWithException(
      BulkPluginInfoGetterWithId getter) throws Exception {
    Change.Id id = createChange().getChange().getId();
    assertThat(getter.call(id).get(id)).isNull();

    try (AutoCloseable ignored =
        installPlugin("my-plugin", PluginDefinedBulkExceptionModule.class)) {
      List<PluginDefinedInfo> outputInfos = getter.call(id).get(id);
      assertThat(outputInfos).hasSize(1);
      assertThat(outputInfos.get(0).name).isEqualTo("my-plugin");
      assertThat(outputInfos.get(0).message).isEqualTo("Something went wrong in plugin: my-plugin");
    }

    assertThat(getter.call(id).get(id)).isNull();
  }

  protected static ImmutableList<PluginDefinedInfo> pluginInfoFromSingletonList(
      List<ChangeInfo> changeInfos) {
    assertThat(changeInfos).hasSize(1);
    return pluginInfoFromChangeInfo(changeInfos.get(0));
  }

  @Nullable
  protected static ImmutableList<PluginDefinedInfo> pluginInfoFromChangeInfo(
      ChangeInfo changeInfo) {
    List<PluginDefinedInfo> pluginInfo = changeInfo.plugins;
    if (pluginInfo == null) {
      return null;
    }
    return pluginInfo.stream().map(PluginDefinedInfo.class::cast).collect(toImmutableList());
  }

  protected static Map<Change.Id, List<PluginDefinedInfo>> pluginInfosFromChangeInfos(
      List<ChangeInfo> changeInfos) {
    Map<Change.Id, List<PluginDefinedInfo>> out = new HashMap<>();
    changeInfos.forEach(ci -> out.put(Change.id(ci._number), pluginInfoFromChangeInfo(ci)));
    return out;
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
  @Nullable
  protected static List<PluginDefinedInfo> decodeRawPluginsList(
      Gson gson, @Nullable Object plugins) {
    if (plugins == null) {
      return null;
    }
    checkArgument(plugins instanceof List, "not a list: %s", plugins);
    return gson.fromJson(gson.toJson(plugins), new TypeToken<List<MyInfo>>() {}.getType());
  }

  protected static Map<Change.Id, List<PluginDefinedInfo>> getPluginInfosFromChangeInfos(
      Gson gson, List<Map<String, Object>> changeInfos) {
    Map<Change.Id, List<PluginDefinedInfo>> out = new HashMap<>();
    changeInfos.forEach(
        change -> {
          Double changeId =
              (Double)
                  (change.get("number") != null ? change.get("number") : change.get("_number"));
          out.put(
              Change.id(changeId.intValue()), decodeRawPluginsList(gson, change.get("plugins")));
        });
    return out;
  }

  @FunctionalInterface
  protected interface BulkPluginInfoGetter {
    Map<Change.Id, List<PluginDefinedInfo>> call() throws Exception;
  }

  @FunctionalInterface
  protected interface BulkPluginInfoGetterWithId {
    Map<Change.Id, List<PluginDefinedInfo>> call(Change.Id id) throws Exception;
  }

  @FunctionalInterface
  protected interface BulkPluginInfoGetterWithIdAndOptions {
    Map<Change.Id, List<PluginDefinedInfo>> call(
        Change.Id id, ImmutableListMultimap<String, String> pluginOptions) throws Exception;
  }
}
