/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import 'polymer-resin/standalone/polymer-resin';

export type SafeTypeBridge = (
  value: unknown,
  type: string,
  fallback: unknown
) => unknown;

export type ReportHandler = (
  isDisallowedValue: boolean,
  printfFormatString: string,
  ...printfArgs: unknown[]
) => void;

declare global {
  interface Window {
    security: {
      polymer_resin: {
        SafeType: {
          CONSTANT: string;
          HTML: string;
          JAVASCRIPT: string;
          RESOURCE_URL: string;
          /** Unprivileged but possibly wrapped string. */
          STRING: string;
          STYLE: string;
          URL: string;
        };
        CONSOLE_LOGGING_REPORT_HANDLER: ReportHandler;
        install(options: {
          UNSAFE_passThruDisallowedValues?: boolean;
          allowedIdentifierPrefixes?: string[];
          reportHandler?: ReportHandler;
          safeTypesBridge?: SafeTypeBridge;
        }): void;
      };
    };
  }
}

const security = window.security;

export const _testOnly_defaultResinReportHandler =
  security.polymer_resin.CONSOLE_LOGGING_REPORT_HANDLER;

export function installPolymerResin(
  safeTypesBridge: SafeTypeBridge,
  reportHandler = security.polymer_resin.CONSOLE_LOGGING_REPORT_HANDLER
) {
  window.security.polymer_resin.install({
    allowedIdentifierPrefixes: [''],
    reportHandler,
    safeTypesBridge,
  });
}
