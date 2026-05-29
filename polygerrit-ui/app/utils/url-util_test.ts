/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {AuthType, BasePatchSetNum, RevisionPatchSetNum} from '../api/rest-api';
import '../test/common-test-setup';
import {
  encodeURL,
  getBaseUrl,
  getDocUrl,
  getPatchRangeExpression,
  loginUrl,
  PatchRangeParams,
  sameOrigin,
  singleDecodeURL,
  toPath,
  toPathname,
  toSearchParams,
} from './url-util';
import {assert} from '@open-wc/testing';
import {createAuth} from '../test/test-data-generators';

suite('url-util tests', () => {
  suite('getBaseUrl tests', () => {
    let originalCanonicalPath: string | undefined;

    suiteSetup(() => {
      originalCanonicalPath = window.CANONICAL_PATH;
      window.CANONICAL_PATH = '/r';
    });

    suiteTeardown(() => {
      window.CANONICAL_PATH = originalCanonicalPath;
    });

    test('getBaseUrl', () => {
      assert.deepEqual(getBaseUrl(), '/r');
    });
  });

  suite('getDocUrl tests', () => {
    test('getDocUrl', () => {
      assert.deepEqual(getDocUrl('a', 'b'), 'a/b');
      assert.deepEqual(getDocUrl('a/', 'b'), 'a/b');
      assert.deepEqual(getDocUrl('a', '/b'), 'a/b');
      assert.deepEqual(getDocUrl('a/', '/b'), 'a/b');
    });
  });

  suite('loginUrl tests', () => {
    const authConfig = createAuth();

    test('default url if auth.loginUrl is not defined', () => {
      const current = encodeURIComponent(
        window.location.pathname + window.location.search + window.location.hash
      );
      assert.deepEqual(loginUrl(undefined), '/login/' + current);
      assert.deepEqual(loginUrl(authConfig), '/login/' + current);
    });

    test('default url if auth type is not HTTP or HTTP_LDAP', () => {
      const defaultUrl =
        '/login/' +
        encodeURIComponent(
          window.location.pathname +
            window.location.search +
            window.location.hash
        );
      const customLoginUrl = '/custom';
      authConfig.login_url = customLoginUrl;

      authConfig.auth_type = AuthType.LDAP;
      assert.deepEqual(loginUrl(authConfig), defaultUrl);
      authConfig.auth_type = AuthType.OPENID_SSO;
      assert.deepEqual(loginUrl(authConfig), defaultUrl);
      authConfig.auth_type = AuthType.OAUTH;
      assert.deepEqual(loginUrl(authConfig), defaultUrl);
    });

    test('use auth.loginUrl when defined', () => {
      const customLoginUrl = '/custom';
      authConfig.login_url = customLoginUrl;

      authConfig.auth_type = AuthType.HTTP;
      assert.deepEqual(loginUrl(authConfig), customLoginUrl);
      authConfig.auth_type = AuthType.HTTP_LDAP;
      assert.deepEqual(loginUrl(authConfig), customLoginUrl);
    });

    test('auth.loginUrl is sanitized when defined as a relative url', () => {
      authConfig.login_url = 'custom';
      authConfig.auth_type = AuthType.HTTP;
      assert.deepEqual(loginUrl(authConfig), '/custom');
    });
  });

  suite('url encoding and decoding tests', () => {
    suite('encodeURL', () => {
      test('does not encode alphanumeric chars', () => {
        assert.equal(encodeURL("AZaz09-_.!~*'()"), "AZaz09-_.!~*'()");
      });

      test('double encodes %', () => {
        assert.equal(encodeURL('abc%def'), 'abc%2525def');
      });

      test('double encodes +', () => {
        assert.equal(encodeURL('abc+def'), 'abc%252Bdef');
      });

      test('does not encode colon and slash', () => {
        assert.equal(encodeURL(':/'), ':/');
      });

      test('encodes spaces as +', () => {
        assert.equal(encodeURL('words with spaces'), 'words+with+spaces');
      });
    });

    suite('singleDecodeUrl', () => {
      test('single decodes', () => {
        assert.equal(singleDecodeURL('abc%3Fdef'), 'abc?def');
      });

      test('converts + to space', () => {
        assert.equal(singleDecodeURL('ghi+jkl'), 'ghi jkl');
      });
    });
  });

  test('sameOrigin', () => {
    assert.isTrue(sameOrigin('/asdf'));
    assert.isTrue(sameOrigin(window.location.origin + '/asdf'));
    assert.isFalse(sameOrigin('http://www.goole.com/asdf'));
    assert.isFalse(sameOrigin('http://b]'));
  });

  test('toPathname', () => {
    assert.equal(toPathname('asdf'), 'asdf');
    assert.equal(toPathname('asdf?qwer=zxcv'), 'asdf');
  });

  test('toSearchParams', () => {
    assert.equal(toSearchParams('asdf').toString(), '');
    assert.equal(toSearchParams('asdf?qwer=zxcv').get('qwer'), 'zxcv');
  });

  test('toPathname', () => {
    const params = new URLSearchParams();
    assert.equal(toPath('asdf', params), 'asdf');
    params.set('qwer', 'zxcv');
    assert.equal(toPath('asdf', params), 'asdf?qwer=zxcv');
    assert.equal(
      toPath(toPathname('asdf?qwer=zxcv'), toSearchParams('asdf?qwer=zxcv')),
      'asdf?qwer=zxcv'
    );
  });

  test('getPatchRangeExpression', () => {
    const params: PatchRangeParams = {};
    let actual = getPatchRangeExpression(params);
    assert.equal(actual, '');

    params.patchNum = 4 as RevisionPatchSetNum;
    actual = getPatchRangeExpression(params);
    assert.equal(actual, '4');

    params.basePatchNum = 2 as BasePatchSetNum;
    actual = getPatchRangeExpression(params);
    assert.equal(actual, '2..4');

    delete params.patchNum;
    actual = getPatchRangeExpression(params);
    assert.equal(actual, '2..');
  });
});
