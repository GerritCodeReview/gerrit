/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  AvatarPluginApi,
  AvatarProvider,
  registerAvatarProvider,
} from '../../../api/avatar';
import {PluginApi} from '../../../api/plugin';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';

export class GrAvatarApi implements AvatarPluginApi {
  constructor(
    private readonly reporting: ReportingService,
    private readonly plugin: PluginApi
  ) {}

  registerAvatarProvider(provider: AvatarProvider) {
    this.reporting.trackApi(this.plugin, 'avatar', 'registerAvatarProvider');
    registerAvatarProvider(provider);
  }
}
