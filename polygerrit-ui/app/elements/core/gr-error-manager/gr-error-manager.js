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
/* Import to get Gerrit interface */
/* TODO(taoalpha): decouple gr-gerrit from gr-js-api-interface */
/*
  FIXME(polymer-modulizer): the above comments were extracted
  from HTML and may be out of place here. Review them and
  then delete this comment!
*/
import '../../../behaviors/base-url-behavior/base-url-behavior.js';

import '../../../scripts/bundled-polymer.js';
import '../../../behaviors/fire-behavior/fire-behavior.js';
import '../gr-error-dialog/gr-error-dialog.js';
import '../gr-reporting/gr-reporting.js';
import '../../shared/gr-alert/gr-alert.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-js-api-interface/gr-js-api-interface.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-error-manager_html.js';
import {GrReportingProvider} from '../gr-reporting/gr-reporting.js';

const HIDE_ALERT_TIMEOUT_MS = 5000;
const CHECK_SIGN_IN_INTERVAL_MS = 60 * 1000;
const STALE_CREDENTIAL_THRESHOLD_MS = 10 * 60 * 1000;
const SIGN_IN_WIDTH_PX = 690;
const SIGN_IN_HEIGHT_PX = 500;
const TOO_MANY_FILES = 'too many files to find conflicts';
const AUTHENTICATION_REQUIRED = 'Authentication required\n';

/**
 * @appliesMixin Gerrit.BaseUrlMixin
 * @appliesMixin Gerrit.FireMixin
 * @extends Polymer.Element
 */
class GrErrorManager extends mixinBehaviors( [
  Gerrit.BaseUrlBehavior,
  Gerrit.FireBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-error-manager'; }

  static get properties() {
    return {
    /**
     * The ID of the account that was logged in when the app was launched. If
     * not set, then there was no account at launch.
     */
      knownAccountId: Number,

      /** @type {?Object} */
      _alertElement: Object,
      /** @type {?number} */
      _hideAlertHandle: Number,
      _refreshingCredentials: {
        type: Boolean,
        value: false,
      },

      /**
       * The time (in milliseconds) since the most recent credential check.
       */
      _lastCredentialCheck: {
        type: Number,
        value() { return Date.now(); },
      },

      loginUrl: {
        type: String,
        value: '/login',
      },
    };
  }

  constructor() {
    super();

    /** @type {!Gerrit.Auth} */
    this._authService = Gerrit.Auth;

    /** @type {?Function} */
    this._authErrorHandlerDeregistrationHook;
  }

  /** @override */
  created() {
    super.created();
    this.reporting = GrReportingProvider.getReportingInstance();
  }

  /** @override */
  attached() {
    super.attached();
    this.listen(document, 'server-error', '_handleServerError');
    this.listen(document, 'network-error', '_handleNetworkError');
    this.listen(document, 'show-alert', '_handleShowAlert');
    this.listen(document, 'show-error', '_handleShowErrorDialog');
    this.listen(document, 'visibilitychange', '_handleVisibilityChange');
    this.listen(document, 'show-auth-required', '_handleAuthRequired');

    this._authErrorHandlerDeregistrationHook = Gerrit.on('auth-error',
        event => {
          this._handleAuthError(event.message, event.action);
        });
  }

  /** @override */
  detached() {
    super.detached();
    this._clearHideAlertHandle();
    this.unlisten(document, 'server-error', '_handleServerError');
    this.unlisten(document, 'network-error', '_handleNetworkError');
    this.unlisten(document, 'show-auth-required', '_handleAuthRequired');
    this.unlisten(document, 'visibilitychange', '_handleVisibilityChange');
    this.unlisten(document, 'show-error', '_handleShowErrorDialog');

    this._authErrorHandlerDeregistrationHook();
  }

  _shouldSuppressError(msg) {
    return msg.includes(TOO_MANY_FILES);
  }

  _handleAuthRequired() {
    this._showAuthErrorAlert(
        'Log in is required to perform that action.', 'Log in.');
  }

  _handleAuthError(msg, action) {
    this.$.noInteractionOverlay.open().then(() => {
      this._showAuthErrorAlert(msg, action);
    });
  }

  _handleServerError(e) {
    const {request, response} = e.detail;
    response.text().then(errorText => {
      const url = request && (request.anonymizedUrl || request.url);
      const {status, statusText} = response;
      if (response.status === 403
              && !this._authService.isAuthed
              && errorText === AUTHENTICATION_REQUIRED) {
        // if not authed previously, this is trying to access auth required APIs
        // show auth required alert
        this._handleAuthRequired();
      } else if (response.status === 403
              && this._authService.isAuthed
              && errorText === AUTHENTICATION_REQUIRED) {
        // The app was logged at one point and is now getting auth errors.
        // This indicates the auth token may no longer valid.
        // Re-check on auth
        this._authService.clearCache();
        this.$.restAPI.getLoggedIn();
      } else if (!this._shouldSuppressError(errorText)) {
        const trace =
            response.headers && response.headers.get('X-Gerrit-Trace');
        if (response.status === 404) {
          this._showNotFoundMessageWithTip({
            status,
            statusText,
            errorText,
            url,
            trace,
          });
        } else {
          this._showErrorDialog(this._constructServerErrorMsg({
            status,
            statusText,
            errorText,
            url,
            trace,
          }));
        }
      }
      console.log(`server error: ${errorText}`);
    });
  }

  _showNotFoundMessageWithTip({status, statusText, errorText, url, trace}) {
    this.$.restAPI.getLoggedIn().then(isLoggedIn => {
      const tip = isLoggedIn ?
        'You might have not enough privileges.' :
        'You might have not enough privileges. Sign in and try again.';
      this._showErrorDialog(this._constructServerErrorMsg({
        status,
        statusText,
        errorText,
        url,
        trace,
        tip,
      }), {
        showSignInButton: !isLoggedIn,
      });
    });
    return;
  }

  _constructServerErrorMsg({errorText, status, statusText, url, trace, tip}) {
    let err = '';
    if (tip) {
      err += `${tip}\n\n`;
    }
    err += `Error ${status}`;
    if (statusText) { err += ` (${statusText})`; }
    if (errorText || url) { err += ': '; }
    if (errorText) { err += errorText; }
    if (url) { err += `\nEndpoint: ${url}`; }
    if (trace) { err += `\nTrace Id: ${trace}`; }
    return err;
  }

  _handleShowAlert(e) {
    this._showAlert(e.detail.message, e.detail.action, e.detail.callback,
        e.detail.dismissOnNavigation);
  }

  _handleNetworkError(e) {
    this._showAlert('Server unavailable');
    console.error(e.detail.error.message);
  }

  /**
   * @param {string} text
   * @param {?string=} opt_actionText
   * @param {?Function=} opt_actionCallback
   * @param {?boolean=} opt_dismissOnNavigation
   */
  _showAlert(text, opt_actionText, opt_actionCallback,
      opt_dismissOnNavigation) {
    if (this._alertElement) {
      // do not override auth alerts
      if (this._alertElement.type === 'AUTH') return;
      this._hideAlert();
    }

    this._clearHideAlertHandle();
    if (opt_dismissOnNavigation) {
      // Persist alert until navigation.
      this.listen(document, 'location-change', '_hideAlert');
    } else {
      this._hideAlertHandle =
        this.async(this._hideAlert, HIDE_ALERT_TIMEOUT_MS);
    }
    const el = this._createToastAlert();
    el.show(text, opt_actionText, opt_actionCallback);
    this._alertElement = el;
  }

  _hideAlert() {
    if (!this._alertElement) { return; }

    this._alertElement.hide();
    this._alertElement = null;

    // Remove listener for page navigation, if it exists.
    this.unlisten(document, 'location-change', '_hideAlert');
  }

  _clearHideAlertHandle() {
    if (this._hideAlertHandle != null) {
      this.cancelAsync(this._hideAlertHandle);
      this._hideAlertHandle = null;
    }
  }

  _showAuthErrorAlert(errorText, actionText) {
    // hide any existing alert like `reload`
    // as auth error should have the highest priority
    if (this._alertElement) {
      this._alertElement.hide();
    }

    this._alertElement = this._createToastAlert();
    this._alertElement.type = 'AUTH';
    this._alertElement.show(errorText, actionText,
        this._createLoginPopup.bind(this));

    this._refreshingCredentials = true;
    this._requestCheckLoggedIn();
    if (!document.hidden) {
      this._handleVisibilityChange();
    }
  }

  _createToastAlert() {
    const el = document.createElement('gr-alert');
    el.toast = true;
    return el;
  }

  _handleVisibilityChange() {
    // Ignore when the page is transitioning to hidden (or hidden is
    // undefined).
    if (document.hidden !== false) { return; }

    // If not currently refreshing credentials and the credentials are old,
    // request them to confirm their validity or (display an auth toast if it
    // fails).
    const timeSinceLastCheck = Date.now() - this._lastCredentialCheck;
    if (!this._refreshingCredentials &&
        this.knownAccountId !== undefined &&
        timeSinceLastCheck > STALE_CREDENTIAL_THRESHOLD_MS) {
      this._lastCredentialCheck = Date.now();

      // check auth status in case:
      // - user signed out
      // - user switched account
      this._checkSignedIn();
    }
  }

  _requestCheckLoggedIn() {
    this.debounce(
        'checkLoggedIn', this._checkSignedIn, CHECK_SIGN_IN_INTERVAL_MS);
  }

  _checkSignedIn() {
    this._lastCredentialCheck = Date.now();

    // force to refetch account info
    this.$.restAPI.invalidateAccountsCache();
    this._authService.clearCache();

    this.$.restAPI.getLoggedIn().then(isLoggedIn => {
      // do nothing if its refreshing
      if (!this._refreshingCredentials) return;

      if (!isLoggedIn) {
        // check later
        // 1. guest mode
        // 2. or signed out
        // in case #2, auth-error is taken care of separately
        this._requestCheckLoggedIn();
      } else {
        // check account
        this.$.restAPI.getAccount().then(account => {
          if (this._refreshingCredentials) {
            // If the credentials were refreshed but the account is different
            // then reload the page completely.
            if (account._account_id !== this.knownAccountId) {
              this._reloadPage();
              return;
            }

            this._handleCredentialRefreshed();
          }
        });
      }
    });
  }

  _reloadPage() {
    window.location.reload();
  }

  _createLoginPopup() {
    const left = window.screenLeft +
        (window.outerWidth - SIGN_IN_WIDTH_PX) / 2;
    const top = window.screenTop +
        (window.outerHeight - SIGN_IN_HEIGHT_PX) / 2;
    const options = [
      'width=' + SIGN_IN_WIDTH_PX,
      'height=' + SIGN_IN_HEIGHT_PX,
      'left=' + left,
      'top=' + top,
    ];
    window.open(this.getBaseUrl() +
        '/login/%3FcloseAfterLogin', '_blank', options.join(','));
    this.listen(window, 'focus', '_handleWindowFocus');
  }

  _handleCredentialRefreshed() {
    this.unlisten(window, 'focus', '_handleWindowFocus');
    this._refreshingCredentials = false;
    this._hideAlert();
    this._showAlert('Credentials refreshed.');
    this.$.noInteractionOverlay.close();

    // Clear the cache for auth
    this._authService.clearCache();
  }

  _handleWindowFocus() {
    this.flushDebouncer('checkLoggedIn');
  }

  _handleShowErrorDialog(e) {
    this._showErrorDialog(e.detail.message);
  }

  _handleDismissErrorDialog() {
    this.$.errorOverlay.close();
  }

  _showErrorDialog(message, opt_options) {
    this.reporting.reportErrorDialog(message);
    this.$.errorDialog.text = message;
    this.$.errorDialog.showSignInButton =
        opt_options && opt_options.showSignInButton;
    this.$.errorOverlay.open();
  }
}

customElements.define(GrErrorManager.is, GrErrorManager);
