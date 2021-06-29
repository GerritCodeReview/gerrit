/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
