/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {define} from '../../models/dependency';
import {AuthRequestInit, Finalizable} from '../../types/types';

export const authServiceToken = define<AuthService>('auth-service');

export interface AuthService extends Finalizable {
  isAuthed: boolean;

  /**
   * Returns if user is authed or not.
   */
  authCheck(): Promise<boolean>;

  clearCache(): void;

  /**
   * Perform network fetch with authentication.
   */
  fetch(url: string, options?: AuthRequestInit): Promise<Response>;
}
