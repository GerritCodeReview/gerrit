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
import {AppContext} from './app-context';
import {create, Finalizable, Registry} from './registry';
import {FlagsServiceImplementation} from './flags/flags_impl';
import {GrReporting} from './gr-reporting/gr-reporting_impl';
import {EventEmitter} from './gr-event-interface/gr-event-interface_impl';
import {Auth} from './gr-auth/gr-auth_impl';
import {GrRestApiServiceImpl} from '../elements/shared/gr-rest-api-interface/gr-rest-api-impl';
import {ChangeModel} from './change/change-model';
import {ChecksModel} from './checks/checks-model';
import {GrJsApiInterface} from '../elements/shared/gr-js-api-interface/gr-js-api-interface-element';
import {GrStorageService} from './storage/gr-storage_impl';
import {UserModel} from './user/user-model';
import {CommentsModel} from './comments/comments-model';
import {RouterModel} from './router/router-model';
import {ShortcutsService} from './shortcuts/shortcuts-service';
import {assertIsDefined} from '../utils/common-util';
import {ConfigModel} from './config/config-model';

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
      return new GrReporting(ctx.flagsService!);
    },
    eventEmitter: (_ctx: Partial<AppContext>) => new EventEmitter(),
    authService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.eventEmitter, 'eventEmitter');
      return new Auth(ctx.eventEmitter!);
    },
    restApiService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.authService, 'authService');
      assertIsDefined(ctx.flagsService, 'flagsService)');
      return new GrRestApiServiceImpl(ctx.authService!, ctx.flagsService!);
    },
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
      const reportingService = ctx.reportingService;
      assertIsDefined(reportingService, 'reportingService');
      return new GrJsApiInterface(reportingService!);
    },
    storageService: (_ctx: Partial<AppContext>) => new GrStorageService(),
    configModel: (ctx: Partial<AppContext>) => {
      const changeModel = ctx.changeModel;
      const restApiService = ctx.restApiService;
      assertIsDefined(changeModel, 'changeModel');
      assertIsDefined(restApiService, 'restApiService');
      return new ConfigModel(changeModel, restApiService);
    },
    userModel: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new UserModel(ctx.restApiService!);
    },
    shortcutsService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.userModel, 'userModel');
      assertIsDefined(ctx.reportingService, 'reportingService');
      return new ShortcutsService(ctx.userModel, ctx.reportingService!);
    },
  };
  return create<AppContext>(appRegistry);
}
