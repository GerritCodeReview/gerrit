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

import '../test/common-test-setup-karma.js';
import {
  getBaseUrl,
  getDocsBaseUrl,
  _testOnly_clearDocsBaseUrlCache,
  encodeURL, singleDecodeURL,
} from './url-util.js';

suite('url-util tests', () => {
  suite('getBaseUrl tests', () => {
    let originalCanonicalPath;

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
    });

    test('null config', async () => {
      const mockRestApi = {
        probePath: sinon.stub().returns(Promise.resolve(true)),
      };
      const docsBaseUrl = await getDocsBaseUrl(null, mockRestApi);
      assert.isTrue(
          mockRestApi.probePath.calledWith('/Documentation/index.html'));
      assert.equal(docsBaseUrl, '/Documentation');
    });

    test('no doc config', async () => {
      const mockRestApi = {
        probePath: sinon.stub().returns(Promise.resolve(true)),
      };
      const config = {gerrit: {}};
      const docsBaseUrl = await getDocsBaseUrl(config, mockRestApi);
      assert.isTrue(
          mockRestApi.probePath.calledWith('/Documentation/index.html'));
      assert.equal(docsBaseUrl, '/Documentation');
    });

    test('has doc config', async () => {
      const mockRestApi = {
        probePath: sinon.stub().returns(Promise.resolve(true)),
      };
      const config = {gerrit: {doc_url: 'foobar'}};
      const docsBaseUrl = await getDocsBaseUrl(config, mockRestApi);
      assert.isFalse(mockRestApi.probePath.called);
      assert.equal(docsBaseUrl, 'foobar');
    });

    test('no probe', async () => {
      const mockRestApi = {
        probePath: sinon.stub().returns(Promise.resolve(false)),
      };
      const docsBaseUrl = await getDocsBaseUrl(null, mockRestApi);
      assert.isTrue(
          mockRestApi.probePath.calledWith('/Documentation/index.html'));
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
});
