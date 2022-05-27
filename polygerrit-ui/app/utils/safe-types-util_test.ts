/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../test/common-test-setup-karma';
import {safeTypesBridge, _testOnly_SafeUrl} from './safe-types-util';

suite('safe-types-util tests', () => {
  test('SafeUrl accepts valid urls', () => {
    function accepts(url: string) {
      const safeUrl = new _testOnly_SafeUrl(url);
      assert.isOk(safeUrl);
      assert.equal(url, safeUrl.toString());
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
    function rejects(url: string) {
      assert.throws(() => {
        new _testOnly_SafeUrl(url);
      });
    }
    rejects('javascript://alert("evil");');
    rejects('ftp:example.com');
    rejects('data:text/html,scary business');
  });

  suite('safeTypesBridge', () => {
    function acceptsString(value: string, type: string) {
      assert.equal(safeTypesBridge(value, type), value);
    }

    function rejects(value: unknown, type: string) {
      assert.throws(() => {
        safeTypesBridge(value, type);
      });
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
      const safeUrl = new _testOnly_SafeUrl(url);
      assert.equal(safeTypesBridge(safeUrl, 'URL'), url);
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
