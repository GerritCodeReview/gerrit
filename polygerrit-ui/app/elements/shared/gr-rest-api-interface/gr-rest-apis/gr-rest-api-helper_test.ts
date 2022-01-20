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

import '../../../../test/common-test-setup-karma';
import {SiteBasedCache, FetchPromisesCache, GrRestApiHelper} from './gr-rest-api-helper';
import {getAppContext} from '../../../../services/app-context';
import {stubAuth} from '../../../../test/test-utils';
import {BaseScheduler} from '../../../../services/scheduler/scheduler';
import {ParsedJSON} from '../../../../types/common';

function makeParsedJSON(num: number): ParsedJSON {
  return num as unknown as ParsedJSON;
}

suite('gr-rest-api-helper tests', () => {
  let helper: GrRestApiHelper;

  let cache: SiteBasedCache;
  let fetchPromisesCache: FetchPromisesCache;
  let originalCanonicalPath: string | undefined;
  let authFetchStub: sinon.SinonStub;

  setup(() => {
    cache = new SiteBasedCache();
    fetchPromisesCache = new FetchPromisesCache();

    originalCanonicalPath = window.CANONICAL_PATH;
    window.CANONICAL_PATH = 'testhelper';

    const testJSON = ')]}\'\n{"hello": "bonjour"}';
    authFetchStub = stubAuth('fetch').returns(Promise.resolve({
      ...new Response(),
      ok: true,
      text() {
        return Promise.resolve(testJSON);
      },
    }));

    helper = new GrRestApiHelper(cache, getAppContext().authService,
        fetchPromisesCache,
        new BaseScheduler(), new BaseScheduler());
  });

  teardown(() => {
    window.CANONICAL_PATH = originalCanonicalPath;
  });

  suite('fetchJSON()', () => {
    test('Sets header to accept application/json', () => {
      helper.fetchJSON({url: '/dummy/url'});
      assert.isTrue(authFetchStub.called);
      assert.equal(authFetchStub.lastCall.args[1].headers.get('Accept'),
          'application/json');
    });

    test('Use header option accept when provided', () => {
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
        assert.deepEqual(obj, {hello: 'bonjour'} as unknown as ParsedJSON);
      })
  );

  test('cached results', () => {
    let n = 0;
    sinon.stub(helper, 'fetchJSON').callsFake(
      () => Promise.resolve(makeParsedJSON(++n))
    );
    const promises = [];
    promises.push(helper.fetchCacheURL({url: '/foo'}));
    promises.push(helper.fetchCacheURL({url: '/foo'}));
    promises.push(helper.fetchCacheURL({url: '/foo'}));

    return Promise.all(promises).then(results => {
      assert.deepEqual(results, [
        makeParsedJSON(1),
        makeParsedJSON(1),
        makeParsedJSON(1)]);
      return helper.fetchCacheURL({url: '/foo'}).then(foo => {
        assert.equal(foo, 1 as unknown as ParsedJSON);
      });
    });
  });

  test('cache invalidation', async () => {
    cache.set('/foo/bar', makeParsedJSON(1));
    cache.set('/bar', makeParsedJSON(2));
    fetchPromisesCache.set(
      '/foo/bar',
      Promise.resolve(makeParsedJSON(3)));
    fetchPromisesCache.set('/bar', Promise.resolve(makeParsedJSON(4)));
    helper.invalidateFetchPromisesPrefix('/foo/');
    assert.isFalse(cache.has('/foo/bar'));
    assert.isTrue(cache.has('/bar'));
    assert.isUndefined(fetchPromisesCache.get('/foo/bar'));
    assert.strictEqual(makeParsedJSON(4), await fetchPromisesCache.get('/bar'));
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
    authFetchStub.returns(Promise.resolve({
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

