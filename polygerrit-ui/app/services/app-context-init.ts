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
import {appContext, AppContext} from './app-context';
import {create, Registry} from './registry';
import {FlagsServiceImplementation} from './flags/flags_impl';
import {GrReporting} from './gr-reporting/gr-reporting_impl';
import {EventEmitter} from './gr-event-interface/gr-event-interface_impl';
import {Auth} from './gr-auth/gr-auth_impl';
import {GrRestApiInterface} from '../elements/shared/gr-rest-api-interface/gr-rest-api-interface';
import {ChangeService} from './change/change-service';
import {ChecksService} from './checks/checks-service';
import {GrJsApiInterface} from '../elements/shared/gr-js-api-interface/gr-js-api-interface-element';
import {GrStorageService} from './storage/gr-storage_impl';
import {ConfigService} from './config/config-service';
import {UserService} from './user/user-service';
import {CommentsService} from './comments/comments-service';
import {ShortcutsService} from './shortcuts/shortcuts-service';
import {BrowserService} from './browser/browser-service';

/**
 * The AppContext lazy initializator for all services
 */
export function createAppContext(): Partial<AppContext> {
  const appRegistry: Registry<AppContext> = {
    flagsService: (_ctx: AppContext) => new FlagsServiceImplementation(),
    reportingService: (ctx: AppContext) => new GrReporting(ctx.flagsService),
    eventEmitter: (_ctx: AppContext) => new EventEmitter(),
    authService: (ctx: AppContext) => new Auth(ctx.eventEmitter),
    restApiService: (ctx: AppContext) =>
      new GrRestApiInterface(ctx.authService, ctx.flagsService),
    changeService: (ctx: AppContext) => new ChangeService(ctx.restApiService),
    commentsService: (ctx: AppContext) => new CommentsService(ctx.restApiService),
    checksService: (ctx: AppContext) => new ChecksService(ctx.reportingService),
    jsApiService: (ctx: AppContext) => new GrJsApiInterface(ctx.reportingService),
    storageService: (_ctx: AppContext) => new GrStorageService(),
    configService: (ctx: AppContext) => new ConfigService(ctx.restApiService),
    userService: (ctx: AppContext) => new UserService(ctx.restApiService),
    shortcutsService: (ctx: AppContext) => new ShortcutsService(ctx.reportingService),
    browserService: (_ctx: AppContext) => new BrowserService(),
  };
  return create<AppContext>(appRegistry);
}


