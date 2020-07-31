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
    auth = appContext.authService;
  });

  suite('Auth class methods', () => {
    let fakeFetch;
    setup(() => {
      auth = new Auth(appContext.eventEmitter);
      fakeFetch = sinon.stub(window, 'fetch');
    });

    test('auth-check returns 403', done => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      auth.authCheck().then(authed => {
        assert.isFalse(authed);
        assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
        done();
      });
    });

    test('auth-check returns 204', done => {
      fakeFetch.returns(Promise.resolve({status: 204}));
      auth.authCheck().then(authed => {
        assert.isTrue(authed);
        assert.equal(auth.status, Auth.STATUS.AUTHED);
        done();
      });
    });

    test('auth-check returns 502', done => {
      fakeFetch.returns(Promise.resolve({status: 502}));
      auth.authCheck().then(authed => {
        assert.isFalse(authed);
        assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
        done();
      });
    });

    test('auth-check failed', done => {
      fakeFetch.returns(Promise.reject(new Error('random error')));
      auth.authCheck().then(authed => {
        assert.isFalse(authed);
        assert.equal(auth.status, Auth.STATUS.ERROR);
        done();
      });
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

    test('cache auth-check result', done => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      auth.authCheck().then(authed => {
        assert.isFalse(authed);
        assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
        fakeFetch.returns(Promise.resolve({status: 204}));
        auth.authCheck().then(authed2 => {
          assert.isFalse(authed);
          assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
          done();
        });
      });
    });

    test('clearCache should refetch auth-check result', done => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      auth.authCheck().then(authed => {
        assert.isFalse(authed);
        assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
        fakeFetch.returns(Promise.resolve({status: 204}));
        auth.clearCache();
        auth.authCheck().then(authed2 => {
          assert.isTrue(authed2);
          assert.equal(auth.status, Auth.STATUS.AUTHED);
          done();
        });
      });
    });

    test('cache expired on auth-check after certain time', done => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      auth.authCheck().then(authed => {
        assert.isFalse(authed);
        assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
        clock.tick(1000 * 10000);
        fakeFetch.returns(Promise.resolve({status: 204}));
        auth.authCheck().then(authed2 => {
          assert.isTrue(authed2);
          assert.equal(auth.status, Auth.STATUS.AUTHED);
          done();
        });
      });
    });

    test('no cache if auth-check failed', done => {
      fakeFetch.returns(Promise.reject(new Error('random error')));
      auth.authCheck().then(authed => {
        assert.isFalse(authed);
        assert.equal(auth.status, Auth.STATUS.ERROR);
        assert.equal(fakeFetch.callCount, 1);
        auth.authCheck().then(() => {
          assert.equal(fakeFetch.callCount, 2);
          done();
        });
      });
    });

    test('fire event when switch from authed to unauthed', done => {
      fakeFetch.returns(Promise.resolve({status: 204}));
      auth.authCheck().then(authed => {
        assert.isTrue(authed);
        assert.equal(auth.status, Auth.STATUS.AUTHED);
        clock.tick(1000 * 10000);
        fakeFetch.returns(Promise.resolve({status: 403}));
        const emitStub = sinon.stub();
        appContext.eventEmitter.emit = emitStub;
        auth.authCheck().then(authed2 => {
          assert.isFalse(authed2);
          assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
          assert.isTrue(emitStub.called);
          done();
        });
      });
    });

    test('fire event when switch from authed to error', done => {
      fakeFetch.returns(Promise.resolve({status: 204}));
      auth.authCheck().then(authed => {
        assert.isTrue(authed);
        assert.equal(auth.status, Auth.STATUS.AUTHED);
        clock.tick(1000 * 10000);
        fakeFetch.returns(Promise.reject(new Error('random error')));
        const emitStub = sinon.stub();
        appContext.eventEmitter.emit = emitStub;
        auth.authCheck().then(authed2 => {
          assert.isFalse(authed2);
          assert.isTrue(emitStub.called);
          assert.equal(auth.status, Auth.STATUS.ERROR);
          done();
        });
      });
    });

    test('no event from non-authed to other status', done => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      auth.authCheck().then(authed => {
        assert.isFalse(authed);
        assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
        clock.tick(1000 * 10000);
        fakeFetch.returns(Promise.resolve({status: 204}));
        const emitStub = sinon.stub();
        appContext.eventEmitter.emit = emitStub;
        auth.authCheck().then(authed2 => {
          assert.isTrue(authed2);
          assert.isFalse(emitStub.called);
          assert.equal(auth.status, Auth.STATUS.AUTHED);
          done();
        });
      });
    });

    test('no event from non-authed to other status', done => {
      fakeFetch.returns(Promise.resolve({status: 403}));
      auth.authCheck().then(authed => {
        assert.isFalse(authed);
        assert.equal(auth.status, Auth.STATUS.NOT_AUTHED);
        clock.tick(1000 * 10000);
        fakeFetch.returns(Promise.reject(new Error('random error')));
        const emitStub = sinon.stub();
        appContext.eventEmitter.emit = emitStub;
        auth.authCheck().then(authed2 => {
          assert.isFalse(authed2);
          assert.isFalse(emitStub.called);
          assert.equal(auth.status, Auth.STATUS.ERROR);
          done();
        });
      });
    });
  });

  suite('default (xsrf token header)', () => {
    setup(() => {
      sinon.stub(window, 'fetch').returns(Promise.resolve({ok: true}));
    });

    test('GET', done => {
      auth.fetch('/url', {bar: 'bar'}).then(() => {
        const [url, options] = fetch.lastCall.args;
        assert.equal(url, '/url');
        assert.equal(options.credentials, 'same-origin');
        done();
      });
    });

    test('POST', done => {
      sinon.stub(auth, '_getCookie')
          .withArgs('XSRF_TOKEN')
          .returns('foobar');
      auth.fetch('/url', {method: 'POST'}).then(() => {
        const [url, options] = fetch.lastCall.args;
        assert.equal(url, '/url');
        assert.equal(options.credentials, 'same-origin');
        assert.equal(options.headers.get('X-Gerrit-Auth'), 'foobar');
        done();
      });
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

    test('base url support', done => {
      const baseUrl = 'http://foo';
      stubBaseUrl(baseUrl);
      auth.fetch(baseUrl + '/url', {bar: 'bar'}).then(() => {
        const [url] = fetch.lastCall.args;
        assert.equal(url, 'http://foo/a/url?access_token=zbaz');
        done();
      });
    });

    test('fetch not signed in', done => {
      getToken.returns(Promise.resolve());
      auth.fetch('/url', {bar: 'bar'}).then(() => {
        const [url, options] = fetch.lastCall.args;
        assert.equal(url, '/url');
        assert.equal(options.bar, 'bar');
        assert.equal(Object.keys(options.headers).length, 0);
        done();
      });
    });

    test('fetch signed in', done => {
      auth.fetch('/url', {bar: 'bar'}).then(() => {
        const [url, options] = fetch.lastCall.args;
        assert.equal(url, '/a/url?access_token=zbaz');
        assert.equal(options.bar, 'bar');
        done();
      });
    });

    test('getToken calls are cached', done => {
      Promise.all([
        auth.fetch('/url-one'), auth.fetch('/url-two')]).then(() => {
        assert.equal(getToken.callCount, 1);
        done();
      });
    });

    test('getToken refreshes token', done => {
      sinon.stub(auth, '_isTokenValid');
      auth._isTokenValid
          .onFirstCall().returns(true)
          .onSecondCall()
          .returns(false)
          .onThirdCall()
          .returns(true);
      auth.fetch('/url-one')
          .then(() => {
            getToken.returns(Promise.resolve(makeToken('bzzbb')));
            return auth.fetch('/url-two');
          })
          .then(() => {
            const [[firstUrl], [secondUrl]] = fetch.args;
            assert.equal(firstUrl, '/a/url-one?access_token=zbaz');
            assert.equal(secondUrl, '/a/url-two?access_token=bzzbb');
            done();
          });
    });

    test('signed in token error falls back to anonymous', done => {
      getToken.returns(Promise.resolve('rubbish'));
      auth.fetch('/url', {bar: 'bar'}).then(() => {
        const [url, options] = fetch.lastCall.args;
        assert.equal(url, '/url');
        assert.equal(options.bar, 'bar');
        done();
      });
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

    test('HTTP PUT with content type', done => {
      const originalOptions = {
        method: 'PUT',
        headers: new Headers({'Content-Type': 'mail/pigeon'}),
      };
      auth.fetch('/url', originalOptions).then(() => {
        assert.isTrue(getToken.called);
        const [url, options] = fetch.lastCall.args;
        assert.include(url, '$ct=mail%2Fpigeon');
        assert.include(url, '$m=PUT');
        assert.include(url, 'access_token=zbaz');
        assert.equal(options.method, 'POST');
        assert.equal(options.headers.get('Content-Type'), 'text/plain');
        done();
      });
    });

    test('HTTP PUT without content type', done => {
      const originalOptions = {
        method: 'PUT',
      };
      auth.fetch('/url', originalOptions).then(() => {
        assert.isTrue(getToken.called);
        const [url, options] = fetch.lastCall.args;
        assert.include(url, '$ct=text%2Fplain');
        assert.include(url, '$m=PUT');
        assert.include(url, 'access_token=zbaz');
        assert.equal(options.method, 'POST');
        assert.equal(options.headers.get('Content-Type'), 'text/plain');
        done();
      });
    });
  });
});

