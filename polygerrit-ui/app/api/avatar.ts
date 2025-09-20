/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {AccountInfo} from './rest-api';

export type AvatarProvider = (account: AccountInfo) => string | undefined;

export declare interface AvatarPluginApi {
  registerAvatarProvider(provider: AvatarProvider): void;
}

const avatarProviders: AvatarProvider[] = [];

export function registerAvatarProvider(provider: AvatarProvider) {
  avatarProviders.push(provider);
}

export function getAvatarProviders(): readonly AvatarProvider[] {
  return avatarProviders;
}
