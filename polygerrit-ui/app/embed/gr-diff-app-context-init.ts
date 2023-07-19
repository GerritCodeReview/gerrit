/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Finalizable} from '../services/registry';
import {AppContext} from '../services/app-context';
import {AuthService} from '../services/gr-auth/gr-auth';
import {FlagsService} from '../services/flags/flags';
import {grReportingMock} from '../services/gr-reporting/gr-reporting_mock';
import {grRestApiMock} from '../test/mocks/gr-rest-api_mock';

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
  const flagsService = new MockFlagsService();
  const reportingService = grReportingMock;
  const authService = new MockAuthService();
  const restApiService = grRestApiMock;
  return {
    flagsService,
    reportingService,
    authService,
    restApiService,
    finalize: () => {
      reportingService.finalize();
      restApiService.finalize();
      authService.finalize();
      flagsService.finalize();
    },
  };
}
