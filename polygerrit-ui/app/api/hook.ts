/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ChangeInfo, ConfigInfo, RevisionInfo} from './rest-api';
import {PluginApi} from './plugin';

export declare interface GerritElementExtensions {
  content?: HTMLElement;
  change?: ChangeInfo;
  revision?: RevisionInfo;
  token?: string;
  repoName?: string;
  config?: ConfigInfo;
  plugin?: PluginApi;
}

export type PluginElement = HTMLElement & GerritElementExtensions;

export type HookCallback<T extends PluginElement> = (el: T) => void;

export declare interface RegisterOptions {
  /** Defaults to empty string. */
  slot?: string;
  /** Defaults to false. */
  replace?: boolean;
  /**
   * Optional sort order for components registered on the same endpoint or slot.
   * Lower numbers are placed first; components without `order` are placed after
   * ordered components.
   */
  order?: number;
}

export declare interface HookApi<T extends PluginElement> {
  onAttached(callback: HookCallback<T>): HookApi<T>;

  onDetached(callback: HookCallback<T>): HookApi<T>;

  getAllAttached(): HTMLElement[];

  getLastAttached(): Promise<HTMLElement>;

  getModuleName(): string;

  handleInstanceDetached(instance: HTMLElement): void;

  handleInstanceAttached(instance: HTMLElement): void;
}
