/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Finalizable} from '../registry';
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

export interface AuthService extends Finalizable {
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
