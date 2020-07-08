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
import {getBaseUrl, getDocsBaseUrl, _testOnly_clearDocsBaseUrlCache} from './url-util.js';

suite('url-util tests', () => {
  suite('getBaseUrl tests', () => {
    let originialCanonicalPath;

    suiteSetup(() => {
      originialCanonicalPath = window.CANONICAL_PATH;
      window.CANONICAL_PATH = '/r';
    });

    suiteTeardown(() => {
      window.CANONICAL_PATH = originialCanonicalPath;
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
});
