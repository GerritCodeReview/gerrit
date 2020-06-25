
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


<meta charset="utf-8">








import '../../../test/common-test-setup.js';
import './gr-js-api-interface.js';
import {getPluginNameFromUrl} from './gr-api-utils.js';

const PRELOADED_PROTOCOL = 'preloaded:';

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
          getPluginNameFromUrl('http://example.com/static/a.html'),
          null
      );
    });

    test('with valid urls', () => {
      assert.equal(
          getPluginNameFromUrl('http://example.com/plugins/a.html'),
          'a'
      );
      assert.equal(
          getPluginNameFromUrl('http://example.com/plugins/a/static/t.html'),
          'a'
      );
    });

    test('with preloaded urls', () => {
      assert.equal(getPluginNameFromUrl(`${PRELOADED_PROTOCOL}a`), 'a');
    });

    test('with gerrit-theme override', () => {
      assert.equal(
          getPluginNameFromUrl('http://example.com/static/gerrit-theme.html'),
          'gerrit-theme'
      );
    });

    test('with ASSETS_PATH', () => {
      window.ASSETS_PATH = 'http://cdn.com/2';
      assert.equal(
          getPluginNameFromUrl(`${window.ASSETS_PATH}/plugins/a.html`),
          'a'
      );
      window.ASSETS_PATH = undefined;
    });
  });
});

