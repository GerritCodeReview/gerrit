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
import {getBaseUrl} from '../../utils/url-util';
import {EventEmitterService} from '../gr-event-interface/gr-event-interface';
import {Finalizable} from '../registry';
import {
  AuthRequestInit,
  AuthService,
  AuthStatus,
  AuthType,
  DefaultAuthOptions,
  GetTokenCallback,
  Token,
} from './gr-auth';

export const MAX_AUTH_CHECK_WAIT_TIME_MS = 1000 * 30; // 30s
const MAX_GET_TOKEN_RETRIES = 2;

interface ValidToken extends Token {
  access_token: string;
  expires_at: string;
}

interface AuthRequestInitWithHeaders extends AuthRequestInit {
  // RequestInit define headers as optional property with a type
  // Headers | string[][] | Record<string, string>
  // In Auth class headers property is always set and has type Headers
  headers: Headers;
}

/**
 * Auth class.
 */
export class Auth implements AuthService, Finalizable {
  // TODO(dmfilippov): Remove Type and Status properties, expose AuthType and
  // AuthStatus to API
  static TYPE = {
    XSRF_TOKEN: AuthType.XSRF_TOKEN,
    ACCESS_TOKEN: AuthType.ACCESS_TOKEN,
  };

  static STATUS = {
    UNDETERMINED: AuthStatus.UNDETERMINED,
    AUTHED: AuthStatus.AUTHED,
    NOT_AUTHED: AuthStatus.NOT_AUTHED,
    ERROR: AuthStatus.ERROR,
  };

  static CREDS_EXPIRED_MSG = 'Credentials expired.';

  private authCheckPromise?: Promise<boolean>;

  private _last_auth_check_time: number = Date.now();

  private _status = AuthStatus.UNDETERMINED;

  private retriesLeft = MAX_GET_TOKEN_RETRIES;

  private cachedTokenPromise: Promise<Token | null> | null = null;

  private type?: AuthType;

  private defaultOptions: AuthRequestInit = {};

  private getToken: GetTokenCallback;

  public eventEmitter: EventEmitterService;

  constructor(eventEmitter: EventEmitterService) {
    this.getToken = () => Promise.resolve(this.cachedTokenPromise);
    this.eventEmitter = eventEmitter;
  }

  get baseUrl() {
    return getBaseUrl();
  }

  finalize() {}

  /**
   * Returns if user is authed or not.
   */
  authCheck(): Promise<boolean> {
    if (
      !this.authCheckPromise ||
      Date.now() - this._last_auth_check_time > MAX_AUTH_CHECK_WAIT_TIME_MS
    ) {
      // Refetch after last check expired
      this.authCheckPromise = fetch(`${this.baseUrl}/auth-check`)
        .then(res => {
          // Make a call that requires loading the body of the request. This makes it so that the browser
          // can close the request even though callers of this method might only ever read headers.
          // See https://stackoverflow.com/questions/45816743/how-to-solve-this-caution-request-is-not-finished-yet-in-chrome
          try {
            res.clone().text();
          } catch {
            // Ignore error
          }

          // auth-check will return 204 if authed
          // treat the rest as unauthed
          if (res.status === 204) {
            this._setStatus(Auth.STATUS.AUTHED);
            return true;
          } else {
            this._setStatus(Auth.STATUS.NOT_AUTHED);
            return false;
          }
        })
        .catch(() => {
          this._setStatus(AuthStatus.ERROR);
          // Reset authCheckPromise to avoid caching the failed promise
          this.authCheckPromise = undefined;
          return false;
        });
      this._last_auth_check_time = Date.now();
    }

    return this.authCheckPromise;
  }

  clearCache() {
    this.authCheckPromise = undefined;
  }

  private _setStatus(status: AuthStatus) {
    if (this._status === status) return;

    if (this._status === AuthStatus.AUTHED) {
      this.eventEmitter.emit('auth-error', {
        message: Auth.CREDS_EXPIRED_MSG,
        action: 'Refresh credentials',
      });
    }
    this._status = status;
  }

  get status() {
    return this._status;
  }

  get isAuthed() {
    return this._status === Auth.STATUS.AUTHED;
  }

  /**
   * Enable cross-domain authentication using OAuth access token.
   */
  setup(getToken: GetTokenCallback, defaultOptions: DefaultAuthOptions) {
    this.retriesLeft = MAX_GET_TOKEN_RETRIES;
    if (getToken) {
      this.type = AuthType.ACCESS_TOKEN;
      this.cachedTokenPromise = null;
      this.getToken = getToken;
    }
    this.defaultOptions = {};
    if (defaultOptions) {
      this.defaultOptions.credentials = defaultOptions.credentials;
    }
  }

  /**
   * Perform network fetch with authentication.
   */
  fetch(url: string, opt_options?: AuthRequestInit): Promise<Response> {
    const options: AuthRequestInitWithHeaders = {
      headers: new Headers(),
      ...this.defaultOptions,
      ...opt_options,
    };
    if (this.type === AuthType.ACCESS_TOKEN) {
      return this._getAccessToken().then(accessToken =>
        this._fetchWithAccessToken(url, options, accessToken)
      );
    } else {
      return this._fetchWithXsrfToken(url, options);
    }
  }

  // private but used in test
  _getCookie(name: string): string {
    const key = name + '=';
    let result = '';
    document.cookie.split(';').some(c => {
      c = c.trim();
      if (c.startsWith(key)) {
        result = c.substring(key.length);
        return true;
      }
      return false;
    });
    return result;
  }

  // private but used in test
  _isTokenValid(token: Token | null): token is ValidToken {
    if (!token) {
      return false;
    }
    if (!token.access_token || !token.expires_at) {
      return false;
    }

    const expiration = new Date(Number(token.expires_at) * 1000);
    if (Date.now() >= expiration.getTime()) {
      return false;
    }

    return true;
  }

  private _fetchWithXsrfToken(
    url: string,
    options: AuthRequestInitWithHeaders
  ): Promise<Response> {
    if (options.method && options.method !== 'GET') {
      const token = this._getCookie('XSRF_TOKEN');
      if (token) {
        options.headers.append('X-Gerrit-Auth', token);
      }
    }
    options.credentials = 'same-origin';
    return this._ensureBodyLoaded(fetch(url, options));
  }

  private _getAccessToken(): Promise<string | null> {
    if (!this.cachedTokenPromise) {
      this.cachedTokenPromise = this.getToken();
    }
    return this.cachedTokenPromise.then(token => {
      if (this._isTokenValid(token)) {
        this.retriesLeft = MAX_GET_TOKEN_RETRIES;
        return token.access_token;
      }
      if (this.retriesLeft > 0) {
        this.retriesLeft--;
        this.cachedTokenPromise = null;
        return this._getAccessToken();
      }
      // Fall back to anonymous access.
      return null;
    });
  }

  private _fetchWithAccessToken(
    url: string,
    options: AuthRequestInitWithHeaders,
    accessToken: string | null
  ): Promise<Response> {
    const params = [];

    if (accessToken) {
      params.push(`access_token=${accessToken}`);
      const baseUrl = this.baseUrl;
      const pathname = baseUrl
        ? url.substring(url.indexOf(baseUrl) + baseUrl.length)
        : url;
      if (!pathname.startsWith('/a/')) {
        url = url.replace(pathname, '/a' + pathname);
      }
    }

    const method = options.method || 'GET';
    let contentType = options.headers.get('Content-Type');

    // For all requests with body, ensure json content type.
    if (!contentType && options.body) {
      contentType = 'application/json';
    }

    if (method !== 'GET') {
      options.method = 'POST';
      params.push(`$m=${method}`);
      // If a request is not GET, and does not have a body, ensure text/plain
      // content type.
      if (!contentType) {
        contentType = 'text/plain';
      }
    }

    if (contentType) {
      options.headers.set('Content-Type', 'text/plain');
      params.push(`$ct=${encodeURIComponent(contentType)}`);
    }

    if (params.length) {
      url = url + (url.indexOf('?') === -1 ? '?' : '&') + params.join('&');
    }
    return this._ensureBodyLoaded(fetch(url, options));
  }

  private _ensureBodyLoaded(response: Promise<Response>): Promise<Response> {
    return response.then(response => {
      if (!response.ok) {
        // Make a call that requires loading the body of the request. This makes it so that the browser
        // can close the request even though callers of this method might only ever read headers.
        // See https://stackoverflow.com/questions/45816743/how-to-solve-this-caution-request-is-not-finished-yet-in-chrome
        try {
          response.clone().text();
        } catch {
          // Ignore error
        }
      }
      return response;
    });
  }
}
