/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {EventType, PluginApi} from '../../../api/plugin';
import {AdminPluginApi, MenuLink} from '../../../api/admin';
import {getAppContext} from '../../../services/app-context';

/**
 * GrAdminApi class.
 *
 * Defines common methods to register / retrieve menu links.
 */
export class GrAdminApi implements AdminPluginApi {
  // TODO(TS): maybe define as enum if its a limited set
  private menuLinks: MenuLink[] = [];

  private readonly reporting = getAppContext().reportingService;

  constructor(private readonly plugin: PluginApi) {
    this.reporting.trackApi(this.plugin, 'admin', 'constructor');
    this.plugin.on(EventType.ADMIN_MENU_LINKS, this);
  }

  addMenuLink(text: string, url: string, capability?: string) {
    this.reporting.trackApi(this.plugin, 'admin', 'addMenuLink');
    this.menuLinks.push({text, url, capability: capability || null});
  }

  getMenuLinks(): MenuLink[] {
    this.reporting.trackApi(this.plugin, 'admin', 'getMenuLinks');
    return this.menuLinks.slice(0);
  }
}
