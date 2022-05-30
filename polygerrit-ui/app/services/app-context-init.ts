/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {AppContext} from './app-context';
import {create, Finalizable, Registry} from './registry';
import {DependencyToken} from '../models/dependency';
import {FlagsServiceImplementation} from './flags/flags_impl';
import {GrReporting} from './gr-reporting/gr-reporting_impl';
import {EventEmitter} from './gr-event-interface/gr-event-interface_impl';
import {Auth} from './gr-auth/gr-auth_impl';
import {GrRestApiServiceImpl} from './gr-rest-api/gr-rest-api-impl';
import {ChangeModel, changeModelToken} from '../models/change/change-model';
import {ChecksModel, checksModelToken} from '../models/checks/checks-model';
import {GrJsApiInterface} from '../elements/shared/gr-js-api-interface/gr-js-api-interface-element';
import {GrStorageService} from './storage/gr-storage_impl';
import {UserModel} from '../models/user/user-model';
import {
  CommentsModel,
  commentsModelToken,
} from '../models/comments/comments-model';
import {RouterModel} from './router/router-model';
import {ShortcutsService} from './shortcuts/shortcuts-service';
import {assertIsDefined} from '../utils/common-util';
import {ConfigModel, configModelToken} from '../models/config/config-model';
import {BrowserModel, browserModelToken} from '../models/browser/browser-model';
import {PluginsModel} from '../models/plugins/plugins-model';
import {HighlightService} from './highlight/highlight-service';

/**
 * The AppContext lazy initializator for all services
 */
export function createAppContext(): AppContext & Finalizable {
  const appRegistry: Registry<AppContext> = {
    routerModel: (_ctx: Partial<AppContext>) => new RouterModel(),
    flagsService: (_ctx: Partial<AppContext>) =>
      new FlagsServiceImplementation(),
    reportingService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.flagsService, 'flagsService)');
      return new GrReporting(ctx.flagsService);
    },
    eventEmitter: (_ctx: Partial<AppContext>) => new EventEmitter(),
    authService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.eventEmitter, 'eventEmitter');
      return new Auth(ctx.eventEmitter);
    },
    restApiService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.authService, 'authService');
      return new GrRestApiServiceImpl(ctx.authService);
    },
    jsApiService: (ctx: Partial<AppContext>) => {
      const reportingService = ctx.reportingService;
      assertIsDefined(reportingService, 'reportingService');
      return new GrJsApiInterface(reportingService);
    },
    storageService: (_ctx: Partial<AppContext>) => new GrStorageService(),
    userModel: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new UserModel(ctx.restApiService);
    },
    pluginsModel: (_ctx: Partial<AppContext>) => new PluginsModel(),
    highlightService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.reportingService, 'reportingService');
      return new HighlightService(ctx.reportingService);
    },
  };
  return create<AppContext>(appRegistry);
}

export function createAppDependencies(
  appContext: AppContext
): Map<DependencyToken<unknown>, Finalizable> {
  const dependencies = new Map<DependencyToken<unknown>, Finalizable>();
  const browserModel = new BrowserModel(appContext.userModel);
  dependencies.set(browserModelToken, browserModel);

  const changeModel = new ChangeModel(
    appContext.routerModel,
    appContext.restApiService,
    appContext.userModel
  );
  dependencies.set(changeModelToken, changeModel);

  const commentsModel = new CommentsModel(
    appContext.routerModel,
    changeModel,
    appContext.restApiService,
    appContext.reportingService
  );
  dependencies.set(commentsModelToken, commentsModel);

  const configModel = new ConfigModel(changeModel, appContext.restApiService);
  dependencies.set(configModelToken, configModel);

  const checksModel = new ChecksModel(
    appContext.routerModel,
    changeModel,
    appContext.reportingService,
    appContext.pluginsModel
  );

  dependencies.set(checksModelToken, checksModel);

  return dependencies;
}
