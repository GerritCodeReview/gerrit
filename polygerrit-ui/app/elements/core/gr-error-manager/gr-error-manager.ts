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
import '../gr-error-dialog/gr-error-dialog';
import '../../shared/gr-alert/gr-alert';
import '../../shared/gr-overlay/gr-overlay';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-error-manager_html';
import {getBaseUrl} from '../../../utils/url-util';
import {appContext} from '../../../services/app-context';
import {IronA11yAnnouncer} from '@polymer/iron-a11y-announcer/iron-a11y-announcer';
import {customElement, property} from '@polymer/decorators';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GrErrorDialog} from '../gr-error-dialog/gr-error-dialog';
import {GrAlert} from '../../shared/gr-alert/gr-alert';
import {ErrorType, FixIronA11yAnnouncer} from '../../../types/types';
import {AccountId} from '../../../types/common';
import {EventType} from '../../../utils/event-util';
import {
  NetworkErrorEvent,
  ServerErrorEvent,
  ShowAlertEvent,
} from '../../../types/events';
import {windowLocationReload} from '../../../utils/dom-util';

const HIDE_ALERT_TIMEOUT_MS = 5000;
const CHECK_SIGN_IN_INTERVAL_MS = 60 * 1000;
const STALE_CREDENTIAL_THRESHOLD_MS = 10 * 60 * 1000;
const SIGN_IN_WIDTH_PX = 690;
const SIGN_IN_HEIGHT_PX = 500;
const TOO_MANY_FILES = 'too many files to find conflicts';
const AUTHENTICATION_REQUIRED = 'Authentication required\n';

// Bigger number has higher priority
const ErrorTypePriority = {
  [ErrorType.AUTH]: 3,
  [ErrorType.NETWORK]: 2,
  [ErrorType.GENERIC]: 1,
};

interface ErrorMsg {
  errorText?: string;
  status?: number;
  statusText?: string;
  url?: string;
  trace?: string | null;
  tip?: string;
}

export const __testOnly_ErrorType = ErrorType;

export interface GrErrorManager {
  $: {
    noInteractionOverlay: GrOverlay;
    errorDialog: GrErrorDialog;
    errorOverlay: GrOverlay;
  };
}
@customElement('gr-error-manager')
export class GrErrorManager extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * The ID of the account that was logged in when the app was launched. If
   * not set, then there was no account at launch.
   */
  @property({type: Number})
  knownAccountId?: AccountId | null;

  @property({type: Object})
  _alertElement: GrAlert | null = null;

  @property({type: Number})
  _hideAlertHandle: number | null = null;

  @property({type: Boolean})
  _refreshingCredentials = false;

  /**
   * The time (in milliseconds) since the most recent credential check.
   */
  @property({type: Number})
  _lastCredentialCheck: number = Date.now();

  @property({type: String})
  loginUrl = '/login';

  private readonly reporting = appContext.reportingService;

  private readonly _authService = appContext.authService;

  private readonly eventEmitter = appContext.eventEmitter;

  _authErrorHandlerDeregistrationHook?: Function;

  private readonly restApiService = appContext.restApiService;

  /** @override */
  attached() {
    super.attached();
    this.listen(document, EventType.SERVER_ERROR, '_handleServerError');
    this.listen(document, EventType.NETWORK_ERROR, '_handleNetworkError');
    this.listen(document, EventType.SHOW_ALERT, '_handleShowAlert');
    this.listen(document, 'hide-alert', '_hideAlert');
    this.listen(document, 'show-error', '_handleShowErrorDialog');
    this.listen(document, 'visibilitychange', '_handleVisibilityChange');
    this.listen(document, 'show-auth-required', '_handleAuthRequired');

    this._authErrorHandlerDeregistrationHook = this.eventEmitter.on(
      'auth-error',
      event => {
        this._handleAuthError(event.message, event.action);
      }
    );

    ((IronA11yAnnouncer as unknown) as FixIronA11yAnnouncer).requestAvailability();
  }

  /** @override */
  detached() {
    super.detached();
    this._clearHideAlertHandle();
    this.unlisten(document, EventType.SERVER_ERROR, '_handleServerError');
    this.unlisten(document, EventType.NETWORK_ERROR, '_handleNetworkError');
    this.unlisten(document, EventType.SHOW_ALERT, '_handleShowAlert');
    this.unlisten(document, 'hide-alert', '_hideAlert');
    this.unlisten(document, 'show-error', '_handleShowErrorDialog');
    this.unlisten(document, 'visibilitychange', '_handleVisibilityChange');
    this.unlisten(document, 'show-auth-required', '_handleAuthRequired');

    if (this._authErrorHandlerDeregistrationHook) {
      this._authErrorHandlerDeregistrationHook();
    }
  }

  _shouldSuppressError(msg: string) {
    return msg.includes(TOO_MANY_FILES);
  }

  _handleAuthRequired() {
    this._showAuthErrorAlert(
      'Log in is required to perform that action.',
      'Log in.'
    );
  }

  _handleAuthError(msg: string, action: string) {
    this.$.noInteractionOverlay.open().then(() => {
      this._showAuthErrorAlert(msg, action);
    });
  }

  _handleServerError(e: ServerErrorEvent) {
    const {request, response} = e.detail;
    response.text().then(errorText => {
      const url = request && (request.anonymizedUrl || request.url);
      const {status, statusText} = response;
      if (
        response.status === 403 &&
        !this._authService.isAuthed &&
        errorText === AUTHENTICATION_REQUIRED
      ) {
        // if not authed previously, this is trying to access auth required APIs
        // show auth required alert
        this._handleAuthRequired();
      } else if (
        response.status === 403 &&
        this._authService.isAuthed &&
        errorText === AUTHENTICATION_REQUIRED
      ) {
        // The app was logged at one point and is now getting auth errors.
        // This indicates the auth token may no longer valid.
        // Re-check on auth
        this._authService.clearCache();
        this.restApiService.getLoggedIn();
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
          this._showErrorDialog(
            this._constructServerErrorMsg({
              status,
              statusText,
              errorText,
              url,
              trace,
            })
          );
        }
      }
      console.info(`server error: ${errorText}`);
    });
  }

  _showNotFoundMessageWithTip({
    status,
    statusText,
    errorText,
    url,
    trace,
  }: ErrorMsg) {
    this.restApiService.getLoggedIn().then(isLoggedIn => {
      const tip = isLoggedIn
        ? 'You might have not enough privileges.'
        : 'You might have not enough privileges. Sign in and try again.';
      this._showErrorDialog(
        this._constructServerErrorMsg({
          status,
          statusText,
          errorText,
          url,
          trace,
          tip,
        }),
        {
          showSignInButton: !isLoggedIn,
        }
      );
    });
  }

  _constructServerErrorMsg({
    errorText,
    status,
    statusText,
    url,
    trace,
    tip,
  }: ErrorMsg) {
    let err = '';
    if (tip) {
      err += `${tip}\n\n`;
    }
    err += `Error ${status}`;
    if (statusText) {
      err += ` (${statusText})`;
    }
    if (errorText || url) {
      err += ': ';
    }
    if (errorText) {
      err += errorText;
    }
    if (url) {
      err += `\nEndpoint: ${url}`;
    }
    if (trace) {
      err += `\nTrace Id: ${trace}`;
    }
    return err;
  }

  _handleShowAlert(e: ShowAlertEvent) {
    this._showAlert(
      e.detail.message,
      e.detail.action,
      e.detail.callback,
      e.detail.dismissOnNavigation,
      undefined,
      e.detail.showDismiss
    );
  }

  _handleNetworkError(e: NetworkErrorEvent) {
    this._showAlert('Server unavailable');
    console.error(e.detail.error.message);
  }

  // TODO(dhruvsr): allow less priority alerts to override high priority alerts
  // In some use cases we may want generic alerts to show along/over errors
  _canOverride(incoming = ErrorType.GENERIC, existing = ErrorType.GENERIC) {
    return ErrorTypePriority[incoming] >= ErrorTypePriority[existing];
  }

  _showAlert(
    text: string,
    actionText?: string,
    actionCallback?: () => void,
    dismissOnNavigation?: boolean,
    type?: ErrorType,
    showDismiss?: boolean
  ) {
    if (this._alertElement) {
      // check priority before hiding
      if (!this._canOverride(type, this._alertElement.type)) return;
      this._hideAlert();
    }

    this._clearHideAlertHandle();
    if (dismissOnNavigation) {
      // Persist alert until navigation.
      this.listen(document, 'location-change', '_hideAlert');
    } else {
      this._hideAlertHandle = this.async(
        this._hideAlert,
        HIDE_ALERT_TIMEOUT_MS
      );
    }
    const el = this._createToastAlert(showDismiss);
    el.show(text, actionText, actionCallback);
    this._alertElement = el;
    this.fire('iron-announce', {text: `Alert: ${text}`}, {bubbles: true});
    this.reporting.reportInteraction('show-alert', {text});
  }

  _hideAlert() {
    if (!this._alertElement) {
      return;
    }

    this._alertElement.hide();
    this._alertElement = null;

    // Remove listener for page navigation, if it exists.
    this.unlisten(document, 'location-change', '_hideAlert');
  }

  _clearHideAlertHandle() {
    if (this._hideAlertHandle !== null) {
      this.cancelAsync(this._hideAlertHandle);
      this._hideAlertHandle = null;
    }
  }

  _showAuthErrorAlert(errorText: string, actionText?: string) {
    // hide any existing alert like `reload`
    // as auth error should have the highest priority
    if (this._alertElement) {
      this._alertElement.hide();
    }

    this._alertElement = this._createToastAlert();
    this._alertElement.type = ErrorType.AUTH;
    this._alertElement.show(errorText, actionText, () =>
      this._createLoginPopup()
    );
    this.fire('iron-announce', {text: errorText}, {bubbles: true});
    this._refreshingCredentials = true;
    this._requestCheckLoggedIn();
    if (!document.hidden) {
      this._handleVisibilityChange();
    }
  }

  _createToastAlert(showDismiss?: boolean) {
    const el = document.createElement('gr-alert');
    el.toast = true;
    el.showDismiss = !!showDismiss;
    return el;
  }

  _handleVisibilityChange() {
    // Ignore when the page is transitioning to hidden (or hidden is
    // undefined).
    if (document.hidden !== false) {
      return;
    }

    // If not currently refreshing credentials and the credentials are old,
    // request them to confirm their validity or (display an auth toast if it
    // fails).
    const timeSinceLastCheck = Date.now() - this._lastCredentialCheck;
    if (
      !this._refreshingCredentials &&
      this.knownAccountId !== undefined &&
      timeSinceLastCheck > STALE_CREDENTIAL_THRESHOLD_MS
    ) {
      this._lastCredentialCheck = Date.now();

      // check auth status in case:
      // - user signed out
      // - user switched account
      this._checkSignedIn();
    }
  }

  _requestCheckLoggedIn() {
    this.debounce(
      'checkLoggedIn',
      this._checkSignedIn,
      CHECK_SIGN_IN_INTERVAL_MS
    );
  }

  _checkSignedIn() {
    this._lastCredentialCheck = Date.now();

    // force to refetch account info
    this.restApiService.invalidateAccountsCache();
    this._authService.clearCache();

    this.restApiService.getLoggedIn().then(isLoggedIn => {
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
        this.restApiService.getAccount().then(account => {
          if (this._refreshingCredentials) {
            // If the credentials were refreshed but the account is different
            // then reload the page completely.
            if (account?._account_id !== this.knownAccountId) {
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
    windowLocationReload();
  }

  _createLoginPopup() {
    const left = window.screenLeft + (window.outerWidth - SIGN_IN_WIDTH_PX) / 2;
    const top = window.screenTop + (window.outerHeight - SIGN_IN_HEIGHT_PX) / 2;
    const options = [
      `width=${SIGN_IN_WIDTH_PX}`,
      `height=${SIGN_IN_HEIGHT_PX}`,
      `left=${left}`,
      `top=${top}`,
    ];
    window.open(
      getBaseUrl() + '/login/%3FcloseAfterLogin',
      '_blank',
      options.join(',')
    );
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

  _handleShowErrorDialog(e: CustomEvent) {
    this._showErrorDialog(e.detail.message);
  }

  _handleDismissErrorDialog() {
    this.$.errorOverlay.close();
  }

  _showErrorDialog(message: string, options?: {showSignInButton?: boolean}) {
    this.reporting.reportErrorDialog(message);
    this.$.errorDialog.text = message;
    this.$.errorDialog.showSignInButton =
      !!options && !!options.showSignInButton;
    this.$.errorOverlay.open();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-error-manager': GrErrorManager;
  }
}
