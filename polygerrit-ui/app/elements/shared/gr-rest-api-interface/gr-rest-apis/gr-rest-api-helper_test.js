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

import '../../../../test/common-test-setup-karma.js';
import {SiteBasedCache} from './gr-rest-api-helper.js';
import {FetchPromisesCache, GrRestApiHelper} from './gr-rest-api-helper.js';
import {appContext} from '../../../../services/app-context.js';

suite('gr-rest-api-helper tests', () => {
  let helper;

  let cache;
  let fetchPromisesCache;
  let originalCanonicalPath;

  setup(() => {
    cache = new SiteBasedCache();
    fetchPromisesCache = new FetchPromisesCache();

    originalCanonicalPath = window.CANONICAL_PATH;
    window.CANONICAL_PATH = 'testhelper';

    const mockRestApiInterface = {
      fire: sinon.stub(),
    };

    const testJSON = ')]}\'\n{"hello": "bonjour"}';
    sinon.stub(window, 'fetch').returns(Promise.resolve({
      ok: true,
      text() {
        return Promise.resolve(testJSON);
      },
    }));

    helper = new GrRestApiHelper(cache, appContext.authService,
        fetchPromisesCache, mockRestApiInterface);
  });

  teardown(() => {
    window.CANONICAL_PATH = originalCanonicalPath;
  });

  suite('fetchJSON()', () => {
    test('Sets header to accept application/json', () => {
      const authFetchStub = sinon.stub(helper._auth, 'fetch')
          .returns(Promise.resolve());
      helper.fetchJSON({url: '/dummy/url'});
      assert.isTrue(authFetchStub.called);
      assert.equal(authFetchStub.lastCall.args[1].headers.get('Accept'),
          'application/json');
    });

    test('Use header option accept when provided', () => {
      const authFetchStub = sinon.stub(helper._auth, 'fetch')
          .returns(Promise.resolve());
      const headers = new Headers();
      headers.append('Accept', '*/*');
      const fetchOptions = {headers};
      helper.fetchJSON({url: '/dummy/url', fetchOptions});
      assert.isTrue(authFetchStub.called);
      assert.equal(authFetchStub.lastCall.args[1].headers.get('Accept'),
          '*/*');
    });
  });

  test('JSON prefix is properly removed',
      () => helper.fetchJSON({url: '/dummy/url'}).then(obj => {
        assert.deepEqual(obj, {hello: 'bonjour'});
      })
  );

  test('cached results', () => {
    let n = 0;
    sinon.stub(helper, 'fetchJSON').callsFake(() => Promise.resolve(++n));
    const promises = [];
    promises.push(helper.fetchCacheURL('/foo'));
    promises.push(helper.fetchCacheURL('/foo'));
    promises.push(helper.fetchCacheURL('/foo'));

    return Promise.all(promises).then(results => {
      assert.deepEqual(results, [1, 1, 1]);
      return helper.fetchCacheURL('/foo').then(foo => {
        assert.equal(foo, 1);
      });
    });
  });

  test('cached promise', () => {
    const promise = Promise.reject(new Error('foo'));
    cache.set('/foo', promise);
    return helper.fetchCacheURL({url: '/foo'}).catch(p => {
      assert.equal(p.message, 'foo');
    });
  });

  test('cache invalidation', () => {
    cache.set('/foo/bar', 1);
    cache.set('/bar', 2);
    fetchPromisesCache.set('/foo/bar', 3);
    fetchPromisesCache.set('/bar', 4);
    helper.invalidateFetchPromisesPrefix('/foo/');
    assert.isFalse(cache.has('/foo/bar'));
    assert.isTrue(cache.has('/bar'));
    assert.isUndefined(fetchPromisesCache.get('/foo/bar'));
    assert.strictEqual(4, fetchPromisesCache.get('/bar'));
  });

  test('params are properly encoded', () => {
    let url = helper.urlWithParams('/path/', {
      sp: 'hola',
      gr: 'guten tag',
      noval: null,
    });
    assert.equal(url,
        window.CANONICAL_PATH + '/path/?sp=hola&gr=guten%20tag&noval');

    url = helper.urlWithParams('/path/', {
      sp: 'hola',
      en: ['hey', 'hi'],
    });
    assert.equal(url, window.CANONICAL_PATH + '/path/?sp=hola&en=hey&en=hi');

    // Order must be maintained with array params.
    url = helper.urlWithParams('/path/', {
      l: ['c', 'b', 'a'],
    });
    assert.equal(url, window.CANONICAL_PATH + '/path/?l=c&l=b&l=a');
  });

  test('request callbacks can be canceled', () => {
    let cancelCalled = false;
    window.fetch.returns(Promise.resolve({
      body: {
        cancel() { cancelCalled = true; },
      },
    }));
    const cancelCondition = () => true;
    return helper.fetchJSON({url: '/dummy/url', cancelCondition}).then(obj => {
      assert.isUndefined(obj);
      assert.isTrue(cancelCalled);
    });
  });
});

