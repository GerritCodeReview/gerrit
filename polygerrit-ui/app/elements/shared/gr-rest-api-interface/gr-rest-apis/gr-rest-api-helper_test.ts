/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../../test/common-test-setup-karma';
import {
  SiteBasedCache,
  FetchPromisesCache,
  GrRestApiHelper,
} from './gr-rest-api-helper';
import {getAppContext} from '../../../../services/app-context';
import {stubAuth} from '../../../../test/test-utils';
import {FakeScheduler} from '../../../../services/scheduler/fake-scheduler';
import {RetryScheduler} from '../../../../services/scheduler/retry-scheduler';
import {ParsedJSON} from '../../../../types/common';
import {HttpMethod} from '../../../../api/rest-api';
import {SinonFakeTimers} from 'sinon';

function makeParsedJSON<T>(val: T): ParsedJSON {
  return val as unknown as ParsedJSON;
}

suite('gr-rest-api-helper tests', () => {
  let clock: SinonFakeTimers;
  let helper: GrRestApiHelper;

  let cache: SiteBasedCache;
  let fetchPromisesCache: FetchPromisesCache;
  let originalCanonicalPath: string | undefined;
  let authFetchStub: sinon.SinonStub;
  let readScheduler: FakeScheduler<Response>;
  let writeScheduler: FakeScheduler<Response>;

  setup(() => {
    clock = sinon.useFakeTimers();
    cache = new SiteBasedCache();
    fetchPromisesCache = new FetchPromisesCache();

    originalCanonicalPath = window.CANONICAL_PATH;
    window.CANONICAL_PATH = 'testhelper';

    const testJSON = ')]}\'\n{"hello": "bonjour"}';
    authFetchStub = stubAuth('fetch').returns(
      Promise.resolve({
        ...new Response(),
        ok: true,
        text() {
          return Promise.resolve(testJSON);
        },
      })
    );

    readScheduler = new FakeScheduler<Response>();
    writeScheduler = new FakeScheduler<Response>();

    helper = new GrRestApiHelper(
      cache,
      getAppContext().authService,
      fetchPromisesCache,
      readScheduler,
      writeScheduler
    );
  });

  teardown(() => {
    window.CANONICAL_PATH = originalCanonicalPath;
  });

  async function assertReadRequest() {
    assert.equal(readScheduler.scheduled.length, 1);
    await readScheduler.resolve();
    await flush();
  }

  async function assertWriteRequest() {
    assert.equal(writeScheduler.scheduled.length, 1);
    await writeScheduler.resolve();
    await flush();
  }

  suite('send()', () => {
    setup(() => {
      authFetchStub.returns(
        Promise.resolve({
          ...new Response(),
          ok: true,
          text() {
            return Promise.resolve('Yay');
          },
        })
      );
    });

    test('GET are sent to readScheduler', async () => {
      const promise = helper.send({
        method: HttpMethod.GET,
        url: '/dummy/url',
        parseResponse: false,
      });
      assert.equal(writeScheduler.scheduled.length, 0);
      await assertReadRequest();
      const res: Response = await promise;
      assert.equal(await res.text(), 'Yay');
    });

    test('PUT are sent to writeScheduler', async () => {
      const promise = helper.send({
        method: HttpMethod.PUT,
        url: '/dummy/url',
        parseResponse: false,
      });
      assert.equal(readScheduler.scheduled.length, 0);
      await assertWriteRequest();
      const res: Response = await promise;
      assert.equal(await res.text(), 'Yay');
    });
  });

  suite('fetchJSON()', () => {
    test('Sets header to accept application/json', async () => {
      helper.fetchJSON({url: '/dummy/url'});
      assert.isFalse(authFetchStub.called);
      await assertReadRequest();
      assert.isTrue(authFetchStub.called);
      assert.equal(
        authFetchStub.lastCall.args[1].headers.get('Accept'),
        'application/json'
      );
    });

    test('Use header option accept when provided', async () => {
      const headers = new Headers();
      headers.append('Accept', '*/*');
      const fetchOptions = {headers};
      helper.fetchJSON({url: '/dummy/url', fetchOptions});
      assert.isFalse(authFetchStub.called);
      await assertReadRequest();
      assert.isTrue(authFetchStub.called);
      assert.equal(authFetchStub.lastCall.args[1].headers.get('Accept'), '*/*');
    });

    test('JSON prefix is properly removed', async () => {
      const promise = helper.fetchJSON({url: '/dummy/url'});
      await assertReadRequest();
      const obj = await promise;
      assert.deepEqual(obj, makeParsedJSON({hello: 'bonjour'}));
    });
  });

  test('cached results', () => {
    let n = 0;
    sinon
      .stub(helper, 'fetchJSON')
      .callsFake(() => Promise.resolve(makeParsedJSON(++n)));
    const promises = [];
    promises.push(helper.fetchCacheURL({url: '/foo'}));
    promises.push(helper.fetchCacheURL({url: '/foo'}));
    promises.push(helper.fetchCacheURL({url: '/foo'}));

    return Promise.all(promises).then(results => {
      assert.deepEqual(results, [
        makeParsedJSON(1),
        makeParsedJSON(1),
        makeParsedJSON(1),
      ]);
      return helper.fetchCacheURL({url: '/foo'}).then(foo => {
        assert.equal(foo, makeParsedJSON(1));
      });
    });
  });

  test('cache invalidation', async () => {
    cache.set('/foo/bar', makeParsedJSON(1));
    cache.set('/bar', makeParsedJSON(2));
    fetchPromisesCache.set('/foo/bar', Promise.resolve(makeParsedJSON(3)));
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
    assert.equal(
      url,
      `${window.CANONICAL_PATH}/path/?sp=hola&gr=guten%20tag&noval`
    );

    url = helper.urlWithParams('/path/', {
      sp: 'hola',
      en: ['hey', 'hi'],
    });
    assert.equal(url, `${window.CANONICAL_PATH}/path/?sp=hola&en=hey&en=hi`);

    // Order must be maintained with array params.
    url = helper.urlWithParams('/path/', {
      l: ['c', 'b', 'a'],
    });
    assert.equal(url, `${window.CANONICAL_PATH}/path/?l=c&l=b&l=a`);
  });

  test('request callbacks can be canceled', async () => {
    let cancelCalled = false;
    authFetchStub.returns(
      Promise.resolve({
        body: {
          cancel() {
            cancelCalled = true;
          },
        },
      })
    );
    const cancelCondition = () => true;
    const promise = helper.fetchJSON({url: '/dummy/url', cancelCondition});
    await assertReadRequest();
    const obj = await promise;
    assert.isUndefined(obj);
    assert.isTrue(cancelCalled);
  });

  suite('429 errors', () => {
    setup(() => {
      authFetchStub.returns(
        Promise.resolve({
          ...new Response(),
          status: 429,
          ok: false,
        })
      );
    });

    test('still call errFn when not retried', async () => {
      const errFn = sinon.stub();
      const promise = helper.send({
        method: HttpMethod.GET,
        url: '/dummy/url',
        parseResponse: false,
        errFn,
      });
      await assertReadRequest();

      // But we expect the result from the network to return a 429 error when
      // it's no longer being retried.
      await promise;
      assert.isTrue(errFn.called);
    });

    test('still pass through correctly when not retried', async () => {
      const promise = helper.send({
        method: HttpMethod.GET,
        url: '/dummy/url',
        parseResponse: false,
      });
      await assertReadRequest();

      // But we expect the result from the network to return a 429 error when
      // it's no longer being retried.
      const res: Response = await promise;
      assert.equal(res.status, 429);
    });

    test('are retried', async () => {
      helper = new GrRestApiHelper(
        cache,
        getAppContext().authService,
        fetchPromisesCache,
        new RetryScheduler<Response>(readScheduler, 1, 50),
        writeScheduler
      );
      const promise = helper.send({
        method: HttpMethod.GET,
        url: '/dummy/url',
        parseResponse: false,
      });
      await assertReadRequest();
      authFetchStub.returns(
        Promise.resolve({
          ...new Response(),
          ok: true,
          text() {
            return Promise.resolve('Yay');
          },
        })
      );
      // Flush the retry scheduler
      clock.tick(50);
      await flush();
      // We expect a retry.
      await assertReadRequest();
      const res: Response = await promise;
      assert.equal(await res.text(), 'Yay');
    });
  });
});
