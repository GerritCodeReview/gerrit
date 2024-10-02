/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../../test/common-test-setup';
import {
  SiteBasedCache,
  FetchPromisesCache,
  GrRestApiHelper,
  JSON_PREFIX,
  readJSONResponsePayload,
  parsePrefixedJSON,
} from './gr-rest-api-helper';
import {
  addListenerForTest,
  assertFails,
  makePrefixedJSON,
  waitEventLoop,
} from '../../../../test/test-utils';
import {FakeScheduler} from '../../../../services/scheduler/fake-scheduler';
import {RetryScheduler} from '../../../../services/scheduler/retry-scheduler';
import {HttpMethod} from '../../../../api/rest-api';
import {SinonFakeTimers} from 'sinon';
import {assert} from '@open-wc/testing';
import {AuthService} from '../../../../services/gr-auth/gr-auth';
import {GrAuthMock} from '../../../../services/gr-auth/gr-auth_mock';
import {ParsedJSON} from '../../../../types/common';
import {getBaseUrl} from '../../../../utils/url-util';

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
  let authService: AuthService;

  setup(() => {
    clock = sinon.useFakeTimers();
    cache = new SiteBasedCache();
    fetchPromisesCache = new FetchPromisesCache();

    originalCanonicalPath = window.CANONICAL_PATH;
    window.CANONICAL_PATH = 'testhelper';

    const testJSON = ')]}\'\n{"hello": "bonjour"}';
    authService = new GrAuthMock();
    authFetchStub = sinon.stub(authService, 'fetch').returns(
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
      authService,
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
    await waitEventLoop();
  }

  async function assertWriteRequest() {
    assert.equal(writeScheduler.scheduled.length, 1);
    await writeScheduler.resolve();
    await waitEventLoop();
  }

  suite('fetch()', () => {
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
      const promise = helper.fetch({
        fetchOptions: {
          method: HttpMethod.GET,
        },
        url: '/dummy/url',
      });
      assert.equal(writeScheduler.scheduled.length, 0);
      await assertReadRequest();
      const res: Response = await promise;
      assert.equal(await res.text(), 'Yay');
    });

    test('PUT are sent to writeScheduler', async () => {
      const promise = helper.fetch({
        fetchOptions: {
          method: HttpMethod.PUT,
        },
        url: '/dummy/url',
      });
      assert.equal(readScheduler.scheduled.length, 0);
      await assertWriteRequest();
      const res: Response = await promise;
      assert.equal(await res.text(), 'Yay');
    });

    test('fetch calls auth fetch and logs', async () => {
      const logStub = sinon.stub(helper, 'logCall');
      const response = new Response(undefined, {status: 404});
      const url = '/my/url';
      const fetchOptions = {method: 'DELETE'};
      authFetchStub.resolves(response);
      const startTime = 123;
      sinon.stub(Date, 'now').returns(startTime);
      helper.fetch({url, fetchOptions, anonymizedUrl: url});

      await assertWriteRequest();
      assert.isTrue(logStub.calledOnce);
      const expectedReq = {
        url: getBaseUrl() + url,
        fetchOptions,
        anonymizedUrl: url,
      };
      assert.deepEqual(logStub.lastCall.args, [
        expectedReq,
        startTime,
        response.status,
      ]);
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

    suite('error handling', () => {
      let serverErrorCalled: boolean;
      let networkErrorCalled: boolean;

      setup(() => {
        serverErrorCalled = false;
        networkErrorCalled = false;
        addListenerForTest(document, 'server-error', () => {
          serverErrorCalled = true;
        });
        addListenerForTest(document, 'network-error', () => {
          networkErrorCalled = true;
        });
      });

      test('network error, promise rejects, event thrown', async () => {
        authFetchStub.rejects(new Error('No response'));
        const promise = helper.fetchJSON({url: '/dummy/url'});
        await assertReadRequest();
        const err = await assertFails(promise);
        assert.equal(
          (err as Error).message,
          'Network error when trying to fetch. Cause: No response'
        );
        await waitEventLoop();
        assert.isTrue(networkErrorCalled);
        assert.isFalse(serverErrorCalled);
      });

      test('network error, promise rejects, errFn called, no event', async () => {
        const errFn = sinon.stub();
        authFetchStub.rejects(new Error('No response'));
        const promise = helper.fetchJSON({
          url: '/dummy/url',
          errFn,
        });
        await assertReadRequest();
        const err = await assertFails(promise);
        assert.equal(
          (err as Error).message,
          'Network error when trying to fetch. Cause: No response'
        );
        await waitEventLoop();
        assert.isTrue(errFn.called);
        assert.isFalse(networkErrorCalled);
        assert.isFalse(serverErrorCalled);
      });

      test('server error, promise resolves undefined, event thrown', async () => {
        authFetchStub.returns(
          Promise.resolve({
            ...new Response(),
            status: 400,
            ok: false,
            text() {
              return Promise.resolve('Nope');
            },
          })
        );
        const promise = helper.fetchJSON({url: '/dummy/url'});
        await assertReadRequest();
        const resp = await promise;
        assert.isUndefined(resp);
        await waitEventLoop();
        assert.isFalse(networkErrorCalled);
        assert.isTrue(serverErrorCalled);
      });

      test('server error, promise resolves undefined, errFn called, no event', async () => {
        authFetchStub.returns(
          Promise.resolve({
            ...new Response(),
            status: 400,
            ok: false,
            text() {
              return Promise.resolve('Nope');
            },
          })
        );
        const errFn = sinon.stub();
        const promise = helper.fetchJSON({url: '/dummy/url', errFn});
        await assertReadRequest();
        const resp = await promise;
        assert.isUndefined(resp);
        await waitEventLoop();
        assert.isTrue(errFn.called);
        assert.isFalse(networkErrorCalled);
        assert.isFalse(serverErrorCalled);
      });

      test('parsing error, promise rejects', async () => {
        authFetchStub.returns(
          Promise.resolve({
            ...new Response(),
            ok: true,
            text() {
              return Promise.resolve('not a prefixed json');
            },
          })
        );
        const errFn = sinon.stub();
        const promise = helper.fetchJSON({url: '/dummy/url', errFn});
        await assertReadRequest();
        await assertFails(promise);
        await waitEventLoop();
        assert.isFalse(errFn.called);
        assert.isFalse(networkErrorCalled);
        assert.isFalse(serverErrorCalled);
      });
    });
  });

  test('cached results', () => {
    let n = 0;
    sinon
      .stub(helper, 'fetchJSON')
      .callsFake(() => Promise.resolve(makeParsedJSON(++n)));
    const promises = [];
    promises.push(helper.fetchCacheJSON({url: '/foo'}));
    promises.push(helper.fetchCacheJSON({url: '/foo'}));
    promises.push(helper.fetchCacheJSON({url: '/foo'}));

    return Promise.all(promises).then(results => {
      assert.deepEqual(results, [
        makeParsedJSON(1),
        makeParsedJSON(1),
        makeParsedJSON(1),
      ]);
      return helper.fetchCacheJSON({url: '/foo'}).then(foo => {
        assert.equal(foo, makeParsedJSON(1));
      });
    });
  });

  test('cached results with param', () => {
    let n = 0;
    sinon
      .stub(helper, 'fetchJSON')
      .callsFake(() => Promise.resolve(makeParsedJSON(++n)));
    const promises = [];
    promises.push(
      helper.fetchCacheJSON({url: '/foo', params: {hello: 'world'}})
    );
    promises.push(helper.fetchCacheJSON({url: '/foo'}));
    promises.push(
      helper.fetchCacheJSON({url: '/foo', params: {hello: 'world'}})
    );

    return Promise.all(promises).then(results => {
      assert.deepEqual(results, [
        makeParsedJSON(1),
        // The url without params is queried again, since it has different url.
        makeParsedJSON(2),
        makeParsedJSON(1),
      ]);
      return helper
        .fetchCacheJSON({url: '/foo', params: {hello: 'world'}})
        .then(foo => {
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
      novaltoo: undefined,
    });
    assert.equal(
      url,
      `${window.CANONICAL_PATH}/path/?sp=hola&gr=guten%20tag&noval&novaltoo`
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

  suite('throwing in errFn', () => {
    function throwInPromise(response?: Response | null, _?: Error) {
      return response?.text().then(text => {
        throw new Error(text);
      });
    }

    function throwImmediately(_1?: Response | null, _2?: Error) {
      throw new Error('Error Callback error');
    }

    setup(() => {
      authFetchStub.returns(
        Promise.resolve({
          ...new Response(),
          status: 400,
          ok: false,
          text() {
            return Promise.resolve('Nope');
          },
        })
      );
    });

    test('errFn with Promise throw cause fetchJSON to reject on error', async () => {
      const promise = helper.fetchJSON({
        url: '/dummy/url',
        errFn: throwInPromise,
      });
      await assertReadRequest();

      const err = await assertFails(promise);
      assert.equal((err as Error).message, 'Nope');
    });

    test('errFn with immediate throw cause fetchJSON to reject on error', async () => {
      const promise = helper.fetchJSON({
        url: '/dummy/url',
        errFn: throwImmediately,
      });
      await assertReadRequest();

      const err = await assertFails(promise);
      assert.equal((err as Error).message, 'Error Callback error');
    });
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

    test('non-retry scheduler errFn is called on 429 error', async () => {
      const errFn = sinon.stub();
      const promise = helper.fetchJSON({
        url: '/dummy/url',
        errFn,
      });
      await assertReadRequest();

      // But we expect the result from the network to return a 429 error when
      // it's no longer being retried.
      await promise;
      assert.isTrue(errFn.called);
    });

    test('non-retry scheduler 429 error is returned without retrying', async () => {
      const promise = helper.fetch({
        url: '/dummy/url',
      });
      await assertReadRequest();

      // With RetryScheduler we retry if the server returns response with 429
      // status.
      // If we are not using RetryScheduler the response with 429 should simply
      // be returned from fetch without retrying.
      const res: Response = await promise;
      assert.equal(res.status, 429);
    });

    test('With RetryScheduler 429 errors are retried', async () => {
      helper = new GrRestApiHelper(
        cache,
        authService,
        fetchPromisesCache,
        new RetryScheduler<Response>(readScheduler, 1, 50),
        writeScheduler
      );
      const promise = helper.fetch({
        url: '/dummy/url',
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
      await waitEventLoop();
      // We expect a retry.
      await assertReadRequest();
      const res: Response = await promise;
      assert.equal(await res.text(), 'Yay');
    });
  });

  suite('reading responses', () => {
    test('readResponsePayload', async () => {
      const mockObject = {foo: 'bar', baz: 'foo'} as unknown as ParsedJSON;
      const serial = makePrefixedJSON(mockObject);
      const response = new Response(serial);
      const payload = await readJSONResponsePayload(response);
      assert.deepEqual(payload.parsed, mockObject);
      assert.equal(payload.raw, serial);
    });

    test('parsePrefixedJSON', () => {
      const obj = {x: 3, y: {z: 4}, w: 23} as unknown as ParsedJSON;
      const serial = JSON_PREFIX + JSON.stringify(obj);
      const result = parsePrefixedJSON(serial);
      assert.deepEqual(result, obj);
    });

    test('parsing error', async () => {
      const response = new Response('[');
      const err: Error = await assertFails(readJSONResponsePayload(response));
      assert.equal(
        err.message,
        'Response payload is not prefixed json. Payload: ['
      );
    });
  });

  test('logCall only reports requests with anonymized URLs', async () => {
    sinon.stub(Date, 'now').returns(200);
    const handler = sinon.stub();
    addListenerForTest(document, 'gr-rpc-log', handler);

    helper.logCall({url: 'url'}, 100, 200);
    assert.isFalse(handler.called);

    helper.logCall({url: 'url', anonymizedUrl: 'not url'}, 100, 200);
    await waitEventLoop();
    assert.isTrue(handler.calledOnce);
  });
});
