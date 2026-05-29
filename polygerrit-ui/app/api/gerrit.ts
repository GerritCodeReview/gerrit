/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginApi} from './plugin';
import {Styles} from './styles';

declare global {
  interface Window {
    Gerrit: Gerrit;
    VERSION_INFO?: string;
    ENABLED_EXPERIMENTS?: string[];
  }
}

export declare interface Gerrit {
  install(
    callback: (plugin: PluginApi) => void,
    version?: string,
    src?: string
  ): void;
  styles: Styles;
}
