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

import {AuthType, ServerInfo} from '../api/rest-api';
import '../test/common-test-setup-karma';
import {
  createAuth,
  createGerritInfo,
  createServerInfo,
} from '../test/test-data-generators';
import {
  getBaseUrl,
  getDocsBaseUrl,
  _testOnly_clearDocsBaseUrlCache,
  encodeURL,
  singleDecodeURL,
  toPath,
  toPathname,
  toSearchParams,
  loginUrl,
} from './url-util';
import {getAppContext, AppContext} from '../services/app-context';
import {stubRestApi} from '../test/test-utils';

suite('url-util tests', () => {
  let appContext: AppContext;
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

  suite('loginUrl tests', () => {
    const authConfig = createAuth();
    const customLoginUrl = '/custom';
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

      authConfig.login_url = customLoginUrl;
      authConfig.auth_type = AuthType.LDAP;
      assert.deepEqual(loginUrl(authConfig), defaultUrl);
      authConfig.auth_type = AuthType.OPENID_SSO;
      assert.deepEqual(loginUrl(authConfig), defaultUrl);
      authConfig.auth_type = AuthType.OAUTH;
      assert.deepEqual(loginUrl(authConfig), defaultUrl);
    });
    test('use auth.loginUrl when defined', () => {
      authConfig.login_url = customLoginUrl;
      authConfig.auth_type = AuthType.HTTP;
      assert.deepEqual(loginUrl(authConfig), customLoginUrl);
      authConfig.auth_type = AuthType.HTTP_LDAP;
      assert.deepEqual(loginUrl(authConfig), customLoginUrl);
    });
  });

  suite('getDocsBaseUrl tests', () => {
    setup(() => {
      _testOnly_clearDocsBaseUrlCache();
      appContext = getAppContext();
    });

    test('null config', async () => {
      const probePathMock = stubRestApi('probePath').resolves(true);
      const docsBaseUrl = await getDocsBaseUrl(
        undefined,
        appContext.restApiService
      );
      assert.isTrue(probePathMock.calledWith('/Documentation/index.html'));
      assert.equal(docsBaseUrl, '/Documentation');
    });

    test('no doc config', async () => {
      const probePathMock = stubRestApi('probePath').resolves(true);
      const config: ServerInfo = {
        ...createServerInfo(),
        gerrit: createGerritInfo(),
      };
      const docsBaseUrl = await getDocsBaseUrl(
        config,
        appContext.restApiService
      );
      assert.isTrue(probePathMock.calledWith('/Documentation/index.html'));
      assert.equal(docsBaseUrl, '/Documentation');
    });

    test('has doc config', async () => {
      const probePathMock = stubRestApi('probePath').resolves(true);
      const config: ServerInfo = {
        ...createServerInfo(),
        gerrit: {...createGerritInfo(), doc_url: 'foobar'},
      };
      const docsBaseUrl = await getDocsBaseUrl(
        config,
        appContext.restApiService
      );
      assert.isFalse(probePathMock.called);
      assert.equal(docsBaseUrl, 'foobar');
    });

    test('no probe', async () => {
      const probePathMock = stubRestApi('probePath').resolves(false);
      const docsBaseUrl = await getDocsBaseUrl(
        undefined,
        appContext.restApiService
      );
      assert.isTrue(probePathMock.calledWith('/Documentation/index.html'));
      assert.isNotOk(docsBaseUrl);
    });
  });

  suite('url encoding and decoding tests', () => {
    suite('encodeURL', () => {
      test('double encodes', () => {
        assert.equal(encodeURL('abc?123'), 'abc%253F123');
        assert.equal(encodeURL('def/ghi'), 'def%252Fghi');
        assert.equal(encodeURL('jkl'), 'jkl');
        assert.equal(encodeURL(''), '');
      });

      test('does not convert colons', () => {
        assert.equal(encodeURL('mno:pqr'), 'mno:pqr');
      });

      test('converts spaces to +', () => {
        assert.equal(encodeURL('words with spaces'), 'words+with+spaces');
      });

      test('does not convert slashes when configured', () => {
        assert.equal(encodeURL('stu/vwx', true), 'stu/vwx');
      });

      test('does not convert slashes when configured', () => {
        assert.equal(encodeURL('stu/vwx', true), 'stu/vwx');
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
});
