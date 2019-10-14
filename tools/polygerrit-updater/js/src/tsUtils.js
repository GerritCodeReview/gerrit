"use strict";
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (Object.hasOwnProperty.call(mod, k)) result[k] = mod[k];
    result["default"] = mod;
    return result;
};
Object.defineProperty(exports, "__esModule", { value: true });
const ts = __importStar(require("typescript"));
function assertNodeKind(node, expectedKind) {
    if (node.kind !== expectedKind) {
        throw new Error(`Invlid node kind. Expected: ${ts.SyntaxKind[expectedKind]}, actual: ${ts.SyntaxKind[node.kind]}`);
    }
    return node;
}
exports.assertNodeKind = assertNodeKind;
function assertNodeKindOrUndefined(node, expectedKind) {
    if (!node) {
        return undefined;
    }
    return assertNodeKind(node, expectedKind);
}
exports.assertNodeKindOrUndefined = assertNodeKindOrUndefined;
function getPropertyAssignment(expression) {
    return assertNodeKindOrUndefined(expression, ts.SyntaxKind.PropertyAssignment);
}
exports.getPropertyAssignment = getPropertyAssignment;
function getStringLiteralValue(expression) {
    const literal = assertNodeKindOrUndefined(expression, ts.SyntaxKind.StringLiteral);
    return literal ? literal.text : undefined;
}
exports.getStringLiteralValue = getStringLiteralValue;
function getBooleanLiteralValue(expression) {
    if (!expression) {
        return undefined;
    }
    if (expression.kind === ts.SyntaxKind.TrueKeyword) {
        return true;
    }
    if (expression.kind === ts.SyntaxKind.FalseKeyword) {
        return false;
    }
    throw new Error(`Invalid expression kind - ${expression.kind}`);
}
exports.getBooleanLiteralValue = getBooleanLiteralValue;
function getObjectLiteralExpression(expression) {
    return assertNodeKindOrUndefined(expression, ts.SyntaxKind.ObjectLiteralExpression);
}
exports.getObjectLiteralExpression = getObjectLiteralExpression;
function getArrayLiteralExpression(expression) {
    return assertNodeKindOrUndefined(expression, ts.SyntaxKind.ArrayLiteralExpression);
}
exports.getArrayLiteralExpression = getArrayLiteralExpression;
function replaceNode(file, originalNode, newNode) {
    const nodeReplacerTransformer = (context) => {
        const visitor = (node) => {
            if (node === originalNode) {
                return newNode;
            }
            return ts.visitEachChild(node, visitor, context);
        };
        return source => ts.visitNode(source, visitor);
    };
    return ts.transform(file, [nodeReplacerTransformer]);
}
exports.replaceNode = replaceNode;
function createNameExpression(fullPath) {
    const parts = fullPath.split(".");
    let result = parts[0] === "this" ? ts.createThis() : ts.createIdentifier(parts[0]);
    for (let i = 1; i < parts.length; i++) {
        result = ts.createPropertyAccess(result, parts[i]);
    }
    return result;
}
exports.createNameExpression = createNameExpression;
//# sourceMappingURL=tsUtils.js.map