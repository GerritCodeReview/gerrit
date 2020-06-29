/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

import '../../test/common-test-setup-karma.js';
import {Polymer} from '@polymer/polymer/lib/legacy/polymer-fn.js';
import {SafeTypes} from './safe-types-behavior.js';

const basicFixture = fixtureFromElement('safe-types-element');

suite('gr-tooltip-behavior tests', () => {
  let element;
  let sandbox;

  suiteSetup(() => {
    Polymer({
      is: 'safe-types-element',
      behaviors: [SafeTypes],
    });
  });

  setup(() => {
    sandbox = sinon.sandbox.create();
    element = basicFixture.instantiate();
  });

  teardown(() => {
    sandbox.restore();
  });

  test('SafeUrl accepts valid urls', () => {
    function accepts(url) {
      const safeUrl = new element.SafeUrl(url);
      assert.isOk(safeUrl);
      assert.equal(url, safeUrl.asString());
    }
    accepts('http://www.google.com/');
    accepts('https://www.google.com/');
    accepts('HtTpS://www.google.com/');
    accepts('//www.google.com/');
    accepts('/c/1234/file/path.html@45');
    accepts('#hash-url');
    accepts('mailto:name@example.com');
  });

  test('SafeUrl rejects invalid urls', () => {
    function rejects(url) {
      assert.throws(() => { new element.SafeUrl(url); });
    }
    rejects('javascript://alert("evil");');
    rejects('ftp:example.com');
    rejects('data:text/html,scary business');
  });

  suite('safeTypesBridge', () => {
    function acceptsString(value, type) {
      assert.equal(SafeTypes.safeTypesBridge(value, type),
          value);
    }

    function rejects(value, type) {
      assert.throws(() => { SafeTypes.safeTypesBridge(value, type); });
    }

    test('accepts valid URL strings', () => {
      acceptsString('/foo/bar', 'URL');
      acceptsString('#baz', 'URL');
    });

    test('rejects invalid URL strings', () => {
      rejects('javascript://void();', 'URL');
    });

    test('accepts SafeUrl values', () => {
      const url = '/abc/123';
      const safeUrl = new element.SafeUrl(url);
      assert.equal(SafeTypes.safeTypesBridge(safeUrl, 'URL'), url);
    });

    test('rejects non-string or non-SafeUrl types', () => {
      rejects(3.1415926, 'URL');
    });

    test('accepts any binding to STRING or CONSTANT', () => {
      acceptsString('foo/bar/baz', 'STRING');
      acceptsString('lorem ipsum dolor', 'CONSTANT');
    });

    test('rejects all other types', () => {
      rejects('foo', 'JAVASCRIPT');
      rejects('foo', 'HTML');
      rejects('foo', 'RESOURCE_URL');
      rejects('foo', 'STYLE');
    });
  });
});

