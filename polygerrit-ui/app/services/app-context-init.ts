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

export type Creator<T> = () => T & Finalizable;

// Test dependencies are provides as creator functions to ensure that they are
// not created if a test doesn't depend on them. E.g. don't create a
// change-model in change-model_test.ts because it creates one in the test
// after setting up stubs.
export function createAppDependencies(
  appContext: AppContext,
  resolver: <T>(token: DependencyToken<T>) => T
): Map<DependencyToken<unknown>, Creator<unknown>> {
  const dependencies = new Map<DependencyToken<unknown>, Creator<unknown>>();
  const userModelCreator = () => new UserModel(appContext.restApiService);
  dependencies.set(userModelToken, userModelCreator);
  const browserModelCreator = () => new BrowserModel(resolver(userModelToken));
  dependencies.set(browserModelToken, browserModelCreator);

  const adminViewModelCreator = () => new AdminViewModel();
  dependencies.set(adminViewModelToken, adminViewModelCreator);
  const agreementViewModelCreator = () => new AgreementViewModel();
  dependencies.set(agreementViewModelToken, agreementViewModelCreator);
  const changeViewModelCreator = () => new ChangeViewModel();
  dependencies.set(changeViewModelToken, changeViewModelCreator);
  const dashboardViewModelCreator = () => new DashboardViewModel();
  dependencies.set(dashboardViewModelToken, dashboardViewModelCreator);
  const diffViewModelCreator = () => new DiffViewModel();
  dependencies.set(diffViewModelToken, diffViewModelCreator);
  const documentationViewModelCreator = () => new DocumentationViewModel();
  dependencies.set(documentationViewModelToken, documentationViewModelCreator);
  const editViewModelCreator = () => new EditViewModel();
  dependencies.set(editViewModelToken, editViewModelCreator);
  const groupViewModelCreator = () => new GroupViewModel();
  dependencies.set(groupViewModelToken, groupViewModelCreator);
  const pluginViewModelCreator = () => new PluginViewModel();
  dependencies.set(pluginViewModelToken, pluginViewModelCreator);
  const repoViewModelCreator = () => new RepoViewModel();
  dependencies.set(repoViewModelToken, repoViewModelCreator);
  const searchViewModelCreator = () =>
    new SearchViewModel(
      appContext.restApiService,
      resolver(userModelToken),
      () => resolver(navigationToken)
    );
  dependencies.set(searchViewModelToken, searchViewModelCreator);
  const settingsViewModelCreator = () => new SettingsViewModel();
  dependencies.set(settingsViewModelToken, settingsViewModelCreator);

  const routerCreator = () =>
    new GrRouter(
      appContext.reportingService,
      appContext.routerModel,
      appContext.restApiService,
      resolver(adminViewModelToken),
      resolver(agreementViewModelToken),
      resolver(changeViewModelToken),
      resolver(dashboardViewModelToken),
      resolver(diffViewModelToken),
      resolver(documentationViewModelToken),
      resolver(editViewModelToken),
      resolver(groupViewModelToken),
      resolver(pluginViewModelToken),
      resolver(repoViewModelToken),
      resolver(searchViewModelToken),
      resolver(settingsViewModelToken)
    );
  dependencies.set(routerToken, routerCreator);
  dependencies.set(navigationToken, () => resolver(routerToken));

  const changeModelCreator = () =>
    new ChangeModel(
      appContext.routerModel,
      appContext.restApiService,
      resolver(userModelToken)
    );
  dependencies.set(changeModelToken, changeModelCreator);

  const commentsModelCreator = () =>
    new CommentsModel(
      appContext.routerModel,
      resolver(changeModelToken),
      appContext.accountsModel,
      appContext.restApiService,
      appContext.reportingService
    );
  dependencies.set(commentsModelToken, commentsModelCreator);

  const filesModelCreator = () =>
    new FilesModel(
      resolver(changeModelToken),
      resolver(commentsModelToken),
      appContext.restApiService
    );
  dependencies.set(filesModelToken, filesModelCreator);

  const configModelCreator = () =>
    new ConfigModel(resolver(changeModelToken), appContext.restApiService);
  dependencies.set(configModelToken, configModelCreator);

  const checksModelCreator = () =>
    new ChecksModel(
      appContext.routerModel,
      resolver(changeViewModelToken),
      resolver(changeModelToken),
      appContext.reportingService,
      appContext.pluginsModel
    );

  dependencies.set(checksModelToken, checksModelCreator);

  const shortcutServiceCreator = () =>
    new ShortcutsService(resolver(userModelToken), appContext.reportingService);
  dependencies.set(shortcutsServiceToken, shortcutServiceCreator);

  return dependencies;
}
