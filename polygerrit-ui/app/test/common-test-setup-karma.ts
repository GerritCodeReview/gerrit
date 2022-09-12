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

let originalOnBeforeUnload: typeof window.onbeforeunload;

suiteSetup(() => {
  // This suiteSetup() method is called only once before all tests

  // Can't use window.addEventListener("beforeunload",...) here,
  // the handler is raised too late.
  originalOnBeforeUnload = window.onbeforeunload;
  window.onbeforeunload = function (e: BeforeUnloadEvent) {
    // If a test reloads a page, we can't prevent it.
    // However we can print an error and the stack trace with assert.fail
    try {
      throw new Error();
    } catch (e) {
      console.error('Page reloading attempt detected.');
      if (e instanceof Error) {
        console.error(e.stack?.toString());
      }
    }
    if (originalOnBeforeUnload) {
      originalOnBeforeUnload.call(window, e);
    }
  };
});

suiteTeardown(() => {
  // This suiteTeardown() method is called only once after all tests
  window.onbeforeunload = originalOnBeforeUnload;
});

window.testResolver = testResolverImpl;
