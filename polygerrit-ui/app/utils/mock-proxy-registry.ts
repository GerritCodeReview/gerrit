/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

/**
 * This file contains a code for mocking ES6-modules function in tests.
 * Usages:
 * 1) In the ES6-module with the function which you want to mock:
 *    a) import mock-proxy-registry and create registry and a mockProxy const:
 *       // File my-util.js, we want to mock
 *       // imports ...
 *       import ...
 *       import {createMockProxyRegistry} from './mock-proxy-registry.js';
 *       import ...
 *       // Right after all imports (so, it is easy to find)
 *       const mockProxyRegistry = createMockProxyRegistry();
 *       export const myUtilMockProxy = mockProxyRegistry.mockProxy;
 *
 *    c) Add '_' prefix to the function name and remove 'export' modifier from
 *       the function. Right after the function export const like in the
 *       following example:
 *       Before:
 *         export function myFunction(x, y, z) {...}
 *         export function otherFunction(x, y) {
 *       After:
 *         function _myFunction(x, y, z) {...}
 *         export const myFunction = mockProxyRegistry.add(_myFunction)
 *
 *         function _otherFunction(x, y) {...}
 *         export const otherFunction = mockProxyRegistry.add(_otherFunction)
 *
 *  2) In a test file, import mockProxy from the ES6-module. Then you can mock
 *     functions as usual:
 *        import {myUtilMockProxy} from './my-util.js';
 *        ...
 *        const myStub = sinon.stub(myUtilMockProxy, 'myFunction');
 *        ...
 *        assert.isTrue(myStub.called);
 *
 *     Note:
 *       You must always use object returned from sinon.stub, sinon.spy, etc...
 *       methods. You can't check anything directly on function
 *         assert.isTrue(myFunction.called); // Doesn't work, use myStub instead
 *
 *  In the real app, the AppProxyRegistry is used and it doesn't add any
 *  noticable overhead.
 */

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type ProxiedFunction = (...args: any[]) => any;

interface ProxyRegistry {
  mockProxy: {[name: string]: Function};

  // Method returns function with the same type as func!
  // Do not change to <T extends Function> - after the change, the return type
  // is Function and you lost information about 'func' arguments and return type
  add<T extends ProxiedFunction>(func: T): T;
}

class TestProxyRegistry implements ProxyRegistry {
  public mockProxy: {[name: string]: Function} = {};

  add<T extends ProxiedFunction>(func: T): T {
    this.mockProxy[func.name.slice(1)] = func;
    const mockFunc = (...args: any[]) =>
      this.mockProxy[func.name.slice(1)].call(null, ...args);
    return mockFunc as T;
  }
}

class AppProxyRegistry implements ProxyRegistry {
  public mockProxy = {};

  add<T extends ProxiedFunction>(func: T): T {
    return func;
  }
}

const isTestRun = '__karma__' in window;

export function createMockProxyRegistry(): ProxyRegistry {
  return isTestRun ? new TestProxyRegistry() : new AppProxyRegistry();
}
