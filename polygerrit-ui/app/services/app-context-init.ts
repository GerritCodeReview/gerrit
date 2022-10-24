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
import {UserModel, userModelToken} from '../models/user/user-model';
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
import {AdminViewModel, adminViewModelToken} from '../models/views/admin';
import {
  AgreementViewModel,
  agreementViewModelToken,
} from '../models/views/agreement';
import {ChangeViewModel, changeViewModelToken} from '../models/views/change';
import {DiffViewModel, diffViewModelToken} from '../models/views/diff';
import {
  DocumentationViewModel,
  documentationViewModelToken,
} from '../models/views/documentation';
import {EditViewModel, editViewModelToken} from '../models/views/edit';
import {GroupViewModel, groupViewModelToken} from '../models/views/group';
import {PluginViewModel, pluginViewModelToken} from '../models/views/plugin';
import {RepoViewModel, repoViewModelToken} from '../models/views/repo';
import {SearchViewModel, searchViewModelToken} from '../models/views/search';
import {navigationToken} from '../elements/core/gr-navigation/gr-navigation';

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
  const userModel = new UserModel(appContext.restApiService);
  dependencies.set(userModelToken, userModel);
  const browserModel = new BrowserModel(userModel);
  dependencies.set(browserModelToken, browserModel);

  const adminViewModel = new AdminViewModel();
  dependencies.set(adminViewModelToken, adminViewModel);
  const agreementViewModel = new AgreementViewModel();
  dependencies.set(agreementViewModelToken, agreementViewModel);
  const changeViewModel = new ChangeViewModel();
  dependencies.set(changeViewModelToken, changeViewModel);
  const dashboardViewModel = new DashboardViewModel();
  dependencies.set(dashboardViewModelToken, dashboardViewModel);
  const diffViewModel = new DiffViewModel();
  dependencies.set(diffViewModelToken, diffViewModel);
  const documentationViewModel = new DocumentationViewModel();
  dependencies.set(documentationViewModelToken, documentationViewModel);
  const editViewModel = new EditViewModel();
  dependencies.set(editViewModelToken, editViewModel);
  const groupViewModel = new GroupViewModel();
  dependencies.set(groupViewModelToken, groupViewModel);
  const pluginViewModel = new PluginViewModel();
  dependencies.set(pluginViewModelToken, pluginViewModel);
  const repoViewModel = new RepoViewModel();
  dependencies.set(repoViewModelToken, repoViewModel);
  const searchViewModel = new SearchViewModel();
  dependencies.set(searchViewModelToken, searchViewModel);
  const settingsViewModel = new SettingsViewModel();
  dependencies.set(settingsViewModelToken, settingsViewModel);

  const router = new GrRouter(
    appContext.reportingService,
    appContext.routerModel,
    appContext.restApiService,
    adminViewModel,
    agreementViewModel,
    changeViewModel,
    dashboardViewModel,
    diffViewModel,
    documentationViewModel,
    editViewModel,
    groupViewModel,
    pluginViewModel,
    repoViewModel,
    searchViewModel,
    settingsViewModel
  );
  dependencies.set(routerToken, router);
  dependencies.set(navigationToken, router);

  const changeModel = new ChangeModel(
    appContext.routerModel,
    appContext.restApiService,
    userModel
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
    changeViewModel,
    changeModel,
    appContext.reportingService,
    appContext.pluginsModel
  );

  dependencies.set(checksModelToken, checksModel);

  const shortcutsService = new ShortcutsService(
    userModel,
    appContext.reportingService
  );
  dependencies.set(shortcutsServiceToken, shortcutsService);

  return dependencies;
}
