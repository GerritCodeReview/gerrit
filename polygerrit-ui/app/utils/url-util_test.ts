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

import {ServerInfo} from '../api/rest-api';
import '../test/common-test-setup-karma';
import {createGerritInfo, createServerInfo} from '../test/test-data-generators';
import {
  getBaseUrl,
  getDocsBaseUrl,
  _testOnly_clearDocsBaseUrlCache,
  encodeURL,
  singleDecodeURL,
  toPath,
  toPathname,
  toSearchParams,
  getPublicAvailableUrl,
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

  suite('getPublicAvailableUrl tests', () => {
    let oldPrivateToPublicHostMap: typeof window.PRIVATE_TO_PUBLIC_HOST_MAP;
    setup(() => {
      oldPrivateToPublicHostMap = window.PRIVATE_TO_PUBLIC_HOST_MAP;
    });
    teardown(() => {
      window.PRIVATE_TO_PUBLIC_HOST_MAP = oldPrivateToPublicHostMap;
    });

    test('PRIVATE_TO_PUBLIC_HOST_MAP not set - url not changed', () => {
      assert.equal(
        getPublicAvailableUrl('https://gerrit-review.private.corp'),
        'https://gerrit-review.private.corp'
      );
      assert.equal(
        getPublicAvailableUrl('https://gerrit-review.private.corp/'),
        'https://gerrit-review.private.corp/'
      );
      assert.equal(
        getPublicAvailableUrl('https://gerrit-review.private.corp/a/b#c'),
        'https://gerrit-review.private.corp/a/b#c'
      );
      assert.equal(getPublicAvailableUrl('relative/url#x'), 'relative/url#x');
      assert.equal(
        getPublicAvailableUrl('https://gerrit-review.private.corp/a/b?var=x#c'),
        'https://gerrit-review.private.corp/a/b?var=x#c'
      );
      assert.equal(getPublicAvailableUrl('relative/url#x'), 'relative/url#x');
    });

    test('PRIVATE_TO_PUBLIC_HOST_MAP set - absolute url are updated', () => {
      window.PRIVATE_TO_PUBLIC_HOST_MAP = {
        'gerrit-review.private.corp': 'gerrit-review.googlesource.com',
        'ho.ho.ho.corp': 'santa.claus',
      };
      assert.equal(
        getPublicAvailableUrl('https://gerrit-review.private.corp'),
        'https://gerrit-review.googlesource.com/'
      );
      assert.equal(
        getPublicAvailableUrl('https://gerrit-review.private.corp/'),
        'https://gerrit-review.googlesource.com/'
      );
      assert.equal(
        getPublicAvailableUrl('https://ho.ho.ho.corp/a/b#c'),
        'https://santa.claus/a/b#c'
      );
      assert.equal(
        getPublicAvailableUrl('https://ho.ho.ho.corp/a/b?var=x#c'),
        'https://santa.claus/a/b?var=x#c'
      );
    });

    test('PRIVATE_TO_PUBLIC_HOST_MAP set - hosts not in map are not updated', () => {
      window.PRIVATE_TO_PUBLIC_HOST_MAP = {
        'gerrit-review.private.corp': 'gerrit-review.googlesource.com',
        'ho.ho.ho.corp': 'santa.claus',
      };
      assert.equal(
        getPublicAvailableUrl('https://gerrit-review.googlesource.com'),
        'https://gerrit-review.googlesource.com'
      );
      assert.equal(
        getPublicAvailableUrl('https://android-review.googlesource.com/a/b#c'),
        'https://android-review.googlesource.com/a/b#c'
      );
    });
    test('PRIVATE_TO_PUBLIC_HOST_MAP set - relative url are not updated', () => {
      window.PRIVATE_TO_PUBLIC_HOST_MAP = {
        'gerrit-review.private.corp': 'gerrit-review.googlesource.com',
        'ho.ho.ho.corp': 'santa.claus',
      };
      assert.equal(getPublicAvailableUrl('./a/b/c'), './a/b/c');
      assert.equal(getPublicAvailableUrl('a/b/c#d'), 'a/b/c#d');
      assert.equal(getPublicAvailableUrl('/a/b/c#d'), '/a/b/c#d');
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
