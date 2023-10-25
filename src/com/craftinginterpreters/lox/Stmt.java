package com.craftinginterpreters.lox;

import java.util.List;

abstract class Stmt {
	interface Visitor<R> {
		R visitFunctionStmt(Function stmt);
		R visitPrintStmt(Print stmt);
		R visitReturnStmt(Return stmt);
		R visitExpressionStmt(Expression stmt);
		R visitVarStmt(Var stmt);
		R visitBreakStmt(Break stmt);
		R visitBlockStmt(Block stmt);
		R visitClassStmt(Class stmt);
		R visitWhileStmt(While stmt);
		R visitContinueStmt(Continue stmt);
		R visitIfStmt(If stmt);
	}
	static class Function extends Stmt{ 
		Function(Token name, Expr.Function function) {
			this.name = name;
			this.function = function;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitFunctionStmt(this);
		}

		final Token name;
		final Expr.Function function;
	}
	static class Print extends Stmt{ 
		Print(Expr expression) {
			this.expression = expression;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitPrintStmt(this);
		}

		final Expr expression;
	}
	static class Return extends Stmt{ 
		Return(Token keyword, Expr value) {
			this.keyword = keyword;
			this.value = value;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitReturnStmt(this);
		}

		final Token keyword;
		final Expr value;
	}
	static class Expression extends Stmt{ 
		Expression(Expr expression) {
			this.expression = expression;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitExpressionStmt(this);
		}

		final Expr expression;
	}
	static class Var extends Stmt{ 
		Var(Token name, Expr initializer) {
			this.name = name;
			this.initializer = initializer;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitVarStmt(this);
		}

		final Token name;
		final Expr initializer;
	}
	static class Break extends Stmt{ 
		Break(Token keyword) {
			this.keyword = keyword;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitBreakStmt(this);
		}

		final Token keyword;
	}
	static class Block extends Stmt{ 
		Block(List<Stmt> statements) {
			this.statements = statements;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitBlockStmt(this);
		}

		final List<Stmt> statements;
	}
	static class Class extends Stmt{ 
		Class(Token name, List<Stmt.Function> methods) {
			this.name = name;
			this.methods = methods;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitClassStmt(this);
		}

		final Token name;
		final List<Stmt.Function> methods;
	}
	static class While extends Stmt{ 
		While(Expr condition, Stmt body, Stmt forIncrement) {
			this.condition = condition;
			this.body = body;
			this.forIncrement = forIncrement;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitWhileStmt(this);
		}

		final Expr condition;
		final Stmt body;
		final Stmt forIncrement;
	}
	static class Continue extends Stmt{ 
		Continue(Token keyword) {
			this.keyword = keyword;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitContinueStmt(this);
		}

		final Token keyword;
	}
	static class If extends Stmt{ 
		If(Expr condition, Stmt thanBranch, Stmt elseBranch) {
			this.condition = condition;
			this.thanBranch = thanBranch;
			this.elseBranch = elseBranch;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitIfStmt(this);
		}

		final Expr condition;
		final Stmt thanBranch;
		final Stmt elseBranch;
	}

	 abstract <R> R accept(Visitor<R> visitor);
}
