/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import '../../test/common-test-setup-karma.js';
import {Auth} from './gr-auth_impl.js';
import {appContext} from '../app-context.js';
import {stubBaseUrl} from '../../test/test-utils.js';

suite('gr-auth', () => {
  let auth;

  setup(() => {
    auth = new Auth(appContext.eventEmitter);
  });

  suite('Auth class methods', () => {
    let fakeFetch;
    setup(() => {
      auth = new Auth(appContext.eventEmitter);
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
    let fakeFetch;
    let clock;
    setup(() => {
      auth = new Auth(appContext.eventEmitter);
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
      const emitStub = sinon.stub(appContext.eventEmitter, 'emit');
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
      const emitStub = sinon.stub(appContext.eventEmitter, 'emit');
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
      const emitStub = sinon.stub(appContext.eventEmitter, 'emit');
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
      const emitStub = sinon.stub(appContext.eventEmitter, 'emit');
      const authed2 = await auth.authCheck();
      assert.isFalse(authed2);
      assert.isFalse(emitStub.called);
      assert.equal(auth.status, Auth.STATUS.ERROR);
    });
  });

  suite('default (xsrf token header)', () => {
    setup(() => {
      sinon.stub(window, 'fetch').returns(Promise.resolve({ok: true}));
    });

    test('GET', async () => {
      await auth.fetch('/url', {bar: 'bar'});
      const [url, options] = fetch.lastCall.args;
      assert.equal(url, '/url');
      assert.equal(options.credentials, 'same-origin');
    });

    test('POST', async () => {
      sinon.stub(auth, '_getCookie')
          .withArgs('XSRF_TOKEN')
          .returns('foobar');
      await auth.fetch('/url', {method: 'POST'});
      const [url, options] = fetch.lastCall.args;
      assert.equal(url, '/url');
      assert.equal(options.credentials, 'same-origin');
      assert.equal(options.headers.get('X-Gerrit-Auth'), 'foobar');
    });
  });

  suite('cors (access token)', () => {
    setup(() => {
      sinon.stub(window, 'fetch').returns(Promise.resolve({ok: true}));
    });

    let getToken;

    const makeToken = opt_accessToken => {
      return {
        access_token: opt_accessToken || 'zbaz',
        expires_at: new Date(Date.now() + 10e8).getTime(),
      };
    };

    setup(() => {
      getToken = sinon.stub();
      getToken.returns(Promise.resolve(makeToken()));
      auth.setup(getToken);
    });

    test('base url support', async () => {
      const baseUrl = 'http://foo';
      stubBaseUrl(baseUrl);
      await auth.fetch(baseUrl + '/url', {bar: 'bar'});
      const [url] = fetch.lastCall.args;
      assert.equal(url, 'http://foo/a/url?access_token=zbaz');
    });

    test('fetch not signed in', async () => {
      getToken.returns(Promise.resolve());
      await auth.fetch('/url', {bar: 'bar'});
      const [url, options] = fetch.lastCall.args;
      assert.equal(url, '/url');
      assert.equal(options.bar, 'bar');
      assert.equal(Object.keys(options.headers).length, 0);
    });

    test('fetch signed in', async () => {
      await auth.fetch('/url', {bar: 'bar'});
      const [url, options] = fetch.lastCall.args;
      assert.equal(url, '/a/url?access_token=zbaz');
      assert.equal(options.bar, 'bar');
    });

    test('getToken calls are cached', async () => {
      await Promise.all([auth.fetch('/url-one'), auth.fetch('/url-two')]);
      assert.equal(getToken.callCount, 1);
    });

    test('getToken refreshes token', async () => {
      sinon.stub(auth, '_isTokenValid');
      auth._isTokenValid
          .onFirstCall().returns(true)
          .onSecondCall()
          .returns(false)
          .onThirdCall()
          .returns(true);
      await auth.fetch('/url-one');
      getToken.returns(Promise.resolve(makeToken('bzzbb')));
      await auth.fetch('/url-two');

      const [[firstUrl], [secondUrl]] = fetch.args;
      assert.equal(firstUrl, '/a/url-one?access_token=zbaz');
      assert.equal(secondUrl, '/a/url-two?access_token=bzzbb');
    });

    test('signed in token error falls back to anonymous', async () => {
      getToken.returns(Promise.resolve('rubbish'));
      await auth.fetch('/url', {bar: 'bar'});
      const [url, options] = fetch.lastCall.args;
      assert.equal(url, '/url');
      assert.equal(options.bar, 'bar');
    });

    test('_isTokenValid', () => {
      assert.isFalse(auth._isTokenValid());
      assert.isFalse(auth._isTokenValid({}));
      assert.isFalse(auth._isTokenValid({access_token: 'foo'}));
      assert.isFalse(auth._isTokenValid({
        access_token: 'foo',
        expires_at: Date.now()/1000 - 1,
      }));
      assert.isTrue(auth._isTokenValid({
        access_token: 'foo',
        expires_at: Date.now()/1000 + 1,
      }));
    });

    test('HTTP PUT with content type', async () => {
      const originalOptions = {
        method: 'PUT',
        headers: new Headers({'Content-Type': 'mail/pigeon'}),
      };
      await auth.fetch('/url', originalOptions);
      assert.isTrue(getToken.called);
      const [url, options] = fetch.lastCall.args;
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
      const [url, options] = fetch.lastCall.args;
      assert.include(url, '$ct=text%2Fplain');
      assert.include(url, '$m=PUT');
      assert.include(url, 'access_token=zbaz');
      assert.equal(options.method, 'POST');
      assert.equal(options.headers.get('Content-Type'), 'text/plain');
    });
  });
});

