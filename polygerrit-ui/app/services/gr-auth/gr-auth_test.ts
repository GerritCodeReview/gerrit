/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../test/common-test-setup';
import {Auth, AuthStatus} from './gr-auth_impl';
import {SinonFakeTimers} from 'sinon';
import {assert} from '@open-wc/testing';
import {AuthRequestInit} from '../../types/types';

suite('gr-auth', () => {
  let auth: Auth;

  setup(() => {
    auth = new Auth();
  });

  suite('Auth class methods', () => {
    let fakeFetch: sinon.SinonStub;
    setup(() => {
      fakeFetch = sinon.stub(window, 'fetch');
    });

    test('auth-check returns 403', async () => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, AuthStatus.NOT_AUTHED);
    });

    test('auth-check returns 204', async () => {
      fakeFetch.returns(Promise.resolve({status: 204}));
      const authed = await auth.authCheck();
      assert.isTrue(authed);
      assert.equal(auth.status, AuthStatus.AUTHED);
    });

    test('auth-check returns 502', async () => {
      fakeFetch.returns(Promise.resolve({status: 502}));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, AuthStatus.NOT_AUTHED);
    });

    test('auth-check failed', async () => {
      fakeFetch.returns(Promise.reject(new Error('random error')));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, AuthStatus.ERROR);
    });
  });

  suite('cache and events behavior', () => {
    let fakeFetch: sinon.SinonStub;
    let clock: SinonFakeTimers;
    setup(() => {
      clock = sinon.useFakeTimers({shouldClearNativeTimers: true});
      fakeFetch = sinon.stub(window, 'fetch');
    });

    test('cache auth-check result', async () => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, AuthStatus.NOT_AUTHED);
      fakeFetch.returns(Promise.resolve({status: 204}));
      const authed2 = await auth.authCheck();
      assert.isFalse(authed2);
      assert.equal(auth.status, AuthStatus.NOT_AUTHED);
    });

    test('clearCache should refetch auth-check result', async () => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, AuthStatus.NOT_AUTHED);
      fakeFetch.returns(Promise.resolve({status: 204}));
      auth.clearCache();
      const authed2 = await auth.authCheck();
      assert.isTrue(authed2);
      assert.equal(auth.status, AuthStatus.AUTHED);
    });

    test('cache expired on auth-check after certain time', async () => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, AuthStatus.NOT_AUTHED);
      clock.tick(1000 * 10000);
      fakeFetch.returns(Promise.resolve({status: 204}));
      const authed2 = await auth.authCheck();
      assert.isTrue(authed2);
      assert.equal(auth.status, AuthStatus.AUTHED);
    });

    test('no cache if auth-check failed', async () => {
      fakeFetch.returns(Promise.reject(new Error('random error')));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, AuthStatus.ERROR);
      assert.equal(fakeFetch.callCount, 1);
      await auth.authCheck();
      assert.equal(fakeFetch.callCount, 2);
    });

    test('fire event when switch from authed to unauthed', async () => {
      fakeFetch.returns(Promise.resolve({status: 204}));
      const authed = await auth.authCheck();
      assert.isTrue(authed);
      assert.equal(auth.status, AuthStatus.AUTHED);
      clock.tick(1000 * 10000);
      fakeFetch.returns(Promise.resolve({status: 403}));
      const emitStub = sinon.stub();
      document.addEventListener('auth-error', emitStub);
      const authed2 = await auth.authCheck();
      assert.isFalse(authed2);
      assert.equal(auth.status, AuthStatus.NOT_AUTHED);
      assert.isTrue(emitStub.called);
      document.removeEventListener('auth-error', emitStub);
    });

    test('fire event when switch from authed to error', async () => {
      fakeFetch.returns(Promise.resolve({status: 204}));
      const authed = await auth.authCheck();
      assert.isTrue(authed);
      assert.equal(auth.status, AuthStatus.AUTHED);
      clock.tick(1000 * 10000);
      fakeFetch.returns(Promise.reject(new Error('random error')));
      const emitStub = sinon.stub();
      document.addEventListener('auth-error', emitStub);
      const authed2 = await auth.authCheck();
      assert.isFalse(authed2);
      assert.isTrue(emitStub.called);
      assert.equal(auth.status, AuthStatus.ERROR);
      document.removeEventListener('auth-error', emitStub);
    });

    test('no event from non-authed to other status', async () => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, AuthStatus.NOT_AUTHED);
      clock.tick(1000 * 10000);
      fakeFetch.returns(Promise.resolve({status: 204}));
      const emitStub = sinon.stub();
      document.addEventListener('auth-error', emitStub);
      const authed2 = await auth.authCheck();
      assert.isTrue(authed2);
      assert.isFalse(emitStub.called);
      assert.equal(auth.status, AuthStatus.AUTHED);
      document.removeEventListener('auth-error', emitStub);
    });

    test('no event from non-authed to other status', async () => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, AuthStatus.NOT_AUTHED);
      clock.tick(1000 * 10000);
      fakeFetch.returns(Promise.reject(new Error('random error')));
      const emitStub = sinon.stub();
      document.addEventListener('auth-error', emitStub);
      const authed2 = await auth.authCheck();
      assert.isFalse(authed2);
      assert.isFalse(emitStub.called);
      assert.equal(auth.status, AuthStatus.ERROR);
      document.removeEventListener('auth-error', emitStub);
    });
  });

  suite('default (xsrf token header)', () => {
    let fakeFetch: sinon.SinonStub;

    setup(() => {
      fakeFetch = sinon
        .stub(window, 'fetch')
        .returns(Promise.resolve({...new Response(), ok: true}));
    });

    test('GET', async () => {
      await auth.fetch('/url', {bar: 'bar'} as AuthRequestInit);
      const [url, options] = fakeFetch.lastCall.args;
      assert.equal(url, '/url');
      assert.equal(options.credentials, 'same-origin');
    });

    test('POST', async () => {
      sinon.stub(auth, '_getCookie').withArgs('XSRF_TOKEN').returns('foobar');
      await auth.fetch('/url', {method: 'POST'});
      const [url, options] = fakeFetch.lastCall.args;
      assert.equal(url, '/url');
      assert.equal(options.credentials, 'same-origin');
      assert.equal(options.headers.get('X-Gerrit-Auth'), 'foobar');
    });
  });
});
