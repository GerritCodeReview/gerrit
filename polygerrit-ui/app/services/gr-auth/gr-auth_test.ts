/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma';
import {Auth} from './gr-auth_impl';
import {getAppContext} from '../app-context';
import {stubBaseUrl} from '../../test/test-utils';
import {EventEmitterService} from '../gr-event-interface/gr-event-interface';
import {SinonFakeTimers} from 'sinon';
import {AuthRequestInit, DefaultAuthOptions} from './gr-auth';

suite('gr-auth', () => {
  let auth: Auth;
  let eventEmitter: EventEmitterService;

  setup(() => {
    // TODO(poucet): Mock the eventEmitter completely instead of getting it
    // from appContext.
    eventEmitter = getAppContext().eventEmitter;
    auth = new Auth(eventEmitter);
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
      assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
    });

    test('auth-check returns 204', async () => {
      fakeFetch.returns(Promise.resolve({status: 204}));
      const authed = await auth.authCheck();
      assert.isTrue(authed);
      assert.equal(auth.status, Auth.STATUS.AUTHED);
    });

    test('auth-check returns 502', async () => {
      fakeFetch.returns(Promise.resolve({status: 502}));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
    });

    test('auth-check failed', async () => {
      fakeFetch.returns(Promise.reject(new Error('random error')));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, Auth.STATUS.ERROR);
    });
  });

  suite('cache and events behavior', () => {
    let fakeFetch: sinon.SinonStub;
    let clock: SinonFakeTimers;
    setup(() => {
      clock = sinon.useFakeTimers();
      fakeFetch = sinon.stub(window, 'fetch');
    });

    test('cache auth-check result', async () => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
      fakeFetch.returns(Promise.resolve({status: 204}));
      const authed2 = await auth.authCheck();
      assert.isFalse(authed2);
      assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
    });

    test('clearCache should refetch auth-check result', async () => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
      fakeFetch.returns(Promise.resolve({status: 204}));
      auth.clearCache();
      const authed2 = await auth.authCheck();
      assert.isTrue(authed2);
      assert.equal(auth.status, Auth.STATUS.AUTHED);
    });

    test('cache expired on auth-check after certain time', async () => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
      clock.tick(1000 * 10000);
      fakeFetch.returns(Promise.resolve({status: 204}));
      const authed2 = await auth.authCheck();
      assert.isTrue(authed2);
      assert.equal(auth.status, Auth.STATUS.AUTHED);
    });

    test('no cache if auth-check failed', async () => {
      fakeFetch.returns(Promise.reject(new Error('random error')));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, Auth.STATUS.ERROR);
      assert.equal(fakeFetch.callCount, 1);
      await auth.authCheck();
      assert.equal(fakeFetch.callCount, 2);
    });

    test('fire event when switch from authed to unauthed', async () => {
      fakeFetch.returns(Promise.resolve({status: 204}));
      const authed = await auth.authCheck();
      assert.isTrue(authed);
      assert.equal(auth.status, Auth.STATUS.AUTHED);
      clock.tick(1000 * 10000);
      fakeFetch.returns(Promise.resolve({status: 403}));
      const emitStub = sinon.stub(eventEmitter, 'emit');
      const authed2 = await auth.authCheck();
      assert.isFalse(authed2);
      assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
      assert.isTrue(emitStub.called);
    });

    test('fire event when switch from authed to error', async () => {
      fakeFetch.returns(Promise.resolve({status: 204}));
      const authed = await auth.authCheck();
      assert.isTrue(authed);
      assert.equal(auth.status, Auth.STATUS.AUTHED);
      clock.tick(1000 * 10000);
      fakeFetch.returns(Promise.reject(new Error('random error')));
      const emitStub = sinon.stub(eventEmitter, 'emit');
      const authed2 = await auth.authCheck();
      assert.isFalse(authed2);
      assert.isTrue(emitStub.called);
      assert.equal(auth.status, Auth.STATUS.ERROR);
    });

    test('no event from non-authed to other status', async () => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
      clock.tick(1000 * 10000);
      fakeFetch.returns(Promise.resolve({status: 204}));
      const emitStub = sinon.stub(eventEmitter, 'emit');
      const authed2 = await auth.authCheck();
      assert.isTrue(authed2);
      assert.isFalse(emitStub.called);
      assert.equal(auth.status, Auth.STATUS.AUTHED);
    });

    test('no event from non-authed to other status', async () => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      const authed = await auth.authCheck();
      assert.isFalse(authed);
      assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
      clock.tick(1000 * 10000);
      fakeFetch.returns(Promise.reject(new Error('random error')));
      const emitStub = sinon.stub(eventEmitter, 'emit');
      const authed2 = await auth.authCheck();
      assert.isFalse(authed2);
      assert.isFalse(emitStub.called);
      assert.equal(auth.status, Auth.STATUS.ERROR);
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

  suite('cors (access token)', () => {
    let fakeFetch: sinon.SinonStub;

    setup(() => {
      fakeFetch = sinon
        .stub(window, 'fetch')
        .returns(Promise.resolve({...new Response(), ok: true}));
    });

    let getToken: sinon.SinonStub;

    const makeToken = (opt_accessToken?: string) => {
      return {
        access_token: opt_accessToken || 'zbaz',
        expires_at: new Date(Date.now() + 10e8).getTime(),
      };
    };

    setup(() => {
      getToken = sinon.stub();
      getToken.returns(Promise.resolve(makeToken()));
      const defaultOptions: DefaultAuthOptions = {
        credentials: 'include',
      };
      auth.setup(getToken, defaultOptions);
    });

    test('base url support', async () => {
      const baseUrl = 'http://foo';
      stubBaseUrl(baseUrl);
      await auth.fetch(baseUrl + '/url', {bar: 'bar'} as AuthRequestInit);
      const [url] = fakeFetch.lastCall.args;
      assert.equal(url, 'http://foo/a/url?access_token=zbaz');
    });

    test('fetch not signed in', async () => {
      getToken.returns(Promise.resolve());
      await auth.fetch('/url', {bar: 'bar'} as AuthRequestInit);
      const [url, options] = fakeFetch.lastCall.args;
      assert.equal(url, '/url');
      assert.equal(options.bar, 'bar');
      assert.equal(Object.keys(options.headers).length, 0);
    });

    test('fetch signed in', async () => {
      await auth.fetch('/url', {bar: 'bar'} as AuthRequestInit);
      const [url, options] = fakeFetch.lastCall.args;
      assert.equal(url, '/a/url?access_token=zbaz');
      assert.equal(options.bar, 'bar');
    });

    test('getToken calls are cached', async () => {
      await Promise.all([auth.fetch('/url-one'), auth.fetch('/url-two')]);
      assert.equal(getToken.callCount, 1);
    });

    test('getToken refreshes token', async () => {
      const isTokenValidStub = sinon.stub(auth, '_isTokenValid');
      isTokenValidStub
        .onFirstCall()
        .returns(true)
        .onSecondCall()
        .returns(false)
        .onThirdCall()
        .returns(true);
      await auth.fetch('/url-one');
      getToken.returns(Promise.resolve(makeToken('bzzbb')));
      await auth.fetch('/url-two');

      const [[firstUrl], [secondUrl]] = fakeFetch.args;
      assert.equal(firstUrl, '/a/url-one?access_token=zbaz');
      assert.equal(secondUrl, '/a/url-two?access_token=bzzbb');
    });

    test('signed in token error falls back to anonymous', async () => {
      getToken.returns(Promise.resolve('rubbish'));
      await auth.fetch('/url', {bar: 'bar'} as AuthRequestInit);
      const [url, options] = fakeFetch.lastCall.args;
      assert.equal(url, '/url');
      assert.equal(options.bar, 'bar');
    });

    test('_isTokenValid', () => {
      assert.isFalse(auth._isTokenValid(null));
      assert.isFalse(auth._isTokenValid({}));
      assert.isFalse(auth._isTokenValid({access_token: 'foo'}));
      assert.isFalse(
        auth._isTokenValid({
          access_token: 'foo',
          expires_at: `${Date.now() / 1000 - 1}`,
        })
      );
      assert.isTrue(
        auth._isTokenValid({
          access_token: 'foo',
          expires_at: `${Date.now() / 1000 + 1}`,
        })
      );
    });

    test('HTTP PUT with content type', async () => {
      const originalOptions = {
        method: 'PUT',
        headers: new Headers({'Content-Type': 'mail/pigeon'}),
      };
      await auth.fetch('/url', originalOptions);
      assert.isTrue(getToken.called);
      const [url, options] = fakeFetch.lastCall.args;
      assert.include(url, '$ct=mail%2Fpigeon');
      assert.include(url, '$m=PUT');
      assert.include(url, 'access_token=zbaz');
      assert.equal(options.method, 'POST');
      assert.equal(options.headers.get('Content-Type'), 'text/plain');
    });

    test('HTTP PUT without content type', async () => {
      const originalOptions = {
        method: 'PUT',
      };
      await auth.fetch('/url', originalOptions);
      assert.isTrue(getToken.called);
      const [url, options] = fakeFetch.lastCall.args;
      assert.include(url, '$ct=text%2Fplain');
      assert.include(url, '$m=PUT');
      assert.include(url, 'access_token=zbaz');
      assert.equal(options.method, 'POST');
      assert.equal(options.headers.get('Content-Type'), 'text/plain');
    });
  });
});
