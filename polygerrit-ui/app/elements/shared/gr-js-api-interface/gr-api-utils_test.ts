/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-js-api-interface';
import {getPluginNameFromUrl} from './gr-api-utils';

suite('gr-api-utils tests', () => {
  suite('test getPluginNameFromUrl', () => {
    test('with empty string', () => {
      assert.equal(getPluginNameFromUrl(''), null);
    });

    test('with invalid url', () => {
      assert.equal(getPluginNameFromUrl('test'), null);
    });

    test('with random invalid url', () => {
      assert.equal(getPluginNameFromUrl('http://example.com'), null);
      assert.equal(
        getPluginNameFromUrl('http://example.com/static/a.js'),
        null
      );
    });

    test('with valid urls', () => {
      assert.equal(
        getPluginNameFromUrl('http://example.com/plugins/a.js'),
        'a'
      );
      assert.equal(
        getPluginNameFromUrl('http://example.com/plugins/a/static/t.js'),
        'a'
      );
    });

    test('with gerrit-theme override', () => {
      assert.equal(
        getPluginNameFromUrl('http://example.com/static/gerrit-theme.js'),
        'gerrit-theme'
      );
    });

    test('with ASSETS_PATH', () => {
      window.ASSETS_PATH = 'http://cdn.com/2';
      assert.equal(
        getPluginNameFromUrl(`${window.ASSETS_PATH}/plugins/a.js`),
        'a'
      );
      window.ASSETS_PATH = undefined;
    });
  });
});
