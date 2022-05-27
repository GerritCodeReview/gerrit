/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

/** Interface for menu link */
export declare interface MenuLink {
  text: string;
  url: string;
  capability: string | null;
}

export declare interface AdminPluginApi {
  addMenuLink(text: string, url: string, capability?: string): void;

  getMenuLinks(): MenuLink[];
}
