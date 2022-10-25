/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// Init app context before any other imports
import {create, Registry, Finalizable} from '../services/registry';
import {assertIsDefined} from '../utils/common-util';
import {AppContext} from '../services/app-context';
import {grReportingMock} from '../services/gr-reporting/gr-reporting_mock';
import {grRestApiMock} from './mocks/gr-rest-api_mock';
import {grStorageMock} from '../services/storage/gr-storage_mock';
import {GrAuthMock} from '../services/gr-auth/gr-auth_mock';
import {FlagsServiceImplementation} from '../services/flags/flags_impl';
import {EventEmitter} from '../services/gr-event-interface/gr-event-interface_impl';
import {GrJsApiInterface} from '../elements/shared/gr-js-api-interface/gr-js-api-interface-element';
import {PluginsModel} from '../models/plugins/plugins-model';
import {MockHighlightService} from '../services/highlight/highlight-service-mock';
import {createAppDependencies, Creator} from '../services/app-context-init';
import {navigationToken} from '../elements/core/gr-navigation/gr-navigation';
import {DependencyToken} from '../models/dependency';
import {storageServiceToken} from '../services/storage/gr-storage_impl';
import {highlightServiceToken} from '../services/highlight/highlight-service';
import {PluginLoader} from '../elements/shared/gr-js-api-interface/gr-plugin-loader';

export function createTestAppContext(): AppContext & Finalizable {
  const appRegistry: Registry<AppContext> = {
    flagsService: (_ctx: Partial<AppContext>) =>
      new FlagsServiceImplementation(),
    reportingService: (_ctx: Partial<AppContext>) => grReportingMock,
    eventEmitter: (_ctx: Partial<AppContext>) => new EventEmitter(),
    authService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.eventEmitter, 'eventEmitter');
      return new GrAuthMock(ctx.eventEmitter);
    },
    restApiService: (_ctx: Partial<AppContext>) => grRestApiMock,
    jsApiService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.reportingService, 'reportingService');
      return new GrJsApiInterface(
        () => ctx.pluginLoader!.awaitPluginsLoaded(),
        ctx.reportingService
      );
    },
    pluginsModel: (_ctx: Partial<AppContext>) => new PluginsModel(),
    pluginLoader: (ctx: Partial<AppContext>) => {
      const reportingService = ctx.reportingService;
      const jsApiService = ctx.jsApiService;
      const restApiService = ctx.restApiService;
      const pluginsModel = ctx.pluginsModel;
      assertIsDefined(reportingService, 'reportingService');
      assertIsDefined(jsApiService, 'jsApiService');
      assertIsDefined(restApiService, 'restApiService');
      assertIsDefined(pluginsModel, 'pluginsModel');
      return new PluginLoader(
        reportingService,
        jsApiService,
        restApiService,
        pluginsModel
      );
    },
  };
  return create<AppContext>(appRegistry);
}

export function createTestDependencies(
  appContext: AppContext,
  resolver: <T>(token: DependencyToken<T>) => T
): Map<DependencyToken<unknown>, Creator<unknown>> {
  const dependencies = createAppDependencies(appContext, resolver);
  dependencies.set(storageServiceToken, () => grStorageMock);
  dependencies.set(navigationToken, () => {
    return {
      setUrl: () => {},
      replaceUrl: () => {},
      finalize: () => {},
    };
  });
  dependencies.set(
    highlightServiceToken,
    () => new MockHighlightService(appContext.reportingService)
  );
  return dependencies;
}
