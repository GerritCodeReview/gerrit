/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-error-dialog/gr-error-dialog';
import '../../shared/gr-alert/gr-alert';
import {getBaseUrl} from '../../../utils/url-util';
import {getAppContext} from '../../../services/app-context';
import {IronA11yAnnouncer} from '@polymer/iron-a11y-announcer/iron-a11y-announcer';
import {GrErrorDialog} from '../gr-error-dialog/gr-error-dialog';
import {GrAlert} from '../../shared/gr-alert/gr-alert';
import {ErrorType, FixIronA11yAnnouncer} from '../../../types/types';
import {AccountId} from '../../../types/common';
import {
  AuthErrorEvent,
  EventType,
  NetworkErrorEvent,
  ServerErrorEvent,
  ShowAlertEventDetail,
  ShowErrorEvent,
} from '../../../types/events';
import {windowLocationReload} from '../../../utils/dom-util';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {fireIronAnnounce} from '../../../utils/event-util';
import {LitElement, html} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {authServiceToken} from '../../../services/gr-auth/gr-auth';
import {resolve} from '../../../models/dependency';
import {modalStyles} from '../../../styles/gr-modal-styles';

const HIDE_ALERT_TIMEOUT_MS = 10 * 1000;
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

export function constructServerErrorMsg({
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

@customElement('gr-error-manager')
export class GrErrorManager extends LitElement {
  /**
   * The ID of the account that was logged in when the app was launched. If
   * not set, then there was no account at launch.
   */
  @state() knownAccountId?: AccountId | null;

  @state() alertElement: GrAlert | null = null;

  @state() hideAlertHandle: number | null = null;

  @state() refreshingCredentials = false;

  @query('#signInModal') signInModal!: HTMLDialogElement;

  @query('#errorDialog') errorDialog!: GrErrorDialog;

  @query('#errorModal') errorModal!: HTMLDialogElement;

  /**
   * The time (in milliseconds) since the most recent credential check.
   */
  @state() lastCredentialCheck: number = Date.now();

  @property({type: String})
  loginUrl = '/login';

  private readonly reporting = getAppContext().reportingService;

  private readonly getAuthService = resolve(this, authServiceToken);

  private readonly restApiService = getAppContext().restApiService;

  private checkLoggedInTask?: DelayedTask;

  override connectedCallback() {
    super.connectedCallback();
    document.addEventListener(EventType.SERVER_ERROR, this.handleServerError);
    document.addEventListener(EventType.NETWORK_ERROR, this.handleNetworkError);
    document.addEventListener(EventType.SHOW_ALERT, this.handleShowAlert);
    document.addEventListener('hide-alert', this.hideAlert);
    document.addEventListener('show-error', this.handleShowErrorDialog);
    document.addEventListener('visibilitychange', this.handleVisibilityChange);
    document.addEventListener('show-auth-required', this.handleAuthRequired);
    document.addEventListener('auth-error', this.handleAuthError);

    (
      IronA11yAnnouncer as unknown as FixIronA11yAnnouncer
    ).requestAvailability();
  }

  override disconnectedCallback() {
    this.clearHideAlertHandle();
    document.removeEventListener(
      EventType.SERVER_ERROR,
      this.handleServerError
    );
    document.removeEventListener(
      EventType.NETWORK_ERROR,
      this.handleNetworkError
    );
    document.removeEventListener(EventType.SHOW_ALERT, this.handleShowAlert);
    document.removeEventListener('hide-alert', this.hideAlert);
    document.removeEventListener('show-error', this.handleShowErrorDialog);
    document.removeEventListener(
      'visibilitychange',
      this.handleVisibilityChange
    );
    document.removeEventListener('show-auth-required', this.handleAuthRequired);
    this.checkLoggedInTask?.cancel();

    document.removeEventListener('auth-error', this.handleAuthError);
    super.disconnectedCallback();
  }

  static override get styles() {
    return [modalStyles];
  }

  override render() {
    return html`
      <dialog id="errorModal" tabindex="-1">
        <gr-error-dialog
          id="errorDialog"
          @dismiss=${() => this.errorModal.close()}
          .loginUrl=${this.loginUrl}
        ></gr-error-dialog>
      </dialog>
      <dialog
        id="signInModal"
        @keydown=${(e: KeyboardEvent) => {
          if (e.key === 'Escape') {
            e.preventDefault();
            e.stopPropagation();
          }
        }}
        tabindex="-1"
      >
        <gr-dialog
          id="signInDialog"
          confirm-label="Sign In"
          @confirm=${() => {
            this.createLoginPopup();
          }}
          cancel-label=""
        >
          <div class="header" slot="header">Refresh Credentials</div>
        </gr-dialog>
      </dialog>
    `;
  }

  private shouldSuppressError(msg: string) {
    return msg.includes(TOO_MANY_FILES);
  }

  private readonly handleAuthRequired = () => {
    this.showAuthErrorAlert(
      'Log in is required to perform that action.',
      'Log in.'
    );
  };

  private handleAuthError = (event: AuthErrorEvent) => {
    this.signInModal.showModal();
    this.showAuthErrorAlert(event.detail.message, event.detail.action);
  };

  private readonly handleServerError = (e: ServerErrorEvent) => {
    const {request, response} = e.detail;
    response.text().then(errorText => {
      const url = request && (request.anonymizedUrl || request.url);
      const {status, statusText} = response;
      if (
        response.status === 403 &&
        !this.getAuthService().isAuthed &&
        errorText === AUTHENTICATION_REQUIRED
      ) {
        // if not authed previously, this is trying to access auth required APIs
        // show auth required alert
        this.handleAuthRequired();
      } else if (
        response.status === 403 &&
        this.getAuthService().isAuthed &&
        errorText === AUTHENTICATION_REQUIRED
      ) {
        // The app was logged at one point and is now getting auth errors.
        // This indicates the auth token may no longer valid.
        // Re-check on auth
        this.getAuthService().clearCache();
        this.restApiService.getLoggedIn();
      } else if (!this.shouldSuppressError(errorText)) {
        const trace =
          response.headers && response.headers.get('X-Gerrit-Trace');
        if (response.status === 404) {
          this.showNotFoundMessageWithTip({
            status,
            statusText,
            errorText,
            url,
            trace,
          });
        } else if (response.status === 429) {
          this.showQuotaExceeded({status, statusText});
        } else {
          this.showErrorDialog(
            constructServerErrorMsg({
              status,
              statusText,
              errorText,
              url,
              trace,
            })
          );
        }
      }
      this.reporting.error('Server error', new Error(errorText));
    });
  };

  private showNotFoundMessageWithTip({
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
      this.showErrorDialog(
        constructServerErrorMsg({
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

  private showQuotaExceeded({status, statusText}: ErrorMsg) {
    const tip = 'Try again later';
    const errorText = 'Too many requests from this client';
    this.showErrorDialog(
      constructServerErrorMsg({
        status,
        statusText,
        errorText,
        tip,
      })
    );
  }

  private readonly handleShowAlert = (e: CustomEvent<ShowAlertEventDetail>) => {
    this._showAlert(
      e.detail.message,
      e.detail.action,
      e.detail.callback,
      e.detail.dismissOnNavigation,
      undefined,
      e.detail.showDismiss
    );
  };

  private readonly handleNetworkError = (e: NetworkErrorEvent) => {
    this._showAlert('Server unavailable');
    this.reporting.error('Network error', new Error(e.detail.error.message));
  };

  // TODO(dhruvsri): allow less priority alerts to override high priority alerts
  // In some use cases we may want generic alerts to show along/over errors
  // private but used in tests
  canOverride(incoming = ErrorType.GENERIC, existing = ErrorType.GENERIC) {
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
    if (this.alertElement) {
      // check priority before hiding
      if (!this.canOverride(type, this.alertElement.type)) return;
      this.hideAlert();
    }

    this.clearHideAlertHandle();
    if (dismissOnNavigation) {
      // Persist alert until navigation.
      document.addEventListener('location-change', this.hideAlert);
    } else {
      this.hideAlertHandle = window.setTimeout(
        this.hideAlert,
        HIDE_ALERT_TIMEOUT_MS
      );
    }
    const el = this.createToastAlert(showDismiss);
    el.show(text, actionText, actionCallback);
    this.alertElement = el;
    fireIronAnnounce(this, `Alert: ${text}`);
    this.reporting.reportInteraction(EventType.SHOW_ALERT, {text});
  }

  private readonly hideAlert = () => {
    if (!this.alertElement) {
      return;
    }

    this.alertElement.hide();
    this.alertElement = null;

    // Remove listener for page navigation, if it exists.
    document.removeEventListener('location-change', this.hideAlert);
  };

  private clearHideAlertHandle() {
    if (this.hideAlertHandle !== null) {
      window.clearTimeout(this.hideAlertHandle);
      this.hideAlertHandle = null;
    }
  }

  // private but used in tests
  showAuthErrorAlert(errorText: string, actionText?: string) {
    // hide any existing alert like `reload`
    // as auth error should have the highest priority
    if (this.alertElement) {
      this.alertElement.hide();
    }

    this.alertElement = this.createToastAlert();
    this.alertElement.type = ErrorType.AUTH;
    this.alertElement.show(errorText, actionText, () =>
      this.createLoginPopup()
    );
    fireIronAnnounce(this, errorText);
    this.reporting.reportInteraction('show-auth-error', {text: errorText});
    this.refreshingCredentials = true;
    this.requestCheckLoggedIn();
    if (!document.hidden) {
      this.handleVisibilityChange();
    }
  }

  // private but used in tests
  createToastAlert(showDismiss?: boolean) {
    const el = document.createElement('gr-alert');
    el.owner = this;
    el.toast = true;
    el.showDismiss = !!showDismiss;
    return el;
  }

  private readonly handleVisibilityChange = () => {
    // Ignore when the page is transitioning to hidden (or hidden is undefined).
    if (document.hidden !== false) return;

    // If not currently refreshing credentials and the credentials are old,
    // request them to confirm their validity or (display an auth toast if it
    // fails).
    const timeSinceLastCheck = Date.now() - this.lastCredentialCheck;
    if (
      !this.refreshingCredentials &&
      this.knownAccountId !== undefined &&
      timeSinceLastCheck > STALE_CREDENTIAL_THRESHOLD_MS
    ) {
      this.reporting.reportInteraction('visibility-sign-in-check');
      this.lastCredentialCheck = Date.now();

      // check auth status in case:
      // - user signed out
      // - user switched account
      this.checkSignedIn();
    }
  };

  // private but used in tests
  requestCheckLoggedIn() {
    this.checkLoggedInTask = debounce(
      this.checkLoggedInTask,
      () => this.checkSignedIn(),
      CHECK_SIGN_IN_INTERVAL_MS
    );
  }

  // private but used in tests
  checkSignedIn() {
    this.lastCredentialCheck = Date.now();

    // force to refetch account info
    this.restApiService.invalidateAccountsCache();
    this.getAuthService().clearCache();

    this.restApiService.getLoggedIn().then(isLoggedIn => {
      if (!this.refreshingCredentials) return;

      if (!isLoggedIn) {
        // check later
        // 1. guest mode
        // 2. or signed out
        // in case #2, auth-error is taken care of separately
        this.requestCheckLoggedIn();
      } else {
        this.restApiService.getAccount().then(account => {
          if (this.refreshingCredentials) {
            // If the credentials were refreshed but the account is different,
            // then reload the page completely.
            if (account?._account_id !== this.knownAccountId) {
              this.reporting.reportInteraction('sign-in-window-reload', {
                oldAccount: !!this.knownAccountId,
                newAccount: !!account?._account_id,
              });
              this.reloadPage();
              return;
            }

            this.handleCredentialRefreshed();
          }
        });
      }
    });
  }

  reloadPage() {
    windowLocationReload();
  }

  private createLoginPopup() {
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
    window.addEventListener('focus', this.handleWindowFocus);
  }

  // private but used in tests
  handleCredentialRefreshed() {
    window.removeEventListener('focus', this.handleWindowFocus);
    this.refreshingCredentials = false;
    this.hideAlert();
    this._showAlert('Credentials refreshed.');
    this.signInModal.close();

    // Clear the cache for auth
    this.getAuthService().clearCache();
  }

  private readonly handleWindowFocus = () => {
    this.checkLoggedInTask?.flush();
  };

  private readonly handleShowErrorDialog = (e: ShowErrorEvent) => {
    this.showErrorDialog(e.detail.message);
  };

  // private but used in tests
  showErrorDialog(message: string, options?: {showSignInButton?: boolean}) {
    this.reporting.reportErrorDialog(message);
    this.errorDialog.text = message;
    this.errorDialog.showSignInButton = !!options && !!options.showSignInButton;
    this.errorModal.showModal();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-error-manager': GrErrorManager;
  }
}
