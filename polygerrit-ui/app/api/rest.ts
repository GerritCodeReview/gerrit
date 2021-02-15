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
import {HttpMethod} from '../constants/constants';
import {
  AccountCapabilityInfo,
  AccountDetailInfo,
  ParsedJSON,
  ProjectInfoWithName,
  RequestPayload,
  ServerInfo,
} from '../types/common';

export type ErrorCallback = (response?: Response | null, err?: Error) => void;

export interface RestPluginApi {
  getLoggedIn(): Promise<boolean>;

  getVersion(): Promise<string | undefined>;

  getConfig(): Promise<ServerInfo | undefined>;

  invalidateReposCache(): void;

  getAccount(): Promise<AccountDetailInfo | undefined>;

  getAccountCapabilities(
    capabilities: string[]
  ): Promise<AccountCapabilityInfo | undefined>;

  getRepos(
    filter: string,
    reposPerPage: number,
    offset?: number
  ): Promise<ProjectInfoWithName[] | undefined>;

  fetch(
    method: HttpMethod,
    url: string,
    payload?: RequestPayload,
    errFn?: undefined,
    contentType?: string
  ): Promise<Response>;

  fetch(
    method: HttpMethod,
    url: string,
    payload: RequestPayload | undefined,
    errFn: ErrorCallback,
    contentType?: string
  ): Promise<Response | void>;

  fetch(
    method: HttpMethod,
    url: string,
    payload: RequestPayload | undefined,
    errFn?: ErrorCallback,
    contentType?: string
  ): Promise<Response | void>;

  /**
   * Fetch and return native browser REST API Response.
   */
  fetch(
    method: HttpMethod,
    url: string,
    payload?: RequestPayload,
    errFn?: ErrorCallback,
    contentType?: string
  ): Promise<Response | void>;

  /**
   * Fetch and parse REST API response, if request succeeds.
   */
  send(
    method: HttpMethod,
    url: string,
    payload?: RequestPayload,
    errFn?: ErrorCallback,
    contentType?: string
  ): Promise<ParsedJSON>;

  get(url: string): Promise<ParsedJSON>;

  post(
    url: string,
    payload?: RequestPayload,
    errFn?: ErrorCallback,
    contentType?: string
  ): Promise<ParsedJSON>;

  put(
    url: string,
    payload?: RequestPayload,
    errFn?: ErrorCallback,
    contentType?: string
  ): Promise<ParsedJSON>;

  delete(url: string): Promise<Response>;
}
