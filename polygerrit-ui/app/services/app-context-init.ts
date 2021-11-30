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
import {ChangeService} from './change/change-service';
import {ChecksModel} from './checks/checks-model';
import {GrJsApiInterface} from '../elements/shared/gr-js-api-interface/gr-js-api-interface-element';
import {GrStorageService} from './storage/gr-storage_impl';
import {UserModel} from './user/user-model';
import {CommentsService} from './comments/comments-service';
import {ShortcutsService} from './shortcuts/shortcuts-service';
import {BrowserModel} from './browser/browser-model';
import {assertIsDefined} from '../utils/common-util';
import {ConfigModel} from './config/config-model';

/**
 * The AppContext lazy initializator for all services
 */
export function createAppContext(): AppContext & Finalizable {
  const appRegistry: Registry<AppContext> = {
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
    changeService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new ChangeService(ctx.restApiService!);
    },
    commentsService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new CommentsService(ctx.restApiService!);
    },
    checksModel: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.reportingService, 'reportingService');
      return new ChecksModel(ctx.reportingService!);
    },
    jsApiService: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.reportingService, 'reportingService');
      return new GrJsApiInterface(ctx.reportingService!);
    },
    storageService: (_ctx: Partial<AppContext>) => new GrStorageService(),
    configModel: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.restApiService, 'restApiService');
      return new ConfigModel(ctx.restApiService!);
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
    browserModel: (ctx: Partial<AppContext>) => {
      assertIsDefined(ctx.userModel, 'userModel');
      return new BrowserModel(ctx.userModel!);
    },
  };
  return create<AppContext>(appRegistry);
}
