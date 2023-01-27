/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  BasePatchSetNum,
  RevisionPatchSetNum,
  ServerInfo,
} from '../api/rest-api';
import '../test/common-test-setup';
import {createGerritInfo, createServerInfo} from '../test/test-data-generators';
import {
  getBaseUrl,
  getDocsBaseUrl,
  testOnly_clearDocsBaseUrlCache,
  encodeURL,
  singleDecodeURL,
  toPath,
  toPathname,
  toSearchParams,
  getPatchRangeExpression,
  PatchRangeParams,
} from './url-util';
import {getAppContext, AppContext} from '../services/app-context';
import {stubRestApi} from '../test/test-utils';
import {assert} from '@open-wc/testing';

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
      testOnly_clearDocsBaseUrlCache();
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
      test('does not encode alphanumeric chars', () => {
        assert.equal(encodeURL("AZaz09-_.!~*'()"), "AZaz09-_.!~*'()");
      });

      test('double encodes %', () => {
        assert.equal(encodeURL('abc%def'), 'abc%2525def');
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
