/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-js-api-interface';
import {GrPluginStyleApi} from './gr-plugin-style-api';
import {PluginApi} from '../../../api/plugin';
import {getAppContext} from '../../../services/app-context';
import {queryAndAssert} from '../../../utils/common-util';
import {assert} from '@open-wc/testing';

suite('gr-plugin-style-api tests', () => {
  let instance: GrPluginStyleApi;

  setup(() => {
    let pluginApi: PluginApi;
    window.Gerrit.install(
      p => {
        pluginApi = p;
      },
      '0.1',
      'http://test.com/plugins/testplugin/static/test.js'
    );
    instance = new GrPluginStyleApi(
      getAppContext().reportingService,
      pluginApi!
    );
  });

  test('addStyle', async () => {
    instance.insertRule('html{color:green;}');
    const styleEl = queryAndAssert<HTMLStyleElement>(
      document.head,
      'style#plugin-style'
    );
    const styleSheet = styleEl.sheet;
    assert.equal(styleSheet?.cssRules.length, 1);
  });
});
