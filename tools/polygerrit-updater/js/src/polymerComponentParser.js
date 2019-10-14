"use strict";
// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
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
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (Object.hasOwnProperty.call(mod, k)) result[k] = mod[k];
    result["default"] = mod;
    return result;
};
Object.defineProperty(exports, "__esModule", { value: true });
const ts = __importStar(require("typescript"));
const fs = __importStar(require("fs"));
const path = __importStar(require("path"));
const utils = __importStar(require("./utils"));
const tsUtils = __importStar(require("./tsUtils"));
const typescript_1 = require("typescript");
class PolymerComponentParser {
    constructor(htmlFiles) {
        this.htmlFiles = htmlFiles;
    }
    parse(jsFile) {
        return __awaiter(this, void 0, void 0, function* () {
            const sourceFile = this.parseJsFile(jsFile);
            const legacyComponent = this.tryParseLegacyComponent(sourceFile);
            if (legacyComponent) {
                return legacyComponent;
            }
            return null;
        });
    }
    parseJsFile(jsFile) {
        return ts.createSourceFile(jsFile, fs.readFileSync(jsFile).toString(), ts.ScriptTarget.ES2015, true);
    }
    tryParseLegacyComponent(sourceFile) {
        const polymerFuncCalls = [];
        function addPolymerFuncCall(node) {
            if (node.kind === ts.SyntaxKind.CallExpression) {
                const callExpression = node;
                if (callExpression.expression.kind === ts.SyntaxKind.Identifier) {
                    const identifier = callExpression.expression;
                    if (identifier.text === "Polymer") {
                        polymerFuncCalls.push(callExpression);
                    }
                }
            }
            ts.forEachChild(node, addPolymerFuncCall);
        }
        addPolymerFuncCall(sourceFile);
        if (polymerFuncCalls.length === 0) {
            return null;
        }
        if (polymerFuncCalls.length > 1) {
            throw new Error("Each .js file must contain only one Polymer component");
        }
        const parsedPath = path.parse(sourceFile.fileName);
        const htmlFullPath = path.format({
            dir: parsedPath.dir,
            name: parsedPath.name,
            ext: ".html"
        });
        if (!this.htmlFiles.has(htmlFullPath)) {
            throw new Error("Legacy .js component dosn't have associated .html file");
        }
        const polymerFuncCall = polymerFuncCalls[0];
        if (polymerFuncCall.arguments.length !== 1) {
            throw new Error("The Polymer function must be called with exactly one parameter");
        }
        const argument = polymerFuncCall.arguments[0];
        if (argument.kind !== ts.SyntaxKind.ObjectLiteralExpression) {
            throw new Error("The parameter for Polymer function must be ObjectLiteralExpression (i.e. '{...}')");
        }
        const infoArg = argument;
        return {
            type: PolymerComponentType.Legacy,
            jsFile: sourceFile.fileName,
            htmlFile: htmlFullPath,
            parsedFile: sourceFile,
            polymerFuncCallExpr: polymerFuncCalls[0],
            componentSettings: this.parseLegacyComponentSettings(infoArg),
        };
    }
    parseLegacyComponentSettings(info) {
        const props = new Map();
        for (const property of info.properties) {
            const name = property.name;
            if (name === undefined) {
                throw new Error("Property name is not defined");
            }
            switch (name.kind) {
                case ts.SyntaxKind.Identifier:
                case ts.SyntaxKind.StringLiteral:
                    if (props.has(name.text)) {
                        throw new Error(`Property ${name.text} appears more than once`);
                    }
                    props.set(name.text, property);
                    break;
                default:
                    utils.unexpetedValue(name.kind);
            }
        }
        if (props.has("_noAccessors")) {
            throw new Error("_noAccessors is not supported");
        }
        const legacyLifecycleMethods = new Map();
        for (const name of exports.LegacyLifecycleMethodsArray) {
            const methodDecl = this.getLegacyMethodDeclaration(props, name);
            if (methodDecl) {
                legacyLifecycleMethods.set(name, methodDecl);
            }
        }
        const ordinaryMethods = new Map();
        const ordinaryShorthandProperties = new Map();
        const ordinaryGetAccessors = new Map();
        const ordinaryPropertyAssignments = new Map();
        for (const [name, val] of props) {
            if (exports.RESERVED_NAMES.hasOwnProperty(name))
                continue;
            switch (val.kind) {
                case ts.SyntaxKind.MethodDeclaration:
                    ordinaryMethods.set(name, val);
                    break;
                case ts.SyntaxKind.ShorthandPropertyAssignment:
                    ordinaryShorthandProperties.set(name, val);
                    break;
                case ts.SyntaxKind.GetAccessor:
                    ordinaryGetAccessors.set(name, val);
                    break;
                case ts.SyntaxKind.PropertyAssignment:
                    ordinaryPropertyAssignments.set(name, val);
                    break;
                default:
                    throw new Error(`Unsupported element kind: ${ts.SyntaxKind[val.kind]}`);
            }
            //ordinaryMethods.set(name, tsUtils.assertNodeKind(val, ts.SyntaxKind.MethodDeclaration) as ts.MethodDeclaration);
        }
        return {
            reservedDeclarations: {
                is: tsUtils.getStringLiteralValue(this.getLegacyPropertyInitializer(props, "is")),
                _legacyUndefinedCheck: tsUtils.getBooleanLiteralValue(this.getLegacyPropertyInitializer(props, "_legacyUndefinedCheck")),
                properties: tsUtils.getObjectLiteralExpression(this.getLegacyPropertyInitializer(props, "properties")),
                behaviors: tsUtils.getArrayLiteralExpression(this.getLegacyPropertyInitializer(props, "behaviors")),
                observers: tsUtils.getArrayLiteralExpression(this.getLegacyPropertyInitializer(props, "observers")),
                listeners: tsUtils.getObjectLiteralExpression(this.getLegacyPropertyInitializer(props, "listeners")),
                hostAttributes: tsUtils.getObjectLiteralExpression(this.getLegacyPropertyInitializer(props, "hostAttributes")),
            },
            lifecycleMethods: legacyLifecycleMethods,
            ordinaryMethods: ordinaryMethods,
            ordinaryShorthandProperties: ordinaryShorthandProperties,
            ordinaryGetAccessors: ordinaryGetAccessors,
            ordinaryPropertyAssignments: ordinaryPropertyAssignments,
        };
    }
    getLegacyPropertyInitializer(props, propName) {
        const property = props.get(propName);
        if (!property) {
            return undefined;
        }
        const assignment = tsUtils.getPropertyAssignment(property);
        if (!assignment) {
            return undefined;
        }
        return assignment.initializer;
    }
    getLegacyMethodDeclaration(props, propName) {
        const property = props.get(propName);
        if (!property) {
            return undefined;
        }
        return tsUtils.assertNodeKind(property, typescript_1.SyntaxKind.MethodDeclaration);
    }
}
exports.PolymerComponentParser = PolymerComponentParser;
var PolymerComponentType;
(function (PolymerComponentType) {
    PolymerComponentType[PolymerComponentType["Legacy"] = 0] = "Legacy";
})(PolymerComponentType = exports.PolymerComponentType || (exports.PolymerComponentType = {}));
exports.LegacyLifecycleMethodsArray = ["attached", "detached", "ready", "created", "beforeRegister", "registered", "attributeChanged"];
exports.RESERVED_NAMES = {
    attached: true,
    detached: true,
    ready: true,
    created: true,
    beforeRegister: true,
    registered: true,
    attributeChanged: true,
    is: true,
    _legacyUndefinedCheck: true,
    properties: true,
    behaviors: true,
    observers: true,
    listeners: true,
    hostAttributes: true,
};
//# sourceMappingURL=polymerComponentParser.js.map