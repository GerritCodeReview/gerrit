/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

import '../../../test/common-test-setup-karma';
import './gr-error-manager';
import {
  constructServerErrorMsg,
  GrErrorManager,
  __testOnly_ErrorType,
} from './gr-error-manager';
import {stubAuth, stubReporting, stubRestApi} from '../../../test/test-utils';
import {appContext} from '../../../services/app-context';
import {
  createAccountDetailWithId,
  createPreferences,
} from '../../../test/test-data-generators';
import {tap} from '@polymer/iron-test-helpers/mock-interactions';
import {AccountId} from '../../../types/common';

const basicFixture = fixtureFromElement('gr-error-manager');

suite('gr-error-manager tests', () => {
  let element: GrErrorManager;

  suite('when authed', () => {
    let toastSpy: sinon.SinonSpy;
    let fetchStub: sinon.SinonStub;
    let getLoggedInStub: sinon.SinonStub;

    setup(() => {
      fetchStub = stubAuth('fetch').returns(
        Promise.resolve({...new Response(), ok: true, status: 204})
      );
      getLoggedInStub = stubRestApi('getLoggedIn').callsFake(() =>
        appContext.authService.authCheck()
      );
      stubRestApi('getPreferences').returns(
        Promise.resolve(createPreferences())
      );
      element = basicFixture.instantiate();
      appContext.authService.clearCache();
      toastSpy = sinon.spy(element, '_createToastAlert');
    });

    teardown(() => {
      toastSpy.getCalls().forEach(call => {
        call.returnValue.remove();
      });
    });

    test('does not show auth error on 403 by default', done => {
      const showAuthErrorStub = sinon.stub(element, '_showAuthErrorAlert');
      const responseText = Promise.resolve('server says no.');
      element.dispatchEvent(
        new CustomEvent('server-error', {
          detail: {
            response: {
              status: 403,
              text() {
                return responseText;
              },
            },
          },
          composed: true,
          bubbles: true,
        })
      );
      flush(() => {
        assert.isFalse(showAuthErrorStub.calledOnce);
        done();
      });
    });

    test('show auth required for 403 with auth error and not authed before', done => {
      const showAuthErrorStub = sinon.stub(element, '_showAuthErrorAlert');
      const responseText = Promise.resolve('Authentication required\n');
      getLoggedInStub.returns(Promise.resolve(true));
      element.dispatchEvent(
        new CustomEvent('server-error', {
          detail: {
            response: {
              status: 403,
              text() {
                return responseText;
              },
            },
          },
          composed: true,
          bubbles: true,
        })
      );
      flush(() => {
        assert.isTrue(showAuthErrorStub.calledOnce);
        done();
      });
    });

    test('recheck auth for 403 with auth error if authed before', async () => {
      // Set status to AUTHED.
      appContext.authService.authCheck();
      const responseText = Promise.resolve('Authentication required\n');
      getLoggedInStub.returns(Promise.resolve(true));
      element.dispatchEvent(
        new CustomEvent('server-error', {
          detail: {
            response: {
              status: 403,
              text() {
                return responseText;
              },
            },
          },
          composed: true,
          bubbles: true,
        })
      );
      await flush();
      assert.isTrue(getLoggedInStub.calledOnce);
    });

    test('show logged in error', () => {
      const spy = sinon.spy(element, '_showAuthErrorAlert');
      element.dispatchEvent(
        new CustomEvent('show-auth-required', {
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(
        spy.calledWithExactly(
          'Log in is required to perform that action.',
          'Log in.'
        )
      );
    });

    test('show normal Error', done => {
      const showErrorSpy = sinon.spy(element, '_showErrorDialog');
      const textSpy = sinon.spy(() => Promise.resolve('ZOMG'));
      element.dispatchEvent(
        new CustomEvent('server-error', {
          detail: {response: {status: 500, text: textSpy}},
          composed: true,
          bubbles: true,
        })
      );

      assert.isTrue(textSpy.called);
      flush(() => {
        assert.isTrue(showErrorSpy.calledOnce);
        assert.isTrue(
          showErrorSpy.lastCall.calledWithExactly('Error 500: ZOMG')
        );
        done();
      });
    });

    test('constructServerErrorMsg', () => {
      const errorText = 'change conflicts';
      const status = 409;
      const statusText = 'Conflict';
      const url = '/my/test/url';

      assert.equal(constructServerErrorMsg({status}), 'Error 409');
      assert.equal(
        constructServerErrorMsg({status, url}),
        'Error 409: \nEndpoint: /my/test/url'
      );
      assert.equal(
        constructServerErrorMsg({status, statusText, url}),
        'Error 409 (Conflict): \nEndpoint: /my/test/url'
      );
      assert.equal(
        constructServerErrorMsg({
          status,
          statusText,
          errorText,
          url,
        }),
        'Error 409 (Conflict): change conflicts' + '\nEndpoint: /my/test/url'
      );
      assert.equal(
        constructServerErrorMsg({
          status,
          statusText,
          errorText,
          url,
          trace: 'xxxxx',
        }),
        'Error 409 (Conflict): change conflicts' +
          '\nEndpoint: /my/test/url\nTrace Id: xxxxx'
      );
    });

    test('extract trace id from headers if exists', done => {
      const textSpy = sinon.spy(() => Promise.resolve('500'));
      const headers = new Headers();
      headers.set('X-Gerrit-Trace', 'xxxx');
      element.dispatchEvent(
        new CustomEvent('server-error', {
          detail: {
            response: {
              headers,
              status: 500,
              text: textSpy,
            },
          },
          composed: true,
          bubbles: true,
        })
      );
      flush(() => {
        assert.equal(
          element.$.errorDialog.text,
          'Error 500: 500\nTrace Id: xxxx'
        );
        done();
      });
    });

    test('suppress TOO_MANY_FILES error', done => {
      const showAlertStub = sinon.stub(element, '_showAlert');
      const textSpy = sinon.spy(() =>
        Promise.resolve('too many files to find conflicts')
      );
      element.dispatchEvent(
        new CustomEvent('server-error', {
          detail: {response: {status: 500, text: textSpy}},
          composed: true,
          bubbles: true,
        })
      );

      assert.isTrue(textSpy.called);
      flush(() => {
        assert.isFalse(showAlertStub.called);
        done();
      });
    });

    test('suppress CONFLICTS_OPERATOR_IS_NOT_SUPPORTED error', done => {
      const showAlertStub = sinon.stub(element, '_showAlert');
      const textSpy = sinon.spy(() =>
        Promise.resolve("'conflicts:' operator is not supported by server")
      );
      element.dispatchEvent(
        new CustomEvent('server-error', {
          detail: {response: {status: 500, text: textSpy}},
          composed: true,
          bubbles: true,
        })
      );

      assert.isTrue(textSpy.called);
      flush(() => {
        assert.isFalse(showAlertStub.called);
        done();
      });
    });

    test('show network error', done => {
      const showAlertStub = sinon.stub(element, '_showAlert');
      element.dispatchEvent(
        new CustomEvent('network-error', {
          detail: {error: new Error('ZOMG')},
          composed: true,
          bubbles: true,
        })
      );
      flush(() => {
        assert.isTrue(showAlertStub.calledOnce);
        assert.isTrue(
          showAlertStub.lastCall.calledWithExactly('Server unavailable')
        );
        done();
      });
    });

    test('_canOverride alerts', () => {
      assert.isFalse(
        element._canOverride(undefined, __testOnly_ErrorType.AUTH)
      );
      assert.isFalse(
        element._canOverride(undefined, __testOnly_ErrorType.NETWORK)
      );
      assert.isTrue(
        element._canOverride(undefined, __testOnly_ErrorType.GENERIC)
      );
      assert.isTrue(element._canOverride(undefined, undefined));

      assert.isTrue(
        element._canOverride(__testOnly_ErrorType.NETWORK, undefined)
      );
      assert.isTrue(element._canOverride(__testOnly_ErrorType.AUTH, undefined));
      assert.isFalse(
        element._canOverride(
          __testOnly_ErrorType.NETWORK,
          __testOnly_ErrorType.AUTH
        )
      );

      assert.isTrue(
        element._canOverride(
          __testOnly_ErrorType.AUTH,
          __testOnly_ErrorType.NETWORK
        )
      );
    });

    test('show auth refresh toast', async () => {
      const clock = sinon.useFakeTimers();

      // Set status to AUTHED.
      appContext.authService.authCheck();
      const refreshStub = stubRestApi('getAccount').callsFake(() =>
        Promise.resolve(createAccountDetailWithId())
      );
      const windowOpen = sinon.stub(window, 'open');
      const responseText = Promise.resolve('Authentication required\n');
      // fake failed auth
      fetchStub.returns(Promise.resolve({...new Response(), status: 403}));
      element.dispatchEvent(
        new CustomEvent('server-error', {
          detail: {
            response: {
              status: 403,
              text() {
                return responseText;
              },
            },
          },
          composed: true,
          bubbles: true,
        })
      );
      assert.equal(fetchStub.callCount, 1);
      await flush();

      // here needs two flush as there are two chanined
      // promises on server-error handler and flush only flushes one
      assert.equal(fetchStub.callCount, 2);
      await flush();
      // Sometime overlay opens with delay, waiting while open is complete
      clock.tick(1000);
      await flush();
      // auth-error fired
      assert.isTrue(toastSpy.called);

      // toast
      let toast = toastSpy.lastCall.returnValue;
      assert.isOk(toast);
      assert.include(toast.shadowRoot.textContent, 'Credentials expired.');
      assert.include(toast.shadowRoot.textContent, 'Refresh credentials');

      // noInteractionOverlay
      const noInteractionOverlay = element.$.noInteractionOverlay;
      assert.isOk(noInteractionOverlay);
      const noInteractionOverlayCloseSpy = sinon.spy(
        noInteractionOverlay,
        'close'
      );
      assert.equal(
        noInteractionOverlay.backdropElement.getAttribute('opened'),
        ''
      );
      assert.isFalse(windowOpen.called);
      tap(toast.shadowRoot.querySelector('gr-button.action'));
      assert.isTrue(windowOpen.called);

      // @see Issue 5822: noopener breaks closeAfterLogin
      assert.equal(windowOpen.lastCall.args[2]?.indexOf('noopener=yes'), -1);

      const hideToastSpy = sinon.spy(toast, 'hide');

      // now fake authed
      fetchStub.returns(Promise.resolve({status: 204}));

      clock.tick(1000);
      element.knownAccountId = 5 as AccountId;
      element._checkSignedIn();
      await flush();

      assert.isTrue(refreshStub.called);
      assert.isTrue(hideToastSpy.called);

      // toast update
      assert.notStrictEqual(toastSpy.lastCall.returnValue, toast);
      toast = toastSpy.lastCall.returnValue;
      assert.isOk(toast);
      assert.include(toast.shadowRoot.textContent, 'Credentials refreshed');

      // close overlay
      assert.isTrue(noInteractionOverlayCloseSpy.called);
    });

    test('auth toast should dismiss existing toast', async () => {
      const clock = sinon.useFakeTimers();
      // Set status to AUTHED.
      appContext.authService.authCheck();
      const responseText = Promise.resolve('Authentication required\n');

      // fake an alert
      element.dispatchEvent(
        new CustomEvent('show-alert', {
          detail: {message: 'test reload', action: 'reload'},
          composed: true,
          bubbles: true,
        })
      );
      await flush();
      let toast = toastSpy.lastCall.returnValue;
      assert.isOk(toast);
      assert.include(toast.shadowRoot.textContent, 'test reload');

      // fake auth
      fetchStub.returns(Promise.resolve({status: 403}));
      element.dispatchEvent(
        new CustomEvent('server-error', {
          detail: {
            response: {
              status: 403,
              text() {
                return responseText;
              },
            },
          },
          composed: true,
          bubbles: true,
        })
      );
      await flush();
      await flush();
      // here needs two flush as there are two chained
      // promises on server-error handler and flush only flushes one
      assert.equal(fetchStub.callCount, 2);
      await flush();
      // Sometime overlay opens with delay, waiting while open is complete
      clock.tick(1000);
      await flush();
      // toast
      toast = toastSpy.lastCall.returnValue;
      assert.include(toast.shadowRoot.textContent, 'Credentials expired.');
      assert.include(toast.shadowRoot.textContent, 'Refresh credentials');
    });

    test('regular toast should dismiss regular toast', async () => {
      // Set status to AUTHED.
      appContext.authService.authCheck();

      // fake an alert
      element.dispatchEvent(
        new CustomEvent('show-alert', {
          detail: {message: 'test reload', action: 'reload'},
          composed: true,
          bubbles: true,
        })
      );
      await flush();
      let toast = toastSpy.lastCall.returnValue;
      assert.isOk(toast);
      assert.include(toast.shadowRoot.textContent, 'test reload');

      // new alert
      element.dispatchEvent(
        new CustomEvent('show-alert', {
          detail: {message: 'second-test', action: 'reload'},
          composed: true,
          bubbles: true,
        })
      );
      await flush();
      toast = toastSpy.lastCall.returnValue;
      assert.include(toast.shadowRoot.textContent, 'second-test');
    });

    test('regular toast should not dismiss auth toast', done => {
      // Set status to AUTHED.
      appContext.authService.authCheck();
      const responseText = Promise.resolve('Authentication required\n');

      // fake auth
      fetchStub.returns(Promise.resolve({status: 403}));
      element.dispatchEvent(
        new CustomEvent('server-error', {
          detail: {
            response: {
              status: 403,
              text() {
                return responseText;
              },
            },
          },
          composed: true,
          bubbles: true,
        })
      );
      assert.equal(fetchStub.callCount, 1);
      flush(() => {
        // here needs two flush as there are two chained
        // promises on server-error handler and flush only flushes one
        assert.equal(fetchStub.callCount, 2);
        flush(() => {
          let toast = toastSpy.lastCall.returnValue;
          assert.include(toast.shadowRoot.textContent, 'Credentials expired.');
          assert.include(toast.shadowRoot.textContent, 'Refresh credentials');

          // fake an alert
          element.dispatchEvent(
            new CustomEvent('show-alert', {
              detail: {
                message: 'test-alert',
                action: 'reload',
              },
              composed: true,
              bubbles: true,
            })
          );
          flush(() => {
            toast = toastSpy.lastCall.returnValue;
            assert.isOk(toast);
            assert.include(
              toast.shadowRoot.textContent,
              'Credentials expired.'
            );
            done();
          });
        });
      });
    });

    test('show alert', () => {
      const alertObj = {message: 'foo'};
      const showAlertStub = sinon.stub(element, '_showAlert');
      element.dispatchEvent(
        new CustomEvent('show-alert', {
          detail: alertObj,
          composed: true,
          bubbles: true,
        })
      );
      assert.isTrue(showAlertStub.calledOnce);
      assert.equal(showAlertStub.lastCall.args[0], 'foo');
      assert.isNotOk(showAlertStub.lastCall.args[1]);
      assert.isNotOk(showAlertStub.lastCall.args[2]);
    });

    test('checks stale credentials on visibility change', () => {
      const refreshStub = sinon.stub(element, '_checkSignedIn');
      sinon.stub(Date, 'now').returns(999999);
      element._lastCredentialCheck = 0;

      document.dispatchEvent(new CustomEvent('visibilitychange'));

      // Since there is no known account, it should not test credentials.
      assert.isFalse(refreshStub.called);
      assert.equal(element._lastCredentialCheck, 0);

      element.knownAccountId = 123 as AccountId;

      document.dispatchEvent(new CustomEvent('visibilitychange'));

      // Should test credentials, since there is a known account.
      assert.isTrue(refreshStub.called);
      assert.equal(element._lastCredentialCheck, 999999);
    });

    test('refreshes with same credentials', done => {
      const accountPromise = Promise.resolve({
        ...createAccountDetailWithId(1234),
      });
      stubRestApi('getAccount').returns(accountPromise);
      const requestCheckStub = sinon.stub(element, '_requestCheckLoggedIn');
      const handleRefreshStub = sinon.stub(
        element,
        'handleCredentialRefreshed'
      );
      const reloadStub = sinon.stub(element, '_reloadPage');

      element.knownAccountId = 1234 as AccountId;
      element._refreshingCredentials = true;
      element._checkSignedIn();

      flush(() => {
        assert.isFalse(requestCheckStub.called);
        assert.isTrue(handleRefreshStub.called);
        assert.isFalse(reloadStub.called);
        done();
      });
    });

    test('_showAlert hides existing alerts', () => {
      element._alertElement = element._createToastAlert();
      // const hideStub = sinon.stub(element, 'hideAlert');
      // element._showAlert('');
      // assert.isTrue(hideStub.calledOnce);
    });

    test('show-error', () => {
      const openStub = sinon.stub(element.$.errorOverlay, 'open');
      const closeStub = sinon.stub(element.$.errorOverlay, 'close');
      const reportStub = stubReporting('reportErrorDialog');

      const message = 'test message';
      element.dispatchEvent(
        new CustomEvent('show-error', {
          detail: {message},
          composed: true,
          bubbles: true,
        })
      );
      flush();

      assert.isTrue(openStub.called);
      assert.isTrue(reportStub.called);
      assert.equal(element.$.errorDialog.text, message);

      element.$.errorDialog.dispatchEvent(
        new CustomEvent('dismiss', {
          composed: true,
          bubbles: true,
        })
      );
      flush();

      assert.isTrue(closeStub.called);
    });

    test('reloads when refreshed credentials differ', done => {
      const accountPromise = Promise.resolve({
        ...createAccountDetailWithId(1234),
      });
      stubRestApi('getAccount').returns(accountPromise);
      const requestCheckStub = sinon.stub(element, '_requestCheckLoggedIn');
      const handleRefreshStub = sinon.stub(
        element,
        'handleCredentialRefreshed'
      );
      const reloadStub = sinon.stub(element, '_reloadPage');

      element.knownAccountId = 4321 as AccountId; // Different from 1234
      element._refreshingCredentials = true;
      element._checkSignedIn();

      flush(() => {
        assert.isFalse(requestCheckStub.called);
        assert.isFalse(handleRefreshStub.called);
        assert.isTrue(reloadStub.called);
        done();
      });
    });
  });

  suite('when not authed', () => {
    let toastSpy: sinon.SinonSpy;
    setup(() => {
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));
      element = basicFixture.instantiate();
      toastSpy = sinon.spy(element, '_createToastAlert');
    });

    teardown(() => {
      toastSpy.getCalls().forEach(call => {
        call.returnValue.remove();
      });
    });

    test('refresh loop continues on credential fail', done => {
      const requestCheckStub = sinon.stub(element, '_requestCheckLoggedIn');
      const handleRefreshStub = sinon.stub(
        element,
        'handleCredentialRefreshed'
      );
      const reloadStub = sinon.stub(element, '_reloadPage');

      element._refreshingCredentials = true;
      element._checkSignedIn();

      flush(() => {
        assert.isTrue(requestCheckStub.called);
        assert.isFalse(handleRefreshStub.called);
        assert.isFalse(reloadStub.called);
        done();
      });
    });
  });
});
