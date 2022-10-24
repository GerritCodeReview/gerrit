/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// Init app context before any other imports
import {create, Registry, Finalizable} from '../services/registry';
import {DependencyToken} from '../models/dependency';
import {assertIsDefined} from '../utils/common-util';
import {AppContext} from '../services/app-context';
import {grReportingMock} from '../services/gr-reporting/gr-reporting_mock';
import {grRestApiMock} from './mocks/gr-rest-api_mock';
import {grStorageMock} from '../services/storage/gr-storage_mock';
import {GrAuthMock} from '../services/gr-auth/gr-auth_mock';
import {FlagsServiceImplementation} from '../services/flags/flags_impl';
import {EventEmitter} from '../services/gr-event-interface/gr-event-interface_impl';
import {ChangeModel, changeModelToken} from '../models/change/change-model';
import {FilesModel, filesModelToken} from '../models/change/files-model';
import {ChecksModel, checksModelToken} from '../models/checks/checks-model';
import {GrJsApiInterface} from '../elements/shared/gr-js-api-interface/gr-js-api-interface-element';
import {UserModel} from '../models/user/user-model';
import {
  CommentsModel,
  commentsModelToken,
} from '../models/comments/comments-model';
import {RouterModel} from '../services/router/router-model';
import {
  ShortcutsService,
  shortcutsServiceToken,
} from '../services/shortcuts/shortcuts-service';
import {ConfigModel, configModelToken} from '../models/config/config-model';
import {BrowserModel, browserModelToken} from '../models/browser/browser-model';
import {PluginsModel} from '../models/plugins/plugins-model';
import {MockHighlightService} from '../services/highlight/highlight-service-mock';
import {
  AccountsModel,
  accountsModelToken,
} from '../models/accounts-model/accounts-model';
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

export function createTestAppContext(): AppContext & Finalizable {
  const appRegistry: Registry<AppContext> = {
    routerModel: (_ctx: Partial<AppContext>) => new RouterModel(),
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
      return new GrJsApiInterface(ctx.reportingService);
    },
    storageService: (_ctx: Partial<AppContext>) => grStorageMock,
    userModel: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new UserModel(ctx.restApiService);
    },
    accountsModel: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new AccountsModel(ctx.restApiService);
    },
    shortcutsService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.userModel, 'userModel');
      assertIsDefined(ctx.flagsService, 'flagsService');
      assertIsDefined(ctx.reportingService, 'reportingService');
      return new ShortcutsService(ctx.userModel, ctx.reportingService);
    },
    pluginsModel: (_ctx: Partial<AppContext>) => new PluginsModel(),
    highlightService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.reportingService, 'reportingService');
      return new MockHighlightService(ctx.reportingService);
    },
  };
  return create<AppContext>(appRegistry);
}

export type Creator<T> = () => T & Finalizable;

// Test dependencies are provides as creator functions to ensure that they are
// not created if a test doesn't depend on them. E.g. don't create a
// change-model in change-model_test.ts because it creates one in the test
// after setting up stubs.
export function createTestDependencies(
  appContext: AppContext,
  resolver: <T>(token: DependencyToken<T>) => T
): Map<DependencyToken<unknown>, Creator<unknown>> {
  const dependencies = new Map<DependencyToken<unknown>, Creator<unknown>>();
  const browserModel = () => new BrowserModel(appContext.userModel);
  dependencies.set(browserModelToken, browserModel);

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
    new SearchViewModel(appContext.restApiService, appContext.userModel, () =>
      resolver(navigationToken)
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
  dependencies.set(navigationToken, () => {
    return {
      setUrl: () => {},
      replaceUrl: () => {},
      finalize: () => {},
    };
  });

  const changeModelCreator = () =>
    new ChangeModel(
      appContext.routerModel,
      appContext.restApiService,
      appContext.userModel,
      resolver(changeViewModelToken)
    );
  dependencies.set(changeModelToken, changeModelCreator);

  const accountsModelCreator = () =>
    new AccountsModel(appContext.restApiService);
  dependencies.set(accountsModelToken, accountsModelCreator);

  const commentsModelCreator = () =>
    new CommentsModel(
      appContext.routerModel,
      resolver(changeModelToken),
      resolver(accountsModelToken),
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
    new ShortcutsService(appContext.userModel, appContext.reportingService);
  dependencies.set(shortcutsServiceToken, shortcutServiceCreator);

  return dependencies;
}
