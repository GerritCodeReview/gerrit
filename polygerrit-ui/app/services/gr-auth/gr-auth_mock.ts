/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {AuthRequestInit} from '../../types/types';
import {fire} from '../../utils/event-util';
import {AuthService} from './gr-auth';

enum AuthStatus {
  UNDETERMINED = 0,
  AUTHED = 1,
  NOT_AUTHED = 2,
  ERROR = 3,
}

export class GrAuthMock implements AuthService {
  private _status = AuthStatus.UNDETERMINED;

  get isAuthed() {
    return this._status === AuthStatus.AUTHED;
  }

  finalize() {}

  private _setStatus(status: AuthStatus) {
    if (this._status === status) return;
    if (this._status === AuthStatus.AUTHED) {
      fire(document, 'auth-error', {
        message: 'Credentials expired.',
        action: 'Refresh credentials',
      });
    }
    this._status = status;
  }

  get status() {
    return this._status;
  }

  authCheck() {
    return this.fetch('/auth-check').then(res => {
      if (res.status === 204) {
        this._setStatus(AuthStatus.AUTHED);
        return true;
      } else {
        this._setStatus(AuthStatus.NOT_AUTHED);
        return false;
      }
    });
  }

  clearCache() {}

  fetch(_url: string, _options?: AuthRequestInit): Promise<Response> {
    return Promise.resolve(new Response());
  }
}
