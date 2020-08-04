/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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


/** Interface for menu link */
export interface MenuLink {
  text: string;
  url: string;
  capability: string | null;
}

// TODO(TS): replace with Plugin once gr-public-js-api migrated
interface PluginApi {
  on(eventName: string, adminApi: GrAdminApi): void;
}

/**
 * GrAdminApi class.
 *
 * Defines common methods to register / retrieve menu links.
 */
export class GrAdminApi {
  // TODO(TS): maybe define as enum if its a limited set
  private menuLinks: MenuLink[] = [];

  constructor(private readonly plugin: PluginApi) {
    this.plugin.on('admin-menu-links', this);
  }

  addMenuLink(text: string, url: string, capability?: string) {
    this.menuLinks.push({text, url, capability: capability || null});
  }

  getMenuLinks(): MenuLink[] {
    return this.menuLinks.slice(0);
  }
}
