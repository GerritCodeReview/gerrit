/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {create, Registry} from '../services/registry';
import {AppContext} from '../services/app-context';
import {AuthService} from '../services/gr-auth/gr-auth';
import {FlagsService} from '../services/flags/flags';
import {grReportingMock} from '../services/gr-reporting/gr-reporting_mock';
import {Finalizable} from '../types/types';

class MockFlagsService implements FlagsService {
  isEnabled() {
    return false;
  }

  finalize() {}

  /**
   * @return array of all enabled experiments.
   */
  get enabledExperiments() {
    return [];
  }
}

class MockAuthService implements AuthService {
  clearCache() {}

  get isAuthed() {
    return false;
  }

  authCheck() {
    return Promise.resolve(false);
  }

  baseUrl = '';

  setup() {}

  finalize() {}

  fetch() {
    const blob = new Blob();
    const init = {status: 200, statusText: 'Ack'};
    const response = new Response(blob, init);
    return Promise.resolve(response);
  }
}

// Setup mocks for appContext.
// This is a temporary solution
// TODO(dmfilippov): find a better solution for gr-diff
export function createDiffAppContext(): AppContext & Finalizable {
  const appRegistry: Registry<AppContext> = {
    flagsService: (_ctx: Partial<AppContext>) => new MockFlagsService(),
    authService: (_ctx: Partial<AppContext>) => new MockAuthService(),
    reportingService: (_ctx: Partial<AppContext>) => grReportingMock,
    eventEmitter: (_ctx: Partial<AppContext>) => {
      throw new Error('eventEmitter is not implemented');
    },
    restApiService: (_ctx: Partial<AppContext>) => {
      throw new Error('restApiService is not implemented');
    },
  };
  return create<AppContext>(appRegistry);
}
