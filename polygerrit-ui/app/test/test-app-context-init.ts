/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import {ChecksModel, checksModelToken} from '../models/checks/checks-model';
import {GrJsApiInterface} from '../elements/shared/gr-js-api-interface/gr-js-api-interface-element';
import {UserModel} from '../models/user/user-model';
import {
  CommentsModel,
  commentsModelToken,
} from '../models/comments/comments-model';
import {RouterModel} from '../services/router/router-model';
import {ShortcutsService} from '../services/shortcuts/shortcuts-service';
import {ConfigModel, configModelToken} from '../models/config/config-model';
import {BrowserModel, browserModelToken} from '../models/browser/browser-model';
import {PluginsModel} from '../models/plugins/plugins-model';
import {MockHighlightService} from '../services/highlight/highlight-service-mock';
import {ViewModel, viewModelToken} from '../models/view/view-model';

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
    shortcutsService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.userModel, 'userModel');
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
  const dependencies = new Map();
  const browserModel = () => new BrowserModel(appContext.userModel);
  dependencies.set(browserModelToken, browserModel);

  const changeModelCreator = () =>
    new ChangeModel(
      appContext.routerModel,
      appContext.restApiService,
      appContext.userModel
    );
  dependencies.set(changeModelToken, changeModelCreator);

  const commentsModelCreator = () =>
    new CommentsModel(
      appContext.routerModel,
      resolver(changeModelToken),
      appContext.restApiService,
      appContext.reportingService
    );
  dependencies.set(commentsModelToken, commentsModelCreator);

  const configModelCreator = () =>
    new ConfigModel(resolver(changeModelToken), appContext.restApiService);
  dependencies.set(configModelToken, configModelCreator);

  const checksModelCreator = () =>
    new ChecksModel(
      appContext.routerModel,
      resolver(changeModelToken),
      appContext.reportingService,
      appContext.pluginsModel
    );

  dependencies.set(checksModelToken, checksModelCreator);

  const viewModelCreator = () => new ViewModel();
  dependencies.set(viewModelToken, viewModelCreator);

  return dependencies;
}
