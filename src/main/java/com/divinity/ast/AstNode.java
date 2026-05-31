package com.divinity.ast;

import java.util.*;

public sealed interface AstNode {

    record ClassNode(
            int accessFlags,
            String name,
            String superName,
            List<String> interfaces,
            List<FieldNode> fields,
            List<MethodNode> methods,
            List<ClassNode> innerClasses,
            boolean isInterface,
            boolean isEnum,
            boolean isRecord,
            boolean isAnnotation,
            List<String> permittedSubclasses,
            List<RecordComponentNode> recordComponents,
            String signature,
            String sourceFile
    ) implements AstNode {}

    record RecordComponentNode(String name, String descriptor, String signature) implements AstNode {}

    record FieldNode(
            int accessFlags,
            String name,
            String descriptor,
            String signature,
            Object constValue
    ) implements AstNode {}

    record MethodNode(
            int accessFlags,
            String name,
            String descriptor,
            String signature,
            List<String> exceptions,
            List<Statement> body
    ) implements AstNode {}

    sealed interface Statement extends AstNode {
        record LabelStmt(String label) implements Statement {}
        record ExpressionStmt(Expression expr) implements Statement {}
        record ReturnStmt(Expression value) implements Statement {}
        record ThrowStmt(Expression exception) implements Statement {}
        record IfStmt(Expression condition, Statement thenBranch, Statement elseBranch) implements Statement {}
        record WhileStmt(Expression condition, Statement body, boolean isDoWhile) implements Statement {}
        record ForStmt(Statement init, Expression condition, Statement update, Statement body) implements Statement {}
        record SwitchStmt(Expression selector, List<CaseEntry> cases, Statement defaultCase) implements Statement {}
        record TryCatchStmt(List<Statement> tryBody, List<CatchClause> catchClauses, Statement finallyBody) implements Statement {}
        record BlockStmt(List<Statement> statements) implements Statement {}
        record VarDeclStmt(String type, String name, Expression initializer) implements Statement {}
        record BreakStmt(String label) implements Statement {}
        record ContinueStmt(String label) implements Statement {}
        record SynchronizedStmt(Expression lock, Statement body) implements Statement {}
        record EmptyStmt() implements Statement {}
    }

    record CaseEntry(List<Expression> matchValues, Statement body) {}

    record CatchClause(String exceptionType, String varName, Statement body) {}

    sealed interface Expression extends AstNode {
        record ThisExpr() implements Expression {}
        record SuperExpr() implements Expression {}
        record NullExpr() implements Expression {}
        record LiteralExpr(Object value, String typeDescriptor) implements Expression {}
        record VarExpr(String name, String descriptor) implements Expression {}
        record ArrayAccessExpr(Expression array, Expression index) implements Expression {}
        record FieldAccessExpr(Expression object, String ownerClass, String name, String descriptor) implements Expression {}
        record StaticFieldExpr(String ownerClass, String name, String descriptor) implements Expression {}
        record UnaryExpr(String operator, Expression operand) implements Expression {}
        record BinaryExpr(String operator, Expression left, Expression right) implements Expression {}
        record TernaryExpr(Expression condition, Expression trueExpr, Expression falseExpr) implements Expression {}
        record CastExpr(String targetType, Expression operand) implements Expression {}
        record InstanceOfExpr(Expression operand, String targetType, Expression pattern) implements Expression {}
        record NewExpr(String type, List<Expression> args, boolean isArray, int arrayDimensions) implements Expression {}
        record NewArrayExpr(String elementType, Expression size) implements Expression {}
        record MethodCallExpr(Expression object, String ownerClass, String name, String descriptor, List<Expression> args) implements Expression {}
        record StaticCallExpr(String ownerClass, String name, String descriptor, List<Expression> args) implements Expression {}
        record ArrayInitExpr(String elementType, List<Expression> elements) implements Expression {}
        record AssignExpr(Expression target, Expression value) implements Expression {}
    }

    static AstNode.Expression.LiteralExpr intLiteral(int value) {
        return new AstNode.Expression.LiteralExpr(value, "I");
    }

    static AstNode.Expression.LiteralExpr longLiteral(long value) {
        return new AstNode.Expression.LiteralExpr(value, "J");
    }

    static AstNode.Expression.LiteralExpr floatLiteral(float value) {
        return new AstNode.Expression.LiteralExpr(value, "F");
    }

    static AstNode.Expression.LiteralExpr doubleLiteral(double value) {
        return new AstNode.Expression.LiteralExpr(value, "D");
    }

    static AstNode.Expression.LiteralExpr stringLiteral(String value) {
        return new AstNode.Expression.LiteralExpr(value, "Ljava/lang/String;");
    }

    static AstNode.Expression.LiteralExpr nullLiteral() {
        return new AstNode.Expression.LiteralExpr(null, "Lnull;");
    }

    static AstNode.Expression.LiteralExpr boolLiteral(boolean value) {
        return new AstNode.Expression.LiteralExpr(value, "Z");
    }
}
