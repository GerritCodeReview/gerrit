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
import {Auth} from './gr-auth/gr-auth_impl';
import {GrRestApiServiceImpl} from './gr-rest-api/gr-rest-api-impl';
import {ChangeModel, changeModelToken} from '../models/change/change-model';
import {FilesModel, filesModelToken} from '../models/change/files-model';
import {ChecksModel, checksModelToken} from '../models/checks/checks-model';
import {GrStorageService, storageServiceToken} from './storage/gr-storage_impl';
import {UserModel, userModelToken} from '../models/user/user-model';
import {
  CommentsModel,
  commentsModelToken,
} from '../models/comments/comments-model';
import {RouterModel, routerModelToken} from './router/router-model';
import {
  ShortcutsService,
  shortcutsServiceToken,
} from './shortcuts/shortcuts-service';
import {assertIsDefined} from '../utils/common-util';
import {ConfigModel, configModelToken} from '../models/config/config-model';
import {BrowserModel, browserModelToken} from '../models/browser/browser-model';
import {
  HighlightService,
  highlightServiceToken,
} from './highlight/highlight-service';
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
import {
  DocumentationViewModel,
  documentationViewModelToken,
} from '../models/views/documentation';
import {GroupViewModel, groupViewModelToken} from '../models/views/group';
import {PluginViewModel, pluginViewModelToken} from '../models/views/plugin';
import {RepoViewModel, repoViewModelToken} from '../models/views/repo';
import {SearchViewModel, searchViewModelToken} from '../models/views/search';
import {navigationToken} from '../elements/core/gr-navigation/gr-navigation';
import {
  PluginLoader,
  pluginLoaderToken,
} from '../elements/shared/gr-js-api-interface/gr-plugin-loader';
import {authServiceToken} from './gr-auth/gr-auth';
import {
  ServiceWorkerInstaller,
  serviceWorkerInstallerToken,
} from './service-worker-installer';
import {
  RelatedChangesModel,
  relatedChangesModelToken,
} from '../models/change/related-changes-model';

/**
 * The AppContext lazy initializator for all services
 */
export function createAppContext(): AppContext & Finalizable {
  const appRegistry: Registry<AppContext> = {
    flagsService: (_ctx: Partial<AppContext>) =>
      new FlagsServiceImplementation(),
    reportingService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.flagsService, 'flagsService)');
      return new GrReporting(ctx.flagsService);
    },
    authService: (_ctx: Partial<AppContext>) => new Auth(),
    restApiService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.authService, 'authService');
      return new GrRestApiServiceImpl(ctx.authService);
    },
  };
  return create<AppContext>(appRegistry);
}

export type Creator<T> = () => T & Finalizable;

// Dependencies are provided as creator functions to ensure that they are
// not created until they are utilized.
// This is mainly useful in tests: E.g. don't create a
// change-model in change-model_test.ts because it creates one in the test
// after setting up stubs.
export function createAppDependencies(
  appContext: AppContext,
  resolver: <T>(token: DependencyToken<T>) => T
): Map<DependencyToken<unknown>, Creator<unknown>> {
  return new Map<DependencyToken<unknown>, Creator<unknown>>([
    [authServiceToken, () => appContext.authService],
    [routerModelToken, () => new RouterModel()],
    [userModelToken, () => new UserModel(appContext.restApiService)],
    [browserModelToken, () => new BrowserModel(resolver(userModelToken))],
    [accountsModelToken, () => new AccountsModel(appContext.restApiService)],
    [adminViewModelToken, () => new AdminViewModel()],
    [agreementViewModelToken, () => new AgreementViewModel()],
    [changeViewModelToken, () => new ChangeViewModel()],
    [dashboardViewModelToken, () => new DashboardViewModel()],
    [documentationViewModelToken, () => new DocumentationViewModel()],
    [groupViewModelToken, () => new GroupViewModel()],
    [pluginViewModelToken, () => new PluginViewModel()],
    [repoViewModelToken, () => new RepoViewModel()],
    [
      searchViewModelToken,
      () =>
        new SearchViewModel(
          appContext.restApiService,
          resolver(userModelToken),
          () => resolver(navigationToken)
        ),
    ],
    [settingsViewModelToken, () => new SettingsViewModel()],
    [
      routerToken,
      () =>
        new GrRouter(
          appContext.reportingService,
          resolver(routerModelToken),
          appContext.restApiService,
          resolver(adminViewModelToken),
          resolver(agreementViewModelToken),
          resolver(changeViewModelToken),
          resolver(dashboardViewModelToken),
          resolver(documentationViewModelToken),
          resolver(groupViewModelToken),
          resolver(pluginViewModelToken),
          resolver(repoViewModelToken),
          resolver(searchViewModelToken),
          resolver(settingsViewModelToken)
        ),
    ],
    [navigationToken, () => resolver(routerToken)],
    [
      changeModelToken,
      () =>
        new ChangeModel(
          resolver(navigationToken),
          resolver(changeViewModelToken),
          appContext.restApiService,
          resolver(userModelToken),
          resolver(pluginLoaderToken),
          appContext.reportingService
        ),
    ],
    [
      commentsModelToken,
      () =>
        new CommentsModel(
          resolver(changeViewModelToken),
          resolver(changeModelToken),
          resolver(accountsModelToken),
          appContext.restApiService,
          appContext.reportingService,
          resolver(navigationToken)
        ),
    ],
    [
      filesModelToken,
      () =>
        new FilesModel(
          resolver(changeModelToken),
          resolver(commentsModelToken),
          appContext.restApiService,
          appContext.reportingService
        ),
    ],
    [
      configModelToken,
      () =>
        new ConfigModel(resolver(changeModelToken), appContext.restApiService),
    ],
    [
      relatedChangesModelToken,
      () =>
        new RelatedChangesModel(
          resolver(changeModelToken),
          resolver(configModelToken),
          appContext.restApiService
        ),
    ],
    [
      pluginLoaderToken,
      () =>
        new PluginLoader(
          appContext.reportingService,
          appContext.restApiService
        ),
    ],
    [
      checksModelToken,
      () =>
        new ChecksModel(
          resolver(changeViewModelToken),
          resolver(changeModelToken),
          appContext.reportingService,
          resolver(pluginLoaderToken).pluginsModel
        ),
    ],
    [
      shortcutsServiceToken,
      () =>
        new ShortcutsService(
          resolver(userModelToken),
          appContext.reportingService
        ),
    ],
    [storageServiceToken, () => new GrStorageService()],
    [
      highlightServiceToken,
      () => new HighlightService(appContext.reportingService),
    ],
    [
      serviceWorkerInstallerToken,
      () =>
        new ServiceWorkerInstaller(
          appContext.flagsService,
          appContext.reportingService,
          resolver(userModelToken)
        ),
    ],
  ]);
}
