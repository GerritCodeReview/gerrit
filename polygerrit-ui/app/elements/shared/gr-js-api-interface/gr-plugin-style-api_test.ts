/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-js-api-interface';
import {query, queryAndAssert} from '../../../utils/common-util';
import {assert} from '@open-wc/testing';
import {StylePluginApi} from '../../../api/styles';

suite('gr-plugin-style-api tests', () => {
  let styleApi: StylePluginApi;

  setup(() => {
    window.Gerrit.install(
      p => (styleApi = p.styleApi()),
      '0.1',
      'http://test.com/plugins/testplugin/static/test.js'
    );
  });

  teardown(() => {
    const styleEl = query<HTMLStyleElement>(
      document.head,
      'style#plugin-style'
    );
    styleEl?.remove();
  });

  test('insertCSSRule adds a rule', async () => {
    styleApi.insertCSSRule('html{color:green;}');
    const styleEl = queryAndAssert<HTMLStyleElement>(
      document.head,
      'style#plugin-style'
    );
    const styleSheet = styleEl.sheet;
    assert.equal(styleSheet?.cssRules.length, 1);
  });

  test('insertCSSRule re-uses the <style> element', async () => {
    styleApi.insertCSSRule('html{color:green;}');
    styleApi.insertCSSRule('html{margin:0px;}');
    const styleEl = queryAndAssert<HTMLStyleElement>(
      document.head,
      'style#plugin-style'
    );
    const styleSheet = styleEl.sheet;
    assert.equal(styleSheet?.cssRules.length, 2);
  });
});
