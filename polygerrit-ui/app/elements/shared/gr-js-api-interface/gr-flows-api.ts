/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PluginsModel} from '../../../models/plugins/plugins-model';
import {FlowsPluginApi, FlowsProvider} from '../../flows';

export class GrFlowsApi implements FlowsPluginApi {
  constructor(private readonly plugins: PluginsModel) {}

  register(provider: FlowsProvider): void {
    this.plugins.registerFlowsProvider(provider);
  }
}
