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
import {assert} from '@open-wc/testing';

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
    pluginApi = new GerritImpl(getAppContext());
  });

  teardown(() => {
    clock.restore();
    element._removeEventCallbacks();
    resetPlugins();
  });

  suite('proxy methods', () => {
    test('Gerrit._isPluginEnabled proxy to getAppContext().pluginLoader', () => {
      const stubFn = sinon.stub();
      sinon
        .stub(getAppContext().pluginLoader, 'isPluginEnabled')
        .callsFake((...args) => stubFn(...args));
      pluginApi._isPluginEnabled('test_plugin');
      assert.isTrue(stubFn.calledWith('test_plugin'));
    });

    test('Gerrit._isPluginLoaded proxy to getAppContext().pluginLoader', () => {
      const stubFn = sinon.stub();
      sinon
        .stub(getAppContext().pluginLoader, 'isPluginLoaded')
        .callsFake((...args) => stubFn(...args));
      pluginApi._isPluginLoaded('test_plugin');
      assert.isTrue(stubFn.calledWith('test_plugin'));
    });
  });
});
