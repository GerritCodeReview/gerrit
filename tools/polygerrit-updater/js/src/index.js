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
const polymerComponentParser_1 = require("./polymerComponentParser");
const fs = __importStar(require("fs"));
const path = __importStar(require("path"));
const utils = __importStar(require("./utils"));
const ts = __importStar(require("typescript"));
const typescript_1 = require("typescript");
const tsUtils = __importStar(require("./tsUtils"));
function addFilesRecursive(dirPath, params, filter) {
    const entries = fs.readdirSync(dirPath, { withFileTypes: true });
    for (const entry of entries) {
        const dirEnt = entry;
        const fullPath = path.join(dirPath, dirEnt.name);
        if (dirEnt.isDirectory()) {
            if (!filter.includeDir(fullPath))
                continue;
            addFilesRecursive(fullPath, params, filter);
        }
        else if (dirEnt.isFile()) {
            const parsedPath = path.parse(fullPath);
            if (!filter.includeFile(parsedPath))
                continue;
            const ext = parsedPath.ext.toLowerCase();
            if (ext === ".html") {
                params.htmlFiles.add(fullPath);
            }
            if (ext === ".js") {
                params.jsFiles.add(fullPath);
            }
        }
        else {
            throw Error(`Unsupported dir entry '${entry.name}' in '${fullPath}'`);
        }
    }
}
class IncludeAllFilesFilter {
    includeDir(fullPath) {
        return true;
    }
    includeFile(fullPath) {
        //return fullPath.name.indexOf("gr-diff") >= 0;
        const exclude = ["gr-reporting", "gr-dom-hooks"].some(name => fullPath.name.indexOf(name) >= 0);
        return !exclude;
    }
}
function getParams() {
    return __awaiter(this, void 0, void 0, function* () {
        const params = {
            htmlFiles: new Set(),
            jsFiles: new Set(),
        };
        addFilesRecursive(path.resolve("../../polygerrit-ui/app"), params, new IncludeAllFilesFilter());
        return params;
    });
}
class TsTextBuilder {
    constructor() {
        this.content = "";
    }
    appendString(str) {
        this.content += str;
    }
    writeToFile(fileName) {
        fs.writeFileSync(fileName, this.content);
    }
}
function updateLegacyComponent(component) {
    return __awaiter(this, void 0, void 0, function* () {
        const builder = new TsTextBuilder();
        const fullText = component.parsedFile.text;
        builder.appendString(fullText.substring(0, component.polymerFuncCallExpr.getStart()));
        const { classDeclaration, componentRegistration } = generatePolymerV2ClassFromLegacyComponent(component);
        // //ts.visitEachChild(, child => classDeclaration, );
        const polymerFuncCallStatement = tsUtils.assertNodeKind(component.polymerFuncCallExpr.parent, typescript_1.SyntaxKind.ExpressionStatement);
        const parentBlock = tsUtils.assertNodeKind(polymerFuncCallStatement.parent, typescript_1.SyntaxKind.Block);
        const updatedBlock = ts.getMutableClone(parentBlock);
        const index = parentBlock.statements.indexOf(polymerFuncCallStatement);
        if (index < 0) {
            throw new Error("Internal error! Couldn't find statement in its own parent");
        }
        const newStatements = Array.from(parentBlock.statements);
        newStatements.splice(index, 1, classDeclaration, componentRegistration);
        updatedBlock.statements = ts.createNodeArray(newStatements);
        const replaceResult = tsUtils.replaceNode(component.parsedFile, parentBlock, updatedBlock);
        const options = {
            removeComments: false,
            newLine: ts.NewLineKind.LineFeed,
        };
        const printer = ts.createPrinter(options);
        try {
            fs.writeFileSync(component.jsFile /*+ ".new"*/, printer.printFile(replaceResult.transformed[0]));
        }
        finally {
            replaceResult.dispose();
        }
    });
}
function generatePolymerV2ClassFromLegacyComponent(component) {
    const legacySettings = component.componentSettings;
    const reservedDeclarations = legacySettings.reservedDeclarations;
    if (!reservedDeclarations.is) {
        throw new Error("Component doesn't have 'is' property");
    }
    //We want to keep the order of members, if possible. pos is the original position for a member
    const members = [];
    //Place 'is' at the top
    members.push({ pos: -1, member: createIsAccessor(reservedDeclarations.is) });
    const className = generateClassNameFromTagName(reservedDeclarations.is);
    if (reservedDeclarations.properties) {
        const returnStatement = ts.createReturn(reservedDeclarations.properties);
        const block = ts.createBlock([returnStatement]);
        const propertiesAccessor = ts.createGetAccessor(undefined, [ts.createModifier(typescript_1.SyntaxKind.StaticKeyword)], "properties", [], undefined, block);
        members.push({ pos: reservedDeclarations.properties.pos, member: propertiesAccessor });
    }
    let baseClass = ts.createExpressionWithTypeArguments([], ts.createPropertyAccess(ts.createIdentifier("Polymer"), "Element"));
    baseClass = ts.createExpressionWithTypeArguments([], ts.createCall(tsUtils.createNameExpression("Polymer.LegacyElementMixin"), [], [baseClass.expression]));
    baseClass = ts.createExpressionWithTypeArguments([], ts.createCall(tsUtils.createNameExpression("Polymer.GestureEventListeners"), [], [baseClass.expression]));
    if (reservedDeclarations._legacyUndefinedCheck) {
        baseClass = ts.createExpressionWithTypeArguments([], ts.createCall(ts.createPropertyAccess(ts.createIdentifier("Polymer"), "LegacyDataMixin"), [], [baseClass.expression]));
    }
    let heritageClauses = [];
    if (reservedDeclarations.behaviors) {
        baseClass = ts.createExpressionWithTypeArguments([], ts.createCall(ts.createPropertyAccess(ts.createIdentifier("Polymer"), "mixinBehaviors"), [], [reservedDeclarations.behaviors, baseClass.expression]));
    }
    const extendClause = ts.createHeritageClause(ts.SyntaxKind.ExtendsKeyword, [baseClass]);
    heritageClauses.push(extendClause);
    if (reservedDeclarations.observers) {
        const returnStatement = ts.createReturn(reservedDeclarations.observers);
        const block = ts.createBlock([returnStatement]);
        const propertiesAccessor = ts.createGetAccessor(undefined, [ts.createModifier(typescript_1.SyntaxKind.StaticKeyword)], "observers", [], undefined, block);
        members.push({ pos: reservedDeclarations.observers.pos, member: propertiesAccessor });
    }
    for (const name of polymerComponentParser_1.LegacyLifecycleMethodsArray) {
        let codeAtMethodStart = [];
        let codeAtMethodEnd = [];
        if (name === "created" && reservedDeclarations.listeners) {
            for (const listener of reservedDeclarations.listeners.properties) {
                const propertyAssignment = tsUtils.assertNodeKind(listener, ts.SyntaxKind.PropertyAssignment);
                if (!propertyAssignment.name) {
                    throw new Error("Listener must have event name");
                }
                let eventNameLiteral;
                if (propertyAssignment.name.kind === ts.SyntaxKind.StringLiteral) {
                    eventNameLiteral = propertyAssignment.name;
                }
                else if (propertyAssignment.name.kind === ts.SyntaxKind.Identifier) {
                    eventNameLiteral = ts.createStringLiteral(propertyAssignment.name.text);
                }
                else {
                    throw new Error(`Unsupported type ${ts.SyntaxKind[propertyAssignment.name.kind]}`);
                }
                const handlerLiteral = tsUtils.assertNodeKind(propertyAssignment.initializer, ts.SyntaxKind.StringLiteral);
                const handlerImpl = legacySettings.ordinaryMethods.get(handlerLiteral.text);
                if (!handlerImpl) {
                    throw new Error(`Can't find event handler '${handlerLiteral.text}'`);
                }
                const eventHandlerAccess = ts.createPropertyAccess(ts.createThis(), handlerLiteral.text);
                //ts.forEachChild(handler)
                const args = handlerImpl.parameters.map((arg) => tsUtils.assertNodeKind(arg.name, ts.SyntaxKind.Identifier));
                const eventHandlerCall = ts.createCall(eventHandlerAccess, [], args);
                const arrowFunc = ts.createArrowFunction([], [], handlerImpl.parameters, undefined, undefined, eventHandlerCall);
                //See https://polymer-library.polymer-project.org/3.0/docs/devguide/gesture-events for a list of events
                if (["down", "up", "tap", "track"].indexOf(eventNameLiteral.text) >= 0) {
                    const methodCall = ts.createCall(tsUtils.createNameExpression("Polymer.Gestures.addListener"), [], [ts.createThis(), eventNameLiteral, arrowFunc]);
                    codeAtMethodEnd.push(ts.createExpressionStatement(methodCall));
                }
                else {
                    const methodCall = ts.createCall(ts.createPropertyAccess(ts.createThis(), "addEventListener"), [], [eventNameLiteral, arrowFunc]);
                    codeAtMethodEnd.push(ts.createExpressionStatement(methodCall));
                }
            }
        }
        if (name === "ready" && reservedDeclarations.hostAttributes) {
            for (const listener of reservedDeclarations.hostAttributes.properties) {
                const propertyAssignment = tsUtils.assertNodeKind(listener, ts.SyntaxKind.PropertyAssignment);
                if (!propertyAssignment.name) {
                    throw new Error("Listener must have event name");
                }
                let attributeNameLiteral;
                if (propertyAssignment.name.kind === ts.SyntaxKind.StringLiteral) {
                    attributeNameLiteral = propertyAssignment.name;
                }
                else if (propertyAssignment.name.kind === ts.SyntaxKind.Identifier) {
                    attributeNameLiteral = ts.createStringLiteral(propertyAssignment.name.text);
                }
                else {
                    throw new Error(`Unsupported type ${ts.SyntaxKind[propertyAssignment.name.kind]}`);
                }
                let attributeValueLiteral;
                if (propertyAssignment.initializer.kind === ts.SyntaxKind.StringLiteral) {
                    attributeValueLiteral = propertyAssignment.initializer;
                }
                else if (propertyAssignment.initializer.kind === ts.SyntaxKind.NumericLiteral) {
                    attributeValueLiteral = propertyAssignment.initializer;
                }
                else {
                    throw new Error(`Unsupported type ${ts.SyntaxKind[propertyAssignment.initializer.kind]}`);
                }
                const methodCall = ts.createCall(ts.createPropertyAccess(ts.createThis(), "_ensureAttribute"), [], [attributeNameLiteral, attributeValueLiteral]);
                codeAtMethodEnd.push(ts.createExpressionStatement(methodCall));
            }
            //codeAtMethodStart
        }
        //if(!legacySettings.lifecycleMethods.hasOwnProperty(name) && !codeAtMethodStart && !codeAtMethodEnd) continue;
        const existingMethod = legacySettings.lifecycleMethods.get(name);
        const newMethod = createLifecycleMethod(name, existingMethod, codeAtMethodStart, codeAtMethodEnd);
        if (newMethod) {
            members.push({ pos: existingMethod ? existingMethod.pos : 999999999, member: newMethod });
        }
    }
    for (const [name, method] of legacySettings.ordinaryMethods) {
        members.push({ pos: method.pos, member: method });
    }
    for (const [name, accessor] of legacySettings.ordinaryGetAccessors) {
        members.push({ pos: accessor.pos, member: accessor });
    }
    if (legacySettings.ordinaryShorthandProperties) {
        console.log("Warning! File has ordinaryShorthandProperties");
        for (const [name, property] of legacySettings.ordinaryShorthandProperties) {
            const returnStatement = ts.createReturn(property.name);
            const block = ts.createBlock([returnStatement]);
            const getAccessor = ts.createGetAccessor([], [], property.name, [], undefined, block);
            members.push({ pos: property.pos, member: getAccessor });
        }
    }
    if (legacySettings.ordinaryPropertyAssignments) {
        console.log("Warning! File has ordinaryPropertyAssignments");
        for (const [name, property] of legacySettings.ordinaryPropertyAssignments) {
            const returnStatement = ts.createReturn(property.initializer);
            const block = ts.createBlock([returnStatement]);
            const getAccessor = ts.createGetAccessor([], [], property.name, [], undefined, block);
            members.push({ pos: property.pos, member: getAccessor });
        }
    }
    members.sort((a, b) => a.pos - b.pos);
    const callExpression = ts.createCall(ts.createPropertyAccess(ts.createIdentifier("customElements"), "define"), undefined, [ts.createPropertyAccess(ts.createIdentifier(className), "is"), ts.createIdentifier(className)]);
    return {
        classDeclaration: ts.createClassDeclaration(undefined, undefined, className, undefined, heritageClauses, members.map(m => m.member)),
        componentRegistration: ts.createExpressionStatement(callExpression),
    };
}
function createLifecycleMethod(name, methodDecl, codeAtStart, codeAtEnd) {
    if (!methodDecl && (codeAtEnd.length > 0 || codeAtEnd.length > 0)) {
        methodDecl = ts.createMethod([], [], undefined, name, undefined, [], [], undefined, ts.createBlock([]));
    }
    if (!methodDecl) {
        return;
    }
    if (!methodDecl.body) {
        throw new Error("Method must have a body");
    }
    if (methodDecl.parameters.length > 0) {
        throw new Error("Methods with parameters are not supported");
    }
    const superMethodCall = ts.createExpressionStatement(ts.createCall(ts.createPropertyAccess(ts.createSuper(), tsUtils.assertNodeKind(methodDecl.name, ts.SyntaxKind.Identifier)), [], []));
    const newBody = ts.getMutableClone(methodDecl.body);
    const newStatements = Array.from(newBody.statements);
    newStatements.splice(0, 0, ...codeAtStart, superMethodCall);
    newStatements.push(...codeAtEnd);
    newBody.statements = ts.createNodeArray(newStatements);
    const newMethod = ts.getMutableClone(methodDecl);
    newMethod.body = newBody;
    return newMethod;
}
function createIsAccessor(tagName) {
    const returnStatement = ts.createReturn(ts.createStringLiteral(tagName));
    const block = ts.createBlock([returnStatement]);
    return ts.createGetAccessor([], [ts.createModifier(typescript_1.SyntaxKind.StaticKeyword)], "is", [], undefined, block);
}
function generateClassNameFromTagName(tagName) {
    let result = "";
    let nextUppercase = true;
    for (const ch of tagName) {
        if (ch === '-') {
            nextUppercase = true;
            continue;
        }
        result += nextUppercase ? ch.toUpperCase() : ch;
        nextUppercase = false;
    }
    return result;
}
function main() {
    return __awaiter(this, void 0, void 0, function* () {
        const params = yield getParams();
        const componentParser = new polymerComponentParser_1.PolymerComponentParser(params.htmlFiles);
        for (const jsFile of params.jsFiles) {
            console.log(`Processing ${jsFile}`);
            const polymerComponent = yield componentParser.parse(jsFile);
            if (!polymerComponent) {
                continue;
            }
            switch (polymerComponent.type) {
                case polymerComponentParser_1.PolymerComponentType.Legacy:
                    yield updateLegacyComponent(polymerComponent);
                    break;
                default:
                    utils.unexpetedValue(polymerComponent.type);
            }
        }
    });
}
main().then(() => {
    process.exit(0);
}).catch(e => {
    console.error(e);
    process.exit(1);
});
//# sourceMappingURL=index.js.map