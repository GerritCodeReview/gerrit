/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {testResolver as testResolverImpl} from './common-test-setup';
import {flush} from '@polymer/polymer/lib/utils/flush';

declare global {
  interface Window {
    flush: typeof flushImpl;
    testResolver: typeof testResolverImpl;
  }
  let flush: typeof flushImpl;
  let testResolver: typeof testResolverImpl;
}

// Workaround for https://github.com/karma-runner/karma-mocha/issues/227
let unhandledError: ErrorEvent;

window.addEventListener('error', e => {
  // For uncaught error mochajs doesn't print the full stack trace.
  // We should print it ourselves.
  console.error('Uncaught error:');
  console.error(e);
  console.error(e.error.stack.toString());
  unhandledError = e;
});

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
  if (unhandledError) {
    throw unhandledError;
  }
});

// Tests can use fake timers (sandbox.useFakeTimers)
// Keep the original one for use in test utils methods.
const nativeSetTimeout = window.setTimeout;

function flushImpl(): Promise<void>;
function flushImpl(callback: () => void): void;
/**
 * Triggers a flush of any pending events, observations, etc and calls you back
 * after they have been processed if callback is passed; otherwise returns
 * promise.
 */
function flushImpl(callback?: () => void): Promise<void> | void {
  // Ideally, this function would be a call to Polymer.dom.flush, but that
  // doesn't support a callback yet
  // (https://github.com/Polymer/polymer-dev/issues/851)
  // The type is used only in one place, disable eslint warning instead of
  // creating an interface
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  flush();
  if (callback) {
    nativeSetTimeout(callback, 0);
  } else {
    return new Promise(resolve => {
      nativeSetTimeout(resolve, 0);
    });
  }
}

self.flush = flushImpl;

window.testResolver = testResolverImpl;
