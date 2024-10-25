/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import '../../change/gr-reply-dialog/gr-reply-dialog';
import {getAppContext} from '../../../services/app-context';
import {stubRestApi} from '../../../test/test-utils';
import {PluginApi} from '../../../api/plugin';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {ReportingPluginApi} from '../../../api/reporting';
import {assert} from '@open-wc/testing';

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
