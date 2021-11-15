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

// NOTE: This global table is a stopgap solution until services know how to
// properly clean up after themselves.
type ServiceName = keyof AppContext;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const initializedServices: Map<ServiceName, any> = new Map<ServiceName, any>();

/**
 * The AppContext lazy initializator for all services
 */
export function createAppContext(): AppContext {
  const appRegistry: Registry<AppContext> = {
    flagsService: (_ctx: Partial<AppContext>) =>
     new FlagsServiceImplementation(),
    reportingService: (ctx: Partial<AppContext>) => {
      if (!ctx.flagsService)
        throw new Error('AppContext.flagsService is not registered');
      return new GrReporting(ctx.flagsService);
    },
    eventEmitter: (_ctx: Partial<AppContext>) => new EventEmitter(),
    authService: (ctx: Partial<AppContext>) => {
      if (!ctx.eventEmitter)
        throw new Error('AppContext.eventEmitter is not registered');
      return new Auth(ctx.eventEmitter);
    },
    restApiService: (ctx: Partial<AppContext>) => {
      if (!ctx.authService)
        throw new Error('AppContext.authService is not registered');
      if (!ctx.flagsService)
        throw new Error('AppContext.flagsService is not registered');
      return new GrRestApiInterface(ctx.authService, ctx.flagsService);
    },
    changeService: (ctx: Partial<AppContext>) => {
      if (!ctx.restApiService)
        throw new Error('AppContext.restApiService is not registered');
      return new ChangeService(ctx.restApiService);
    },
    commentsService: (ctx: Partial<AppContext>) => {
      if (!ctx.restApiService)
        throw new Error('AppContext.restApiService is not registered');
      return new CommentsService(ctx.restApiService);
    },
    checksService: (ctx: Partial<AppContext>) => {
      if (!ctx.reportingService)
        throw new Error('AppContext.reportingService is not registered');
      return new ChecksService(ctx.reportingService);
    },
    jsApiService: (ctx: Partial<AppContext>) => {
      if (!ctx.reportingService)
        throw new Error('AppContext.reportingService is not registered');
      return new GrJsApiInterface(ctx.reportingService);
    },
    storageService: (_ctx: Partial<AppContext>) => new GrStorageService(),
    configService: (ctx: Partial<AppContext>) => {
      if (!ctx.restApiService)
        throw new Error('AppContext.restApiService is not registered');
      return new ConfigService(ctx.restApiService);
    },
    userService: (ctx: Partial<AppContext>) => {
      if (!ctx.restApiService)
        throw new Error('AppContext.restApiService is not registered');
      return new UserService(ctx.restApiService);
    },
    shortcutsService: (ctx: Partial<AppContext>) => {
      if (!ctx.reportingService)
        throw new Error('AppContext.reportingService is not registered');
      return new ShortcutsService(ctx.reportingService);
    },
    browserService: (_ctx: Partial<AppContext>) => new BrowserService(),
  };
  return create<AppContext>(appRegistry, initializedServices) as AppContext;
}
