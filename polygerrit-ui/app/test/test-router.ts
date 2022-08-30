/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritNav} from '../elements/core/gr-navigation/gr-navigation';
import {RouterModel} from '../services/router/router-model';

GerritNav.setup(
  () => {
    /* noop */
  },
  () => '',
  () => [],
  () => {
    return {};
  },
  {} as RouterModel
);
