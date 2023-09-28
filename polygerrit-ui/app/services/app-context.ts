/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {FlagsService} from './flags/flags';
import {ReportingService} from './gr-reporting/gr-reporting';
import {AuthService} from './gr-auth/gr-auth';
import {RestApiService} from './gr-rest-api/gr-rest-api';
import {Finalizable} from '../types/types';

export interface AppContext {
  flagsService: FlagsService;
  reportingService: ReportingService;
  authService: AuthService;
  restApiService: RestApiService;
}

/**
 * The AppContext holds instances of services. It's a convenient way to provide
 * singletons that can be swapped out for testing.
 *
 * AppContext is initialized in ./app-context-init.js
 *
 * It is guaranteed that all fields in appContext are always initialized
 * (except for shared gr-diff)
 */
let appContext: (AppContext & Finalizable) | undefined = undefined;

export function injectAppContext(ctx: AppContext & Finalizable) {
  appContext?.finalize();
  appContext = ctx;
}

export function getAppContext() {
  if (!appContext) throw new Error('App context has not been injected');
  return appContext;
}
