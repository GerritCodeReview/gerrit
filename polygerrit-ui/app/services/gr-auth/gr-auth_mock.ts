/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {EventEmitterService} from '../gr-event-interface/gr-event-interface';
import {
  AuthRequestInit,
  AuthService,
  AuthStatus,
  DefaultAuthOptions,
  GetTokenCallback,
} from './gr-auth';
import {Auth} from './gr-auth_impl';

export class GrAuthMock implements AuthService {
  baseUrl = '';

  private _status = AuthStatus.UNDETERMINED;

  public eventEmitter: EventEmitterService;

  constructor(eventEmitter: EventEmitterService) {
    this.eventEmitter = eventEmitter;
  }

  get isAuthed() {
    return this._status === Auth.STATUS.AUTHED;
  }

  finalize() {}

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

  authCheck() {
    return this.fetch(`${this.baseUrl}/auth-check`).then(res => {
      if (res.status === 204) {
        this._setStatus(Auth.STATUS.AUTHED);
        return true;
      } else {
        this._setStatus(Auth.STATUS.NOT_AUTHED);
        return false;
      }
    });
  }

  clearCache() {}

  setup(_getToken: GetTokenCallback, _defaultOptions: DefaultAuthOptions) {}

  fetch(_url: string, _opt_options?: AuthRequestInit): Promise<Response> {
    return Promise.resolve(new Response());
  }
}
