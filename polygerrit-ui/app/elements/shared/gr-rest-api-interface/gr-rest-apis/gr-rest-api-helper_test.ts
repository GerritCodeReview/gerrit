/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../../test/common-test-setup';
import {
  SiteBasedCache,
  FetchPromisesCache,
  GrRestApiHelper,
} from './gr-rest-api-helper';
import {
  addListenerForTest,
  assertFails,
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
        assert.equal((err as Error).message, 'No response');
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
        assert.equal((err as Error).message, 'No response');
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

    test('still call errFn when not retried', async () => {
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

    test('still pass through correctly when not retried', async () => {
      const promise = helper.fetch({
        url: '/dummy/url',
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
});
