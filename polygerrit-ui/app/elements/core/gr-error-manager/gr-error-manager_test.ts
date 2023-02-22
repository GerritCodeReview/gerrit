/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-error-manager';
import {
  constructServerErrorMsg,
  GrErrorManager,
  __testOnly_ErrorType,
} from './gr-error-manager';
import {
  stubReporting,
  stubRestApi,
  waitEventLoop,
} from '../../../test/test-utils';
import {AppContext, getAppContext} from '../../../services/app-context';
import {
  createAccountDetailWithId,
  createPreferences,
} from '../../../test/test-data-generators';
import {AccountId} from '../../../types/common';
import {waitUntil} from '../../../test/test-utils';
import {fixture, assert} from '@open-wc/testing';
import {html} from 'lit';
import {EventType} from '../../../types/events';
import {testResolver} from '../../../test/common-test-setup';
import {authServiceToken} from '../../../services/gr-auth/gr-auth';

suite('gr-error-manager tests', () => {
  let element: GrErrorManager;

  suite('when authed', () => {
    let toastSpy: sinon.SinonSpy;
    let fetchStub: sinon.SinonStub;
    let getLoggedInStub: sinon.SinonStub;
    let appContext: AppContext;

    setup(async () => {
      fetchStub = sinon
        .stub(testResolver(authServiceToken), 'fetch')
        .returns(Promise.resolve({...new Response(), ok: true, status: 204}));
      appContext = getAppContext();
      getLoggedInStub = stubRestApi('getLoggedIn').callsFake(() =>
        appContext.authService.authCheck()
      );
      stubRestApi('getPreferences').returns(
        Promise.resolve(createPreferences())
      );
      element = await fixture<GrErrorManager>(
        html`<gr-error-manager></gr-error-manager>`
      );
      appContext.authService.clearCache();
      toastSpy = sinon.spy(element, 'createToastAlert');
      await element.updateComplete;
    });

    teardown(() => {
      toastSpy.getCalls().forEach(call => {
        call.returnValue.remove();
      });
    });

    test('renders', () => {
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <dialog id="errorModal" tabindex="-1">
            <gr-error-dialog id="errorDialog"> </gr-error-dialog>
          </dialog>
          <dialog id="signInModal" tabindex="-1">
            <gr-dialog
              id="signInDialog"
              confirm-label="Sign In"
              role="dialog"
              cancel-label=""
            >
              <div class="header" slot="header">Refresh Credentials</div>
            </gr-dialog>
          </dialog>
        `
      );
    });

    test('does not show auth error on 403 by default', async () => {
      const showAuthErrorStub = sinon.stub(element, 'showAuthErrorAlert');
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
      await waitEventLoop();
      assert.isFalse(showAuthErrorStub.calledOnce);
    });

    test('show auth required for 403 with auth error and not authed before', async () => {
      const showAuthErrorStub = sinon.stub(element, 'showAuthErrorAlert');
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
      await waitEventLoop();
      assert.isTrue(showAuthErrorStub.calledOnce);
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
      await waitEventLoop();
      assert.isTrue(getLoggedInStub.calledOnce);
    });

    test('show logged in error', () => {
      const spy = sinon.spy(element, 'showAuthErrorAlert');
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

    test('show normal Error', async () => {
      const showErrorSpy = sinon.spy(element, 'showErrorDialog');
      const textSpy = sinon.spy(() => Promise.resolve('ZOMG'));
      element.dispatchEvent(
        new CustomEvent('server-error', {
          detail: {response: {status: 500, text: textSpy}},
          composed: true,
          bubbles: true,
        })
      );

      assert.isTrue(textSpy.called);
      await waitEventLoop();
      assert.isTrue(showErrorSpy.calledOnce);
      assert.isTrue(showErrorSpy.lastCall.calledWithExactly('Error 500: ZOMG'));
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

    test('extract trace id from headers if exists', async () => {
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
      await waitEventLoop();
      assert.equal(element.errorDialog.text, 'Error 500: 500\nTrace Id: xxxx');
    });

    test('suppress TOO_MANY_FILES error', async () => {
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
      await waitEventLoop();
      assert.isFalse(showAlertStub.called);
    });

    test('show network error', async () => {
      const showAlertStub = sinon.stub(element, '_showAlert');
      element.dispatchEvent(
        new CustomEvent('network-error', {
          detail: {error: new Error('ZOMG')},
          composed: true,
          bubbles: true,
        })
      );
      await waitEventLoop();
      assert.isTrue(showAlertStub.calledOnce);
      assert.isTrue(
        showAlertStub.lastCall.calledWithExactly('Server unavailable')
      );
    });

    test('canOverride alerts', () => {
      assert.isFalse(element.canOverride(undefined, __testOnly_ErrorType.AUTH));
      assert.isFalse(
        element.canOverride(undefined, __testOnly_ErrorType.NETWORK)
      );
      assert.isTrue(
        element.canOverride(undefined, __testOnly_ErrorType.GENERIC)
      );
      assert.isTrue(element.canOverride(undefined, undefined));

      assert.isTrue(
        element.canOverride(__testOnly_ErrorType.NETWORK, undefined)
      );
      assert.isTrue(element.canOverride(__testOnly_ErrorType.AUTH, undefined));
      assert.isFalse(
        element.canOverride(
          __testOnly_ErrorType.NETWORK,
          __testOnly_ErrorType.AUTH
        )
      );

      assert.isTrue(
        element.canOverride(
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
      await waitEventLoop();

      // here needs two waitEventLoop() as there are two chained promises on
      // server-error handler and waitEventLoop() only flushes one
      assert.equal(fetchStub.callCount, 2);
      await waitEventLoop();
      // Sometime overlay opens with delay, waiting while open is complete
      clock.tick(1000);
      await waitEventLoop();
      // auth-error fired
      assert.isTrue(toastSpy.called);

      // toast
      let toast = toastSpy.lastCall.returnValue;
      assert.isOk(toast);
      assert.include(toast.shadowRoot.textContent, 'Credentials expired.');
      assert.include(toast.shadowRoot.textContent, 'Refresh credentials');

      // signInModal
      const signInModal = element.signInModal;
      assert.isOk(signInModal);
      const signInModalCloseSpy = sinon.spy(signInModal, 'close');
      assert.isTrue(signInModal.hasAttribute('open'));
      assert.isFalse(windowOpen.called);
      toast.shadowRoot.querySelector('gr-button.action')!.click();
      assert.isTrue(windowOpen.called);

      // @see Issue 5822: noopener breaks closeAfterLogin
      assert.equal(windowOpen.lastCall.args[2]?.indexOf('noopener=yes'), -1);

      const hideToastSpy = sinon.spy(toast, 'hide');

      // now fake authed
      fetchStub.returns(Promise.resolve({status: 204}));

      clock.tick(1000);
      element.knownAccountId = 5 as AccountId;
      element.checkSignedIn();
      await waitEventLoop();

      assert.isTrue(refreshStub.called);
      assert.isTrue(hideToastSpy.called);

      // toast update
      assert.notStrictEqual(toastSpy.lastCall.returnValue, toast);
      toast = toastSpy.lastCall.returnValue;
      assert.isOk(toast);
      assert.include(toast.shadowRoot.textContent, 'Credentials refreshed');

      // close overlay
      assert.isTrue(signInModalCloseSpy.called);
    });

    test('auth toast should dismiss existing toast', async () => {
      const clock = sinon.useFakeTimers();
      // Set status to AUTHED.
      appContext.authService.authCheck();
      const responseText = Promise.resolve('Authentication required\n');

      // fake an alert
      element.dispatchEvent(
        new CustomEvent(EventType.SHOW_ALERT, {
          detail: {message: 'test reload', action: 'reload'},
          composed: true,
          bubbles: true,
        })
      );
      await waitEventLoop();
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
      await waitEventLoop();
      await waitEventLoop();
      // here needs two waitEventLoop() as there are two chained promises on
      // server-error handler and waitEventLoop() only flushes one
      assert.equal(fetchStub.callCount, 2);
      await waitEventLoop();
      // Sometime overlay opens with delay, waiting while open is complete
      clock.tick(1000);
      await waitEventLoop();
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
        new CustomEvent(EventType.SHOW_ALERT, {
          detail: {message: 'test reload', action: 'reload'},
          composed: true,
          bubbles: true,
        })
      );
      await waitEventLoop();
      let toast = toastSpy.lastCall.returnValue;
      assert.isOk(toast);
      assert.include(toast.shadowRoot.textContent, 'test reload');

      // new alert
      element.dispatchEvent(
        new CustomEvent(EventType.SHOW_ALERT, {
          detail: {message: 'second-test', action: 'reload'},
          composed: true,
          bubbles: true,
        })
      );
      await waitEventLoop();
      toast = toastSpy.lastCall.returnValue;
      assert.include(toast.shadowRoot.textContent, 'second-test');
    });

    test('regular toast should not dismiss auth toast', async () => {
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
      await waitEventLoop();

      // here needs two waitEventLoop() as there are two chained promises on
      // server-error handler and waitEventLoop() only flushes one
      assert.equal(fetchStub.callCount, 2);
      await waitEventLoop();
      await waitUntil(() => toastSpy.calledOnce);
      let toast = toastSpy.lastCall.returnValue;
      assert.include(toast.shadowRoot.textContent, 'Credentials expired.');
      assert.include(toast.shadowRoot.textContent, 'Refresh credentials');

      // fake an alert
      element.dispatchEvent(
        new CustomEvent(EventType.SHOW_ALERT, {
          detail: {
            message: 'test-alert',
            action: 'reload',
          },
          composed: true,
          bubbles: true,
        })
      );

      await waitEventLoop();
      assert.isTrue(toastSpy.calledOnce);
      toast = toastSpy.lastCall.returnValue;
      assert.isOk(toast);
      assert.include(toast.shadowRoot.textContent, 'Credentials expired.');
    });

    test('show alert', () => {
      const alertObj = {message: 'foo'};
      const showAlertStub = sinon.stub(element, '_showAlert');
      element.dispatchEvent(
        new CustomEvent(EventType.SHOW_ALERT, {
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
      const refreshStub = sinon.stub(element, 'checkSignedIn');
      sinon.stub(Date, 'now').returns(999999);
      element.lastCredentialCheck = 0;

      document.dispatchEvent(new CustomEvent('visibilitychange'));

      // Since there is no known account, it should not test credentials.
      assert.isFalse(refreshStub.called);
      assert.equal(element.lastCredentialCheck, 0);

      element.knownAccountId = 123 as AccountId;

      document.dispatchEvent(new CustomEvent('visibilitychange'));

      // Should test credentials, since there is a known account.
      assert.isTrue(refreshStub.called);
      assert.equal(element.lastCredentialCheck, 999999);
    });

    test('refreshes with same credentials', async () => {
      const accountPromise = Promise.resolve({
        ...createAccountDetailWithId(1234),
      });
      stubRestApi('getAccount').returns(accountPromise);
      const requestCheckStub = sinon.stub(element, 'requestCheckLoggedIn');
      const handleRefreshStub = sinon.stub(
        element,
        'handleCredentialRefreshed'
      );
      const reloadStub = sinon.stub(element, 'reloadPage');

      element.knownAccountId = 1234 as AccountId;
      element.refreshingCredentials = true;
      element.checkSignedIn();

      await waitEventLoop();
      assert.isFalse(requestCheckStub.called);
      assert.isTrue(handleRefreshStub.called);
      assert.isFalse(reloadStub.called);
    });

    test('_showAlert hides existing alerts', () => {
      element.alertElement = element.createToastAlert();
      // const hideStub = sinon.stub(element, 'hideAlert');
      // element._showAlert('');
      // assert.isTrue(hideStub.calledOnce);
    });

    test('show-error', async () => {
      const openStub = sinon.stub(element.errorModal, 'showModal');
      const closeStub = sinon.stub(element.errorModal, 'close');
      const reportStub = stubReporting('reportErrorDialog');

      const message = 'test message';
      element.dispatchEvent(
        new CustomEvent('show-error', {
          detail: {message},
          composed: true,
          bubbles: true,
        })
      );
      await waitEventLoop();

      assert.isTrue(openStub.called);
      assert.isTrue(reportStub.called);
      assert.equal(element.errorDialog.text, message);

      element.errorDialog.dispatchEvent(
        new CustomEvent('dismiss', {
          composed: true,
          bubbles: true,
        })
      );
      await waitEventLoop();

      assert.isTrue(closeStub.called);
    });

    test('reloads when refreshed credentials differ', async () => {
      const accountPromise = Promise.resolve({
        ...createAccountDetailWithId(1234),
      });
      stubRestApi('getAccount').returns(accountPromise);
      const requestCheckStub = sinon.stub(element, 'requestCheckLoggedIn');
      const handleRefreshStub = sinon.stub(
        element,
        'handleCredentialRefreshed'
      );
      const reloadStub = sinon.stub(element, 'reloadPage');

      element.knownAccountId = 4321 as AccountId; // Different from 1234
      element.refreshingCredentials = true;
      element.checkSignedIn();

      await waitEventLoop();

      assert.isFalse(requestCheckStub.called);
      assert.isFalse(handleRefreshStub.called);
      assert.isTrue(reloadStub.called);
    });
  });

  suite('when not authed', () => {
    let toastSpy: sinon.SinonSpy;
    setup(async () => {
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));
      element = await fixture<GrErrorManager>(
        html`<gr-error-manager></gr-error-manager>`
      );
      toastSpy = sinon.spy(element, 'createToastAlert');
      await element.updateComplete;
    });

    teardown(() => {
      toastSpy.getCalls().forEach(call => {
        call.returnValue.remove();
      });
    });

    test('refresh loop continues on credential fail', async () => {
      const requestCheckStub = sinon.stub(element, 'requestCheckLoggedIn');
      const handleRefreshStub = sinon.stub(
        element,
        'handleCredentialRefreshed'
      );
      const reloadStub = sinon.stub(element, 'reloadPage');

      element.refreshingCredentials = true;
      element.checkSignedIn();

      await waitEventLoop();
      assert.isTrue(requestCheckStub.called);
      assert.isFalse(handleRefreshStub.called);
      assert.isFalse(reloadStub.called);
    });
  });
});
