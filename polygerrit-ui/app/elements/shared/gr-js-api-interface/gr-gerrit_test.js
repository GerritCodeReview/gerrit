/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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

import '../../../test/common-test-setup-karma.js';
import './gr-js-api-interface.js';
import {pluginLoader} from './gr-plugin-loader.js';
import {resetPlugins} from '../../../test/test-utils.js';
import {_testOnly_initGerritPluginApi} from './gr-gerrit.js';

const basicFixture = fixtureFromElement('gr-js-api-interface');

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-gerrit tests', () => {
  let element;
  let sandbox;
  let sendStub;

  setup(() => {
    window.clock = sinon.useFakeTimers();
    sandbox = sinon.sandbox.create();
    sendStub = sandbox.stub().returns(Promise.resolve({status: 200}));
    stub('gr-rest-api-interface', {
      getAccount() {
        return Promise.resolve({name: 'Judy Hopps'});
      },
      send(...args) {
        return sendStub(...args);
      },
    });
    element = basicFixture.instantiate();
  });

  teardown(() => {
    window.clock.restore();
    sandbox.restore();
    element._removeEventCallbacks();
    resetPlugins();
  });

  suite('proxy methods', () => {
    test('Gerrit._isPluginEnabled proxy to pluginLoader', () => {
      const stubFn = sandbox.stub();
      sandbox.stub(
          pluginLoader,
          'isPluginEnabled',
          (...args) => stubFn(...args)
      );
      pluginApi._isPluginEnabled('test_plugin');
      assert.isTrue(stubFn.calledWith('test_plugin'));
    });

    test('Gerrit._isPluginLoaded proxy to pluginLoader', () => {
      const stubFn = sandbox.stub();
      sandbox.stub(
          pluginLoader,
          'isPluginLoaded',
          (...args) => stubFn(...args)
      );
      pluginApi._isPluginLoaded('test_plugin');
      assert.isTrue(stubFn.calledWith('test_plugin'));
    });

    test('Gerrit._isPluginPreloaded proxy to pluginLoader', () => {
      const stubFn = sandbox.stub();
      sandbox.stub(
          pluginLoader,
          'isPluginPreloaded',
          (...args) => stubFn(...args)
      );
      pluginApi._isPluginPreloaded('test_plugin');
      assert.isTrue(stubFn.calledWith('test_plugin'));
    });
  });
});

