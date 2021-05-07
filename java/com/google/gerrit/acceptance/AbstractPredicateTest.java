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

package com.google.gerrit.acceptance;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.change.ChangePluginDefinedInfoFactory;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.restapi.change.QueryChanges;
import com.google.gerrit.sshd.commands.Query;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kohsuke.args4j.Option;

public abstract class AbstractPredicateTest extends AbstractDaemonTest {
  public static final String PLUGIN_NAME = "my-plugin";
  public static final Gson GSON = OutputFormat.JSON.newGson();

  protected static class PluginModule extends AbstractModule {
    @Override
    public void configure() {
      bind(DynamicOptions.DynamicBean.class)
          .annotatedWith(Exports.named(Query.class))
          .to(MyQueryOptions.class);
      bind(DynamicOptions.DynamicBean.class)
          .annotatedWith(Exports.named(QueryChanges.class))
          .to(MyQueryOptions.class);
      bind(ChangePluginDefinedInfoFactory.class)
          .annotatedWith(Exports.named("sample"))
          .to(AttributeFactory.class);
    }
  }

  public static class MyQueryOptions implements DynamicOptions.DynamicBean {
    @Option(name = "--sample")
    public boolean sample;
  }

  protected static class AttributeFactory implements ChangePluginDefinedInfoFactory {
    private final Provider<ChangeQueryBuilder> queryBuilderProvider;

    @Inject
    AttributeFactory(Provider<ChangeQueryBuilder> queryBuilderProvider) {
      this.queryBuilderProvider = queryBuilderProvider;
    }

    @Override
    public Map<Change.Id, PluginDefinedInfo> createPluginDefinedInfos(
        Collection<ChangeData> cds, DynamicOptions.BeanProvider beanProvider, String plugin) {
      MyQueryOptions options = (MyQueryOptions) beanProvider.getDynamicBean(plugin);
      Map<Change.Id, PluginDefinedInfo> res = new HashMap<>();
      if (options.sample) {
        try {
          Predicate<ChangeData> predicate = queryBuilderProvider.get().parse("label:Code-Review+2");
          for (ChangeData cd : cds) {
            PluginDefinedInfo myInfo = new PluginDefinedInfo();
            if (predicate.isMatchable() && predicate.asMatchable().match(cd)) {
              myInfo.message = "matched";
            } else {
              myInfo.message = "not matched";
            }
            res.put(cd.getId(), myInfo);
          }
        } catch (QueryParseException e) {
          // ignored
        }
      }
      return res;
    }
  }

  protected static List<PluginDefinedInfo> decodeRawPluginsList(@Nullable Object plugins) {
    if (plugins == null) {
      return Collections.emptyList();
    }
    checkArgument(plugins instanceof List, "not a list: %s", plugins);
    return GSON.fromJson(
        GSON.toJson(plugins), new TypeToken<List<PluginDefinedInfo>>() {}.getType());
  }
}
