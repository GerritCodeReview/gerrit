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

import '../../../test/common-test-setup-karma.js';
import '../../change/gr-reply-dialog/gr-reply-dialog.js';
import {_testOnly_initGerritPluginApi} from './gr-gerrit.js';
import {appContext} from '../../../services/app-context.js';
import {stubRestApi} from '../../../test/test-utils.js';

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-reporting-js-api tests', () => {
  let reporting;
  let plugin;

  setup(() => {
    stubRestApi('getAccount').returns(Promise.resolve(null));
  });

  suite('early init', () => {
    setup(() => {
      pluginApi.install(p => { plugin = p; }, '0.1',
          'http://test.com/plugins/testplugin/static/test.js');
      reporting = plugin.reporting();
    });

    teardown(() => {
      reporting = null;
    });

    test('redirect reportInteraction call to reportingService', () => {
      sinon.spy(appContext.reportingService, 'reportInteraction');
      reporting.reportInteraction('test', {});
      assert.isTrue(appContext.reportingService.reportInteraction.called);
      assert.equal(
          appContext.reportingService.reportInteraction.lastCall.args[0],
          'testplugin-test'
      );
      assert.deepEqual(
          appContext.reportingService.reportInteraction.lastCall.args[1],
          {}
      );
    });

    test('redirect reportLifeCycle call to reportingService', () => {
      sinon.spy(appContext.reportingService, 'reportLifeCycle');
      reporting.reportLifeCycle('test', {});
      assert.isTrue(appContext.reportingService.reportLifeCycle.called);
      assert.equal(
          appContext.reportingService.reportLifeCycle.lastCall.args[0],
          'Plugin life cycle'
      );
      assert.deepEqual(
          appContext.reportingService.reportLifeCycle.lastCall.args[1],
          {pluginName: 'testplugin', eventName: 'test'}
      );
    });
  });
});
