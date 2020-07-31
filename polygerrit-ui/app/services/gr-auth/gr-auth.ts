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

export enum AuthType {
  XSRF_TOKEN = 'xsrf_token',
  ACCESS_TOKEN = 'access_token',
}

export enum AuthStatus {
  UNDETERMINED = 0,
  AUTHED = 1,
  NOT_AUTHED = 2,
  ERROR = 3,
}

export interface Token {
  access_token?: string;
  expires_at?: string;
}

export type GetTokenCallback = () => Promise<Token | null>;

export interface DefaultAuthOptions {
  credentials: RequestCredentials;
}

export interface AuthRequestInit extends RequestInit {
  // RequestInit define headers as HeadersInit, i.e.
  // Headers | string[][] | Record<string, string>
  // Auth class supports only Headers in options
  headers?: Headers;
}

export interface AuthService {
  baseUrl: string;
  isAuthed: boolean;

  /**
   * Returns if user is authed or not.
   */
  authCheck(): Promise<boolean>;

  clearCache(): void;

  /**
   * Enable cross-domain authentication using OAuth access token.
   */
  setup(getToken: GetTokenCallback, defaultOptions: DefaultAuthOptions): void;

  /**
   * Perform network fetch with authentication.
   */
  fetch(url: string, opt_options?: AuthRequestInit): Promise<Response>;
}
