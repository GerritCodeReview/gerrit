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
import {assertIsDefined} from '../utils/common-util';
import {AppContext} from '../services/app-context';
import {grReportingMock} from '../services/gr-reporting/gr-reporting_mock';
import {grRestApiMock} from './mocks/gr-rest-api_mock';
import {grStorageMock} from '../services/storage/gr-storage_mock';
import {GrAuthMock} from '../services/gr-auth/gr-auth_mock';
import {FlagsServiceImplementation} from '../services/flags/flags_impl';
import {EventEmitter} from '../services/gr-event-interface/gr-event-interface_impl';
import {ChangeModel} from '../services/change/change-model';
import {ChecksModel} from '../services/checks/checks-model';
import {GrJsApiInterface} from '../elements/shared/gr-js-api-interface/gr-js-api-interface-element';
import {UserModel} from '../services/user/user-model';
import {CommentsModel} from '../services/comments/comments-model';
import {RouterModel} from '../services/router/router-model';
import {ShortcutsService} from '../services/shortcuts/shortcuts-service';
import {BrowserModel} from '../services/browser/browser-model';
import {ConfigModel} from '../services/config/config-model';

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
    changeModel: (ctx: Partial<AppContext>) => {
      const routerModel = ctx.routerModel;
      const restApiService = ctx.restApiService;
      assertIsDefined(routerModel, 'routerModel');
      assertIsDefined(restApiService, 'restApiService');
      return new ChangeModel(routerModel, restApiService);
    },
    commentsModel: (ctx: Partial<AppContext>) => {
      const routerModel = ctx.routerModel;
      const changeModel = ctx.changeModel;
      const restApiService = ctx.restApiService;
      const reportingService = ctx.reportingService;
      assertIsDefined(routerModel, 'routerModel');
      assertIsDefined(changeModel, 'changeModel');
      assertIsDefined(restApiService, 'restApiService');
      assertIsDefined(reportingService, 'reportingService');
      return new CommentsModel(
        routerModel,
        changeModel,
        restApiService,
        reportingService
      );
    },
    checksModel: (ctx: Partial<AppContext>) => {
      const routerModel = ctx.routerModel;
      const changeModel = ctx.changeModel;
      const reportingService = ctx.reportingService;
      assertIsDefined(routerModel, 'routerModel');
      assertIsDefined(changeModel, 'changeModel');
      assertIsDefined(reportingService, 'reportingService');
      return new ChecksModel(routerModel, changeModel, reportingService);
    },
    jsApiService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.reportingService, 'reportingService');
      return new GrJsApiInterface(ctx.reportingService!);
    },
    storageService: (_ctx: Partial<AppContext>) => grStorageMock,
    configModel: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.changeModel, 'changeModel');
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new ConfigModel(ctx.changeModel!, ctx.restApiService!);
    },
    userModel: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new UserModel(ctx.restApiService!);
    },
    shortcutsService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.userModel, 'userModel');
      assertIsDefined(ctx.reportingService, 'reportingService');
      return new ShortcutsService(ctx.userModel!, ctx.reportingService!);
    },
    browserModel: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.userModel, 'userModel');
      return new BrowserModel(ctx.userModel!);
    },
  };
  return create<AppContext>(appRegistry);
}
