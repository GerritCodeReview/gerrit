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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.io.CharStreams;
import com.google.common.reflect.TypeToken;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.DynamicOptions.DynamicBean;
import com.google.gerrit.server.change.ChangeAttributeFactory;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gerrit.server.query.change.OutputStreamQuery;
import com.google.gerrit.server.restapi.change.QueryChanges;
import com.google.gerrit.sshd.PluginCommandModule;
import com.google.gerrit.sshd.commands.Query;
import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.servlet.ServletModule;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.Test;
import org.kohsuke.args4j.Option;

@UseSsh
public class PluginFieldsIT extends AbstractDaemonTest {
  static class MyInfo extends PluginDefinedInfo {
    @Nullable String theAttribute;

    MyInfo(@Nullable String theAttribute) {
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

  static class NullAttributeModule extends AbstractModule {
    @Override
    public void configure() {
      bind(ChangeAttributeFactory.class)
          .annotatedWith(Exports.named(ChangeQueryProcessor.class))
          .toInstance((cd, br, p) -> null);
    }
  }

  static class SimpleAttributeModule extends AbstractModule {
    @Override
    public void configure() {
      bind(ChangeAttributeFactory.class)
          .annotatedWith(Exports.named(ChangeQueryProcessor.class))
          .toInstance((cd, br, p) -> new MyInfo("change " + cd.getId()));
    }
  }

  static class MyOptions implements DynamicBean {
    @Option(name = "--opt")
    private String opt;
  }

  static class OptionAttributeSysModule extends AbstractModule {
    @Override
    public void configure() {
      bind(ChangeAttributeFactory.class)
          .annotatedWith(Exports.named(ChangeQueryProcessor.class))
          .toInstance(
              (cd, br, p) -> {
                MyOptions opts = (MyOptions) br.getDynamicBean(p);
                return opts != null ? new MyInfo("opt " + opts.opt) : null;
              });
    }
  }

  static class OptionAttributeSshModule extends PluginCommandModule {
    @Override
    protected void configureCommands() {
      bind(DynamicBean.class).annotatedWith(Exports.named(Query.class)).to(MyOptions.class);
    }
  }

  static class OptionAttributeHttpModule extends ServletModule {
    @Override
    protected void configureServlets() {
      bind(DynamicBean.class).annotatedWith(Exports.named(QueryChanges.class)).to(MyOptions.class);
    }
  }

  @Test
  public void queryChangeApiWithNullAttribute() throws Exception {
    queryChangeWithNullAttribute(
        id -> pluginInfoFromSingletonList(gApi.changes().query(id.toString()).get()));
  }

  @Test
  public void queryChangeRestWithNullAttribute() throws Exception {
    queryChangeWithNullAttribute(
        id -> pluginInfoFromSingletonListRest(adminRestSession.get(changeQueryUrl(id))));
  }

  @Test
  public void queryChangeSshWithNullAttribute() throws Exception {
    queryChangeWithNullAttribute(
        id -> pluginInfoFromSingletonListSsh(adminSshSession.exec(changeQueryCmd(id))));
  }

  private void queryChangeWithNullAttribute(PluginInfoGetter getter) throws Exception {
    Change.Id id = createChange().getChange().getId();
    assertThat(getter.call(id)).isNull();

    try (AutoCloseable ignored = installPlugin("my-plugin", NullAttributeModule.class)) {
      assertThat(getter.call(id)).isNull();
    }

    assertThat(getter.call(id)).isNull();
  }

  @Test
  public void queryChangeApiWithSimpleAttribute() throws Exception {
    queryChangeWithSimpleAttribute(
        id -> pluginInfoFromSingletonList(gApi.changes().query(id.toString()).get()));
  }

  @Test
  public void queryChangeRestWithSimpleAttribute() throws Exception {
    queryChangeWithSimpleAttribute(
        id -> pluginInfoFromSingletonListRest(adminRestSession.get(changeQueryUrl(id))));
  }

  @Test
  public void queryChangeSshWithSimpleAttribute() throws Exception {
    queryChangeWithSimpleAttribute(
        id -> pluginInfoFromSingletonListSsh(adminSshSession.exec(changeQueryCmd(id))));
  }

  private void queryChangeWithSimpleAttribute(PluginInfoGetter getter) throws Exception {
    Change.Id id = createChange().getChange().getId();
    assertThat(getter.call(id)).isNull();

    try (AutoCloseable ignored = installPlugin("my-plugin", SimpleAttributeModule.class)) {
      List<MyInfo> infos = getter.call(id);
      assertThat(infos).containsExactly(new MyInfo("my-plugin", "change " + id));
    }

    assertThat(getter.call(id)).isNull();
  }

  @Test
  public void queryChangeSshWithOption() throws Exception {
    queryChangeWithOption(
        id -> pluginInfoFromSingletonListSsh(adminSshSession.exec(changeQueryCmd(id))),
        (id, opts) ->
            pluginInfoFromSingletonListSsh(adminSshSession.exec(changeQueryCmd(id, opts))));
  }

  @Test
  public void queryChangeRestWithOption() throws Exception {
    queryChangeWithOption(
        id -> pluginInfoFromSingletonListRest(adminRestSession.get(changeQueryUrl(id))),
        (id, opts) ->
            pluginInfoFromSingletonListRest(adminRestSession.get(changeQueryUrl(id, opts))));
  }

  // No test for plugin-provided options over the extension API. There are currently two separate
  // DynamicMap<DynamicBean> maps initialized in the SSH and HTTP injectors, and plugins have to
  // define separate SSH/HTTP modules and bind their DynamicBeans in each one. To use the extension
  // API, we would have to move everything into the sys injector.
  // TODO(dborowitz): Determine whether this is possible without breaking existing plugins.

  private void queryChangeWithOption(
      PluginInfoGetter getterWithoutOptions, PluginInfoGetterWithOptions getterWithOptions)
      throws Exception {
    Change.Id id = createChange().getChange().getId();
    assertThat(getterWithoutOptions.call(id)).isNull();

    try (AutoCloseable ignored =
        installPlugin(
            "my-plugin",
            OptionAttributeSysModule.class,
            OptionAttributeHttpModule.class,
            OptionAttributeSshModule.class)) {
      assertThat(getterWithoutOptions.call(id))
          .containsExactly(new MyInfo("my-plugin", "opt null"));
      assertThat(getterWithOptions.call(id, ImmutableListMultimap.of("my-plugin--opt", "foo")))
          .containsExactly(new MyInfo("my-plugin", "opt foo"));
    }

    assertThat(getterWithoutOptions.call(id)).isNull();
  }

  private String changeQueryUrl(Change.Id id) {
    return changeQueryUrl(id, ImmutableListMultimap.of());
  }

  private String changeQueryUrl(Change.Id id, ImmutableListMultimap<String, String> opts) {
    String url = "/changes/?q=" + id;
    String queryString = Joiner.on('&').withKeyValueSeparator('=').join(opts.entries());
    if (!queryString.isEmpty()) {
      url += "&" + queryString;
    }
    return url;
  }

  private String changeQueryCmd(Change.Id id) {
    return changeQueryCmd(id, ImmutableListMultimap.of());
  }

  private String changeQueryCmd(Change.Id id, ImmutableListMultimap<String, String> pluginOptions) {
    return "gerrit query --format json "
        + pluginOptions
            .entries()
            .stream()
            .flatMap(e -> Stream.of("--" + e.getKey(), e.getValue()))
            .collect(joining(" "))
        + " "
        + id;
  }

  private static List<MyInfo> pluginInfoFromSingletonList(List<ChangeInfo> changeInfos) {
    assertThat(changeInfos).hasSize(1);
    List<PluginDefinedInfo> pluginInfo = changeInfos.get(0).plugins;
    if (pluginInfo == null) {
      return null;
    }
    return pluginInfo.stream().map(MyInfo.class::cast).collect(toImmutableList());
  }

  @Nullable
  private static List<MyInfo> pluginInfoFromSingletonListRest(RestResponse res) throws Exception {
    res.assertOK();

    // Don't deserialize to ChangeInfo directly, since that would treat the plugins field as
    // List<PluginDefinedInfo> and ignore the unknown keys found in MyInfo.
    Gson gson = OutputFormat.JSON.newGson();
    List<Map<String, Object>> changeInfos =
        gson.fromJson(res.getReader(), new TypeToken<List<Map<String, Object>>>() {}.getType());
    assertThat(changeInfos).hasSize(1);
    Object plugins = changeInfos.get(0).get("plugins");
    if (plugins == null) {
      return null;
    }
    return gson.fromJson(gson.toJson(plugins), new TypeToken<List<MyInfo>>() {}.getType());
  }

  @Nullable
  private static List<MyInfo> pluginInfoFromSingletonListSsh(String sshOutput) throws Exception {
    Gson gson = OutputStreamQuery.GSON;
    List<Map<String, Object>> changeAttrs = new ArrayList<>();
    for (String line : CharStreams.readLines(new StringReader(sshOutput))) {
      // Don't deserialize to ChangeAttribute directly, since that would treat the plugins field as
      // List<PluginDefinedInfo> and ignore the unknown keys found in MyInfo.
      Map<String, Object> changeAttr =
          gson.fromJson(line, new TypeToken<Map<String, Object>>() {}.getType());
      if (!"stats".equals(changeAttr.get("type"))) {
        changeAttrs.add(changeAttr);
      }
    }

    assertThat(changeAttrs).hasSize(1);

    Object plugins = changeAttrs.get(0).get("plugins");
    if (plugins == null) {
      return null;
    }
    return gson.fromJson(gson.toJson(plugins), new TypeToken<List<MyInfo>>() {}.getType());
  }

  @FunctionalInterface
  private interface PluginInfoGetter {
    List<MyInfo> call(Change.Id id) throws Exception;
  }

  @FunctionalInterface
  private interface PluginInfoGetterWithOptions {
    List<MyInfo> call(Change.Id id, ImmutableListMultimap<String, String> pluginOptions)
        throws Exception;
  }
}
