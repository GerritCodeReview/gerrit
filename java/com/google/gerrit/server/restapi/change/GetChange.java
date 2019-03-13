// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.DynamicOptions.DynamicBean;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.kohsuke.args4j.Option;

public class GetChange implements RestReadView<ChangeResource>, DynamicOptions.BeanReceiver {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Register a ChangeAttributeFactory in a config Module like this:
   *
   * <p>bind(ChangeAttributeFactory.class) .annotatedWith(Exports.named("export-name"))
   * .to(YourClass.class);
   */
  public interface ChangeAttributeFactory {
    PluginDefinedInfo create(ChangeData cd, GetChange getChange, String plugin);
  }

  private final ChangeJson.Factory json;
  private final DynamicMap<ChangeAttributeFactory> attrFactories;
  private final EnumSet<ListChangesOption> options = EnumSet.noneOf(ListChangesOption.class);
  private final Map<String, DynamicOptions.DynamicBean> dynamicBeans = new HashMap<>();

  @Option(name = "-o", usage = "Output options")
  void addOption(ListChangesOption o) {
    options.add(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) {
    options.addAll(ListChangesOption.fromBits(Integer.parseInt(hex, 16)));
  }

  @Inject
  GetChange(ChangeJson.Factory json, DynamicMap<ChangeAttributeFactory> attrFactories) {
    this.json = json;
    this.attrFactories = attrFactories;
  }

  @Override
  public void setDynamicBean(String plugin, DynamicBean dynamicBean) {
    dynamicBeans.put(plugin, dynamicBean);
  }

  public DynamicBean getDynamicBean(String plugin) {
    return dynamicBeans.get(plugin);
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc) throws OrmException {
    return Response.withMustRevalidate(newChangeJson().format(rsrc));
  }

  Response<ChangeInfo> apply(RevisionResource rsrc) throws OrmException {
    return Response.withMustRevalidate(newChangeJson().format(rsrc));
  }

  private ChangeJson newChangeJson() {
    return json.create(options, this::buildPluginInfo);
  }

  private ImmutableList<PluginDefinedInfo> buildPluginInfo(ChangeData cd) {
    ImmutableList<PluginDefinedInfo> result =
        Streams.stream(attrFactories)
            .map(
                e -> {
                  String plugin = e.getPluginName();
                  PluginDefinedInfo pdi = null;
                  try {
                    pdi = e.get().create(cd, this, plugin);
                  } catch (RuntimeException ex) {
                    logger.atWarning().withCause(ex).log(
                        "error populating attribute on change %s from plugin %s",
                        cd.getId(), e.getPluginName());
                  }
                  if (pdi != null) {
                    pdi.name = plugin;
                  }
                  return pdi;
                })
            .filter(Objects::nonNull)
            .collect(toImmutableList());
    return !result.isEmpty() ? result : null;
  }
}
