/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  AuthType,
  AUTO_MERGE,
  BasePatchSetNum,
  NumericChangeId,
  PARENT,
  RepoName,
  RevisionPatchSetNum,
} from '../api/rest-api';
import {ParsedJSON} from '../types/common';
import '../test/common-test-setup';
import {
  computeThemeStyleCacheKey,
  encodeURL,
  getBaseUrl,
  getChangePrefix,
  getDocUrl,
  getInitialData,
  getPatchRangeExpression,
  getThemeStyleCacheStorageKey,
  loginUrl,
  PatchRangeParams,
  sameOrigin,
  singleDecodeURL,
  toPath,
  toPathname,
  toSearchParams,
} from './url-util';
import {assert} from '@open-wc/testing';
import {createAuth, createServerInfo} from '../test/test-data-generators';

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

  test('getPatchRangeExpression encodes AUTO_MERGE as 0', () => {
    assert.equal(
      getPatchRangeExpression({
        basePatchNum: AUTO_MERGE,
        patchNum: 4 as RevisionPatchSetNum,
      }),
      '0..4'
    );
    // A lone `0` means "auto merge against the latest patchset".
    assert.equal(getPatchRangeExpression({basePatchNum: AUTO_MERGE}), '0');
    // `PARENT` stays implicit, so that it can be overridden by the
    // `default_base_for_merges` preference.
    assert.equal(
      getPatchRangeExpression({
        basePatchNum: PARENT,
        patchNum: 4 as RevisionPatchSetNum,
      }),
      '4'
    );
  });

  suite('getChangePrefix', () => {
    let originalPath: string;

    setup(() => {
      originalPath = window.location.pathname;
    });

    teardown(() => {
      window.history.replaceState(null, '', originalPath);
    });

    test('strips patchset, diff range, and file path subpaths', () => {
      const origin = window.location.origin;

      window.history.replaceState(null, '', '/c/my-repo/+/123');
      assert.equal(getChangePrefix(), `${origin}/c/my-repo/+/123`);

      window.history.replaceState(null, '', '/c/my-repo/+/123/4');
      assert.equal(getChangePrefix(), `${origin}/c/my-repo/+/123`);

      window.history.replaceState(null, '', '/c/my-repo/+/123/1..4');
      assert.equal(getChangePrefix(), `${origin}/c/my-repo/+/123`);

      window.history.replaceState(
        null,
        '',
        '/c/my-repo/+/123/1..4/src/file.ts'
      );
      assert.equal(getChangePrefix(), `${origin}/c/my-repo/+/123`);
    });

    test('falls back to change project and number when pathname is not a change URL', () => {
      const origin = window.location.origin;
      window.history.replaceState(null, '', '/context.html');

      assert.equal(
        getChangePrefix({
          project: 'my/project' as RepoName,
          _number: 456 as NumericChangeId,
        }),
        `${origin}/c/my/project/+/456`
      );

      assert.equal(
        getChangePrefix({
          project: 'my/project+special%repo' as RepoName,
          _number: 456 as NumericChangeId,
        }),
        `${origin}/c/my/project%252Bspecial%2525repo/+/456`
      );
    });
  });

  suite('getInitialData and theme cache key helpers', () => {
    let hadCanonicalPath: boolean;
    let originalCanonicalPath: string | undefined;
    let hadAssetsPath: boolean;
    let originalAssetsPath: string | undefined;
    let hadInitialData: boolean;
    let originalInitialData: typeof window.INITIAL_DATA;

    setup(() => {
      hadCanonicalPath = 'CANONICAL_PATH' in window;
      originalCanonicalPath = window.CANONICAL_PATH;
      hadAssetsPath = 'ASSETS_PATH' in window;
      originalAssetsPath = window.ASSETS_PATH;
      hadInitialData = 'INITIAL_DATA' in window;
      originalInitialData = window.INITIAL_DATA;
    });

    teardown(() => {
      if (hadCanonicalPath) {
        window.CANONICAL_PATH = originalCanonicalPath;
      } else {
        delete window.CANONICAL_PATH;
      }
      if (hadAssetsPath) {
        window.ASSETS_PATH = originalAssetsPath;
      } else {
        delete window.ASSETS_PATH;
      }
      if (hadInitialData) {
        window.INITIAL_DATA = originalInitialData;
      } else {
        delete window.INITIAL_DATA;
      }
    });

    test('getInitialData handles missing INITIAL_DATA and CANONICAL_PATH', () => {
      delete window.INITIAL_DATA;
      assert.isUndefined(getInitialData('/config/server/info'));

      window.CANONICAL_PATH = '/r';
      window.INITIAL_DATA = {
        '/r/config/server/version': '3.13' as unknown as ParsedJSON,
        '/accounts/self/preferences': {
          theme: 'DARK',
        } as unknown as ParsedJSON,
      };
      assert.equal(getInitialData<string>('/config/server/version'), '3.13');
      assert.deepEqual(
        getInitialData<{theme: string}>('/accounts/self/preferences'),
        {theme: 'DARK'}
      );
      assert.equal(getThemeStyleCacheStorageKey(), 'gr-theme-style-cache:/r');
    });

    test('computeThemeStyleCacheKey incorporates assets, theme, and plugins', () => {
      window.ASSETS_PATH = 'https://cdn.example.com';
      const info = {
        ...createServerInfo(),
        default_theme: '/static/gerrit-theme.js',
        plugin: {
          has_avatars: false,
          js_resource_paths: ['plugins/gerrithub/static/gerrithub.js'],
        },
      };
      assert.equal(
        computeThemeStyleCacheKey(info),
        JSON.stringify([
          'https://cdn.example.com',
          '/static/gerrit-theme.js',
          'plugins/gerrithub/static/gerrithub.js',
        ])
      );
    });
  });
});
