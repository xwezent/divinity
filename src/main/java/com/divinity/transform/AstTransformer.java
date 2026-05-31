package com.divinity.transform;

import com.divinity.ast.AstNode;
import com.divinity.deobfuscation.MbaSimplifier;
import java.util.*;

public final class AstTransformer {

    private final MbaSimplifier mbaSimplifier;
    private final Map<String, String> renamingMap;
    private final TransformationStats stats;

    public AstTransformer(Map<String, String> renamingMap) {
        this.mbaSimplifier = new MbaSimplifier();
        this.renamingMap = renamingMap != null ? renamingMap : new HashMap<>();
        this.stats = new TransformationStats();
    }

    public AstNode.ClassNode transform(AstNode.ClassNode classNode) {
        List<AstNode.MethodNode> transformedMethods = new ArrayList<>();

        for (AstNode.MethodNode method : classNode.methods()) {
            AstNode.MethodNode transformed = transformMethod(method);
            transformedMethods.add(transformed);
        }

        return new AstNode.ClassNode(
            classNode.accessFlags(),
            classNode.name(),
            classNode.superName(),
            classNode.interfaces(),
            classNode.fields(),
            transformedMethods,
            classNode.innerClasses(),
            classNode.isInterface(),
            classNode.isEnum(),
            classNode.isRecord(),
            classNode.isAnnotation(),
            classNode.permittedSubclasses(),
            classNode.recordComponents(),
            classNode.signature(),
            classNode.sourceFile()
        );
    }

    private AstNode.MethodNode transformMethod(AstNode.MethodNode method) {
        List<AstNode.Statement> transformedBody = new ArrayList<>();

        for (AstNode.Statement stmt : method.body()) {
            AstNode.Statement transformed = transformStatement(stmt);
            if (transformed != null) {
                transformedBody.add(transformed);
            }
        }

        return new AstNode.MethodNode(
            method.accessFlags(),
            method.name(),
            method.descriptor(),
            method.signature(),
            method.exceptions(),
            transformedBody
        );
    }

    private AstNode.Statement transformStatement(AstNode.Statement stmt) {
        if (stmt instanceof AstNode.Statement.ExpressionStmt exprStmt) {
            AstNode.Expression transformed = transformExpression(exprStmt.expr());
            return new AstNode.Statement.ExpressionStmt(transformed);
        }

        if (stmt instanceof AstNode.Statement.ReturnStmt returnStmt) {
            if (returnStmt.value() != null) {
                AstNode.Expression transformed = transformExpression(returnStmt.value());
                return new AstNode.Statement.ReturnStmt(transformed);
            }
            return returnStmt;
        }

        if (stmt instanceof AstNode.Statement.IfStmt ifStmt) {
            AstNode.Expression condition = transformExpression(ifStmt.condition());
            AstNode.Statement thenBranch = transformStatement(ifStmt.thenBranch());
            AstNode.Statement elseBranch = ifStmt.elseBranch() != null ?
                transformStatement(ifStmt.elseBranch()) : null;
            return new AstNode.Statement.IfStmt(condition, thenBranch, elseBranch);
        }

        if (stmt instanceof AstNode.Statement.WhileStmt whileStmt) {
            AstNode.Expression condition = transformExpression(whileStmt.condition());
            AstNode.Statement body = transformStatement(whileStmt.body());
            return new AstNode.Statement.WhileStmt(condition, body, whileStmt.isDoWhile());
        }

        if (stmt instanceof AstNode.Statement.BlockStmt blockStmt) {
            List<AstNode.Statement> transformedStmts = new ArrayList<>();
            for (AstNode.Statement s : blockStmt.statements()) {
                AstNode.Statement transformed = transformStatement(s);
                if (transformed != null) {
                    transformedStmts.add(transformed);
                }
            }
            return new AstNode.Statement.BlockStmt(transformedStmts);
        }

        return stmt;
    }

    private AstNode.Expression transformExpression(AstNode.Expression expr) {
        if (expr == null) return null;

        // Apply MBA simplification
        AstNode.Expression simplified = mbaSimplifier.simplify(expr);
        stats.recordSimplification();

        // Apply variable renaming
        simplified = applyRenaming(simplified);

        // Recursively transform sub-expressions
        simplified = transformSubExpressions(simplified);

        return simplified;
    }

    private AstNode.Expression applyRenaming(AstNode.Expression expr) {
        if (expr instanceof AstNode.Expression.VarExpr varExpr) {
            String newName = renamingMap.get(varExpr.name());
            if (newName != null) {
                stats.recordRename();
                return new AstNode.Expression.VarExpr(newName, varExpr.descriptor());
            }
        }

        return expr;
    }

    private AstNode.Expression transformSubExpressions(AstNode.Expression expr) {
        if (expr instanceof AstNode.Expression.BinaryExpr binary) {
            AstNode.Expression left = transformExpression(binary.left());
            AstNode.Expression right = transformExpression(binary.right());
            return new AstNode.Expression.BinaryExpr(binary.operator(), left, right);
        }

        if (expr instanceof AstNode.Expression.UnaryExpr unary) {
            AstNode.Expression operand = transformExpression(unary.operand());
            return new AstNode.Expression.UnaryExpr(unary.operator(), operand);
        }

        if (expr instanceof AstNode.Expression.MethodCallExpr call) {
            List<AstNode.Expression> transformedArgs = new ArrayList<>();
            for (AstNode.Expression arg : call.args()) {
                transformedArgs.add(transformExpression(arg));
            }
            AstNode.Expression target = call.object() != null ?
                transformExpression(call.object()) : null;
            return new AstNode.Expression.MethodCallExpr(
                target, call.ownerClass(), call.name(), call.descriptor(), transformedArgs);
        }

        if (expr instanceof AstNode.Expression.ArrayAccessExpr arrayAccess) {
            AstNode.Expression array = transformExpression(arrayAccess.array());
            AstNode.Expression index = transformExpression(arrayAccess.index());
            return new AstNode.Expression.ArrayAccessExpr(array, index);
        }

        if (expr instanceof AstNode.Expression.FieldAccessExpr fieldAccess) {
            AstNode.Expression target = fieldAccess.object() != null ?
                transformExpression(fieldAccess.object()) : null;
            return new AstNode.Expression.FieldAccessExpr(
                target, fieldAccess.ownerClass(), fieldAccess.name(), fieldAccess.descriptor());
        }

        if (expr instanceof AstNode.Expression.CastExpr cast) {
            AstNode.Expression operand = transformExpression(cast.operand());
            return new AstNode.Expression.CastExpr(cast.targetType(), operand);
        }

        if (expr instanceof AstNode.Expression.TernaryExpr ternary) {
            AstNode.Expression condition = transformExpression(ternary.condition());
            AstNode.Expression thenExpr = transformExpression(ternary.trueExpr());
            AstNode.Expression elseExpr = transformExpression(ternary.falseExpr());
            return new AstNode.Expression.TernaryExpr(condition, thenExpr, elseExpr);
        }

        return expr;
    }

    public TransformationStats getStats() {
        return stats;
    }

    public static class TransformationStats {
        private int simplifications;
        private int renames;
        private int deadCodeRemoved;

        public void recordSimplification() {
            simplifications++;
        }

        public void recordRename() {
            renames++;
        }

        public void recordDeadCodeRemoval() {
            deadCodeRemoved++;
        }

        public int getSimplifications() {
            return simplifications;
        }

        public int getRenames() {
            return renames;
        }

        public int getDeadCodeRemoved() {
            return deadCodeRemoved;
        }

        @Override
        public String toString() {
            return String.format(
                "Transformation Stats: %d simplifications, %d renames, %d dead code removed",
                simplifications, renames, deadCodeRemoved
            );
        }
    }
}
