// Copyright (C) 2019 The Android Open Source Project
//
// Licensed un  der the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

export class CommandLineParser {
  public static createStringArrayOption(optionName: string, help: string, defaultValue: string[]): CommandLineArgument {
    return new StringArrayOption(optionName, help, defaultValue);
  }
  public static createBooleanOption(optionName: string, help: string, defaultValue: boolean): CommandLineArgument {
    return new BooleanOption(optionName, help, defaultValue);
  }
  public static createStringOption(optionName: string, help: string, defaultValue: string | null): CommandLineArgument {
    return new StringOption(optionName, help, defaultValue);
  }

  public constructor(private readonly argumentTypes: {[name: string]: CommandLineArgument}) {
  }
  public parse(argv: string[]): object {
    const result = Object.assign({});
    let index = 2; //argv[0] - node interpreter, argv[1] - index.js
    for(const argumentField in this.argumentTypes) {
      result[argumentField] = this.argumentTypes[argumentField].getDefaultValue();
    }
    while(index < argv.length) {
      let knownArgument = false;
      for(const argumentField in this.argumentTypes) {
        const argumentType = this.argumentTypes[argumentField];
        const argumentValue = argumentType.tryRead(argv, index);
        if(argumentValue) {
          knownArgument = true;
          index += argumentValue.consumed;
          result[argumentField] = argumentValue.value;
          break;
        }
      }
      if(!knownArgument) {
        throw new Error(`Unknown argument ${argv[index]}`);
      }
    }
    return result;
  }
}

interface CommandLineArgumentReadResult {
  consumed: number;
  value: any;
}

export interface CommandLineArgument {
  getDefaultValue(): any;
  tryRead(argv: string[], startIndex: number): CommandLineArgumentReadResult | null;
}

abstract class CommandLineOption implements CommandLineArgument {
  protected constructor(protected readonly optionName: string, protected readonly help: string, private readonly defaultValue: any) {
  }
  public tryRead(argv: string[], startIndex: number): CommandLineArgumentReadResult | null  {
    if(argv[startIndex] !== "--" + this.optionName) {
      return null;
    }
    const readArgumentsResult = this.readArguments(argv, startIndex + 1);
    if(!readArgumentsResult) {
      return null;
    }
    readArgumentsResult.consumed++; // Add option name
    return readArgumentsResult;
  }
  public getDefaultValue(): any {
    return this.defaultValue;
  }

  protected abstract readArguments(argv: string[], startIndex: number) : CommandLineArgumentReadResult | null;
}

class StringArrayOption extends CommandLineOption {
  public constructor(optionName: string, help: string, defaultValue: string[]) {
    super(optionName, help, defaultValue);
  }

  protected readArguments(argv: string[], startIndex: number): CommandLineArgumentReadResult {
    const result = [];
    let index = startIndex;
    while(index < argv.length) {
      if(argv[index].startsWith("--")) {
        break;
      }
      result.push(argv[index]);
      index++;
    }
    return {
      consumed: index - startIndex,
      value: result
    }
  }
}

class BooleanOption extends CommandLineOption {
  public constructor(optionName: string, help: string, defaultValue: boolean) {
    super(optionName, help, defaultValue);
  }

  protected readArguments(argv: string[], startIndex: number): CommandLineArgumentReadResult {
    return {
      consumed: 0,
      value: true
    }
  }
}

class StringOption extends CommandLineOption {
  public constructor(optionName: string, help: string, defaultValue: string | null) {
    super(optionName, help, defaultValue);
  }

  protected readArguments(argv: string[], startIndex: number): CommandLineArgumentReadResult | null {
    if(startIndex >= argv.length) {
      return null;
    }
    return {
      consumed: 1,
      value: argv[startIndex]
    }
  }
}
