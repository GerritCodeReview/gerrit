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
import {FilesModel, filesModelToken} from '../models/change/files-model';
import {ChecksModel, checksModelToken} from '../models/checks/checks-model';
import {GrJsApiInterface} from '../elements/shared/gr-js-api-interface/gr-js-api-interface-element';
import {GrStorageService} from './storage/gr-storage_impl';
import {UserModel} from '../models/user/user-model';
import {
  CommentsModel,
  commentsModelToken,
} from '../models/comments/comments-model';
import {RouterModel} from './router/router-model';
import {
  ShortcutsService,
  shortcutsServiceToken,
} from './shortcuts/shortcuts-service';
import {assertIsDefined} from '../utils/common-util';
import {ConfigModel, configModelToken} from '../models/config/config-model';
import {BrowserModel, browserModelToken} from '../models/browser/browser-model';
import {PluginsModel} from '../models/plugins/plugins-model';
import {HighlightService} from './highlight/highlight-service';
import {AccountsModel} from '../models/accounts-model/accounts-model';
import {
  DashboardViewModel,
  dashboardViewModelToken,
} from '../models/views/dashboard';
import {
  SettingsViewModel,
  settingsViewModelToken,
} from '../models/views/settings';
import {GrRouter, routerToken} from '../elements/core/gr-router/gr-router';

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
      assertIsDefined(ctx.flagsService, 'flagsService');
      return new GrRestApiServiceImpl(ctx.authService, ctx.flagsService);
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
    accountsModel: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new AccountsModel(ctx.restApiService);
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

  const dashboardViewModel = new DashboardViewModel();
  dependencies.set(dashboardViewModelToken, dashboardViewModel);
  const settingsViewModel = new SettingsViewModel();
  dependencies.set(settingsViewModelToken, settingsViewModel);

  const router = new GrRouter(
    appContext.reportingService,
    appContext.routerModel,
    appContext.restApiService,
    dashboardViewModel,
    settingsViewModel
  );
  dependencies.set(routerToken, router);

  const changeModel = new ChangeModel(
    appContext.routerModel,
    appContext.restApiService,
    appContext.userModel
  );
  dependencies.set(changeModelToken, changeModel);

  const accountsModel = new AccountsModel(appContext.restApiService);

  const commentsModel = new CommentsModel(
    appContext.routerModel,
    changeModel,
    accountsModel,
    appContext.restApiService,
    appContext.reportingService
  );
  dependencies.set(commentsModelToken, commentsModel);

  const filesModel = new FilesModel(
    changeModel,
    commentsModel,
    appContext.restApiService
  );
  dependencies.set(filesModelToken, filesModel);

  const configModel = new ConfigModel(changeModel, appContext.restApiService);
  dependencies.set(configModelToken, configModel);

  const checksModel = new ChecksModel(
    appContext.routerModel,
    changeModel,
    appContext.reportingService,
    appContext.pluginsModel
  );

  dependencies.set(checksModelToken, checksModel);

  const shortcutsService = new ShortcutsService(
    appContext.userModel,
    appContext.reportingService
  );
  dependencies.set(shortcutsServiceToken, shortcutsService);

  return dependencies;
}
