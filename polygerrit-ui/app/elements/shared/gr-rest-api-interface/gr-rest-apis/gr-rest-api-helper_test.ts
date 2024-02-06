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
import {waitEventLoop} from '../../../../test/test-utils';
import {FakeScheduler} from '../../../../services/scheduler/fake-scheduler';
import {RetryScheduler} from '../../../../services/scheduler/retry-scheduler';
import {HttpMethod} from '../../../../api/rest-api';
import {SinonFakeTimers} from 'sinon';
import {assert} from '@open-wc/testing';
import {AuthService} from '../../../../services/gr-auth/gr-auth';
import {GrAuthMock} from '../../../../services/gr-auth/gr-auth_mock';

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
