/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {resetPlugins} from '../../../test/test-utils';
import {GerritImpl, GerritInternal} from './gr-gerrit';
import {stubRestApi} from '../../../test/test-utils';
import {getAppContext} from '../../../services/app-context';
import {GrJsApiInterface} from './gr-js-api-interface-element';
import {SinonFakeTimers} from 'sinon';
import {Timestamp} from '../../../api/rest-api';

suite('gr-gerrit tests', () => {
  let element: GrJsApiInterface;
  let clock: SinonFakeTimers;
  let pluginApi: GerritInternal;

  setup(() => {
    clock = sinon.useFakeTimers();

    stubRestApi('getAccount').returns(
      Promise.resolve({name: 'Judy Hopps', registered_on: '' as Timestamp})
    );
    stubRestApi('send').returns(
      Promise.resolve({...new Response(), status: 200})
    );
    element = getAppContext().jsApiService as GrJsApiInterface;
    pluginApi = new GerritImpl(
      getAppContext().reportingService,
      getAppContext().eventEmitter,
      getAppContext().restApiService,
      getAppContext().pluginLoader
    );
  });

  teardown(() => {
    clock.restore();
    element._removeEventCallbacks();
    resetPlugins();
  });
});
