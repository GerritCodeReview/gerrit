// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.extensions.webui;

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.extensions.conditions.BooleanCondition.or;
import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.extensions.registration.PluginName;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.webui.PrivateInternals_UiActionDescription;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.extensions.webui.UiAction.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendCondition;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@Singleton
public class UiActions {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static Predicate<UiAction.Description> enabled() {
    return UiAction.Description::isEnabled;
  }

  private final PermissionBackend permissionBackend;
  private final Timer1<String> uiActionLatency;

  @Inject
  UiActions(PermissionBackend permissionBackend, MetricMaker metricMaker) {
    this.permissionBackend = permissionBackend;
    this.uiActionLatency =
        metricMaker.newTimer(
            "http/server/rest_api/ui_actions/latency",
            new com.google.gerrit.metrics.Description("Latency for RestView#getDescription calls")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            Field.ofString(
                    (metadataBuilder, fieldValue) -> metadataBuilder.restViewName(fieldValue))
                .name("view")
                .build());
  }

  public <R extends RestResource> Iterable<UiAction.Description> from(
      RestCollection<?, R> collection, R resource) {
    return from(collection.views(), resource);
  }

  public <R extends RestResource> Iterable<UiAction.Description> from(
      DynamicMap<RestView<R>> views, R resource) {
    List<UiAction.Description> descs =
        Streams.stream(views)
            .map(e -> describe(e, resource))
            .filter(Objects::nonNull)
            .collect(toList());

    List<PermissionBackendCondition> conds =
        Streams.concat(
                descs.stream().flatMap(u -> Streams.stream(visibleCondition(u))),
                descs.stream().flatMap(u -> Streams.stream(enabledCondition(u))))
            .collect(toList());

    evaluatePermissionBackendConditions(permissionBackend, conds);

    return descs.stream().filter(Description::isVisible).collect(toList());
  }

  @VisibleForTesting
  static void evaluatePermissionBackendConditions(
      PermissionBackend perm, List<PermissionBackendCondition> conds) {
    Map<PermissionBackendCondition, PermissionBackendCondition> dedupedConds =
        new HashMap<>(conds.size());
    for (PermissionBackendCondition cond : conds) {
      dedupedConds.put(cond, cond);
    }
    perm.bulkEvaluateTest(dedupedConds.keySet());
    for (PermissionBackendCondition cond : conds) {
      cond.set(dedupedConds.get(cond).value());
    }
  }

  private static Iterable<PermissionBackendCondition> visibleCondition(Description u) {
    return u.getVisibleCondition().reduce().children(PermissionBackendCondition.class);
  }

  private static Iterable<PermissionBackendCondition> enabledCondition(Description u) {
    return u.getEnabledCondition().reduce().children(PermissionBackendCondition.class);
  }

  @Nullable
  private <R extends RestResource> UiAction.Description describe(
      Extension<RestView<R>> e, R resource) {
    int d = e.getExportName().indexOf('.');
    if (d < 0) {
      return null;
    }

    RestView<R> view;
    try {
      view = e.getProvider().get();
    } catch (RuntimeException err) {
      logger.atSevere().withCause(err).log(
          "error creating view %s.%s", e.getPluginName(), e.getExportName());
      return null;
    }

    if (!(view instanceof UiAction)) {
      return null;
    }

    String name = e.getExportName().substring(d + 1);
    UiAction.Description dsc;
    try (Timer1.Context ignored = uiActionLatency.start(name)) {
      dsc = ((UiAction<R>) view).getDescription(resource);
    }

    if (dsc == null) {
      return null;
    }

    Set<GlobalOrPluginPermission> globalRequired;
    try {
      globalRequired = GlobalPermission.fromAnnotation(e.getPluginName(), view.getClass());
    } catch (PermissionBackendException err) {
      logger.atSevere().withCause(err).log(
          "exception testing view %s.%s", e.getPluginName(), e.getExportName());
      return null;
    }
    if (!globalRequired.isEmpty()) {
      PermissionBackend.WithUser withUser = permissionBackend.currentUser();
      Iterator<GlobalOrPluginPermission> i = globalRequired.iterator();
      BooleanCondition p = withUser.testCond(i.next());
      while (i.hasNext()) {
        p = or(p, withUser.testCond(i.next()));
      }
      dsc.setVisible(and(p, dsc.getVisibleCondition()));
    }

    PrivateInternals_UiActionDescription.setMethod(dsc, e.getExportName().substring(0, d));
    PrivateInternals_UiActionDescription.setId(
        dsc, PluginName.GERRIT.equals(e.getPluginName()) ? name : e.getPluginName() + '~' + name);
    return dsc;
  }
}
