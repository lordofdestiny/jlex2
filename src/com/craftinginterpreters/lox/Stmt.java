package com.craftinginterpreters.lox;

import java.util.List;

abstract class Stmt {
	interface Visitor<R> {
		R visitWhileStmt(While stmt);
		R visitVarStmt(Var stmt);
		R visitPrintStmt(Print stmt);
		R visitIfStmt(If stmt);
		R visitReturnStmt(Return stmt);
		R visitFunctionStmt(Function stmt);
		R visitBlockStmt(Block stmt);
		R visitExpressionStmt(Expression stmt);
		R visitContinueStmt(Continue stmt);
		R visitBreakStmt(Break stmt);
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
	static class Function extends Stmt{ 
		Function(Token name, List<Token> params, List<Stmt> body) {
			this.name = name;
			this.params = params;
			this.body = body;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitFunctionStmt(this);
		}

		final Token name;
		final List<Token> params;
		final List<Stmt> body;
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
	static class Continue extends Stmt{ 
		Continue() {
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitContinueStmt(this);
		}

	}
	static class Break extends Stmt{ 
		Break() {
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitBreakStmt(this);
		}

	}

	 abstract <R> R accept(Visitor<R> visitor);
}
