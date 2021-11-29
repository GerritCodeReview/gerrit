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
import {getAppContext} from '../../../services/app-context.js';
import {stubRestApi} from '../../../test/test-utils.js';
import {PluginApi} from '../../../api/plugin.js';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting.js';
import {ReportingPluginApi} from '../../../api/reporting.js';

suite('gr-reporting-js-api tests', () => {
  let plugin: PluginApi;
  let reportingService: ReportingService;

  setup(() => {
    stubRestApi('getAccount').returns(Promise.resolve(undefined));
    reportingService = getAppContext().reportingService;
  });

  suite('early init', () => {
    let reporting: ReportingPluginApi;
    setup(() => {
      window.Gerrit.install(
        p => {
          plugin = p;
        },
        '0.1',
        'http://test.com/plugins/testplugin/static/test.js'
      );
      reporting = plugin.reporting();
    });

    test('redirect reportInteraction call to reportingService', () => {
      const spy = sinon.spy(reportingService, 'reportPluginInteractionLog');
      reporting.reportInteraction('test', {});
      assert.isTrue(spy.called);
      assert.equal(spy.lastCall.args[0], 'testplugin-test');
      assert.deepEqual(spy.lastCall.args[1], {});
    });

    test('redirect reportLifeCycle call to reportingService', () => {
      const spy = sinon.spy(reportingService, 'reportPluginLifeCycleLog');
      reporting.reportLifeCycle('test', {});
      assert.isTrue(spy.called);
      assert.equal(spy.lastCall.args[0], 'testplugin-test');
      assert.deepEqual(spy.lastCall.args[1], {});
    });
  });
});
