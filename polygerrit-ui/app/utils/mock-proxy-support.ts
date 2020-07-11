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

interface ProxyRegistry {
  mockProxy: {[name: string]: Function};
  add<T extends (...args: any[]) => any>(func: T): T;
}

class TestProxyRegistry implements ProxyRegistry {
  public mockProxy: {[name: string]: Function} = {};

  add<T extends (...args: any[]) => any>(func: T): T {
    this.mockProxy[func.name.slice(1)] = func;
    const mockFunc = (...args: any[]) =>
      this.mockProxy[func.name.slice(1)].call(null, ...args);
    return mockFunc as T;
  }
}

class AppProxyRegistry implements ProxyRegistry {
  public mockProxy = {};

  add<T extends (...args: any[]) => any>(func: T): T {
    return func;
  }
}

const isTestRun = '__karma__' in window;

export function createProxyRegistry(): ProxyRegistry {
  return isTestRun ? new TestProxyRegistry() : new AppProxyRegistry();
}
