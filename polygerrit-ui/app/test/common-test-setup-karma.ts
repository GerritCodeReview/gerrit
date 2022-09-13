/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {testResolver as testResolverImpl} from './common-test-setup';

declare global {
  interface Window {
    testResolver: typeof testResolverImpl;
  }
  let testResolver: typeof testResolverImpl;
}

window.testResolver = testResolverImpl;
