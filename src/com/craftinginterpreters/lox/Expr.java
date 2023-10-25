package com.craftinginterpreters.lox;

import java.util.List;

abstract class Expr {
	interface Visitor<R> {
		R visitUnaryExpr(Unary expr);
		R visitVariableExpr(Variable expr);
		R visitAssignExpr(Assign expr);
		R visitFunctionExpr(Function expr);
		R visitGroupingExpr(Grouping expr);
		R visitLogicalExpr(Logical expr);
		R visitBinaryExpr(Binary expr);
		R visitConditionalExpr(Conditional expr);
		R visitLiteralExpr(Literal expr);
		R visitCallExpr(Call expr);
	}
	static class Unary extends Expr{ 
		Unary(Token operator, Expr right) {
			this.operator = operator;
			this.right = right;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitUnaryExpr(this);
		}

		final Token operator;
		final Expr right;
	}
	static class Variable extends Expr{ 
		Variable(Token name) {
			this.name = name;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitVariableExpr(this);
		}

		final Token name;
	}
	static class Assign extends Expr{ 
		Assign(Token name, Expr value) {
			this.name = name;
			this.value = value;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitAssignExpr(this);
		}

		final Token name;
		final Expr value;
	}
	static class Function extends Expr{ 
		Function(List<Token> parameters, List<Stmt> body) {
			this.parameters = parameters;
			this.body = body;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitFunctionExpr(this);
		}

		final List<Token> parameters;
		final List<Stmt> body;
	}
	static class Grouping extends Expr{ 
		Grouping(Expr expression) {
			this.expression = expression;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitGroupingExpr(this);
		}

		final Expr expression;
	}
	static class Logical extends Expr{ 
		Logical(Expr left, Token operator, Expr right) {
			this.left = left;
			this.operator = operator;
			this.right = right;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitLogicalExpr(this);
		}

		final Expr left;
		final Token operator;
		final Expr right;
	}
	static class Binary extends Expr{ 
		Binary(Expr left, Token operator, Expr right) {
			this.left = left;
			this.operator = operator;
			this.right = right;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitBinaryExpr(this);
		}

		final Expr left;
		final Token operator;
		final Expr right;
	}
	static class Conditional extends Expr{ 
		Conditional(Expr condition, Expr thenBranch, Expr elseBranch) {
			this.condition = condition;
			this.thenBranch = thenBranch;
			this.elseBranch = elseBranch;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitConditionalExpr(this);
		}

		final Expr condition;
		final Expr thenBranch;
		final Expr elseBranch;
	}
	static class Literal extends Expr{ 
		Literal(Object value) {
			this.value = value;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitLiteralExpr(this);
		}

		final Object value;
	}
	static class Call extends Expr{ 
		Call(Expr callee, Token paren, List<Expr> arguments) {
			this.callee = callee;
			this.paren = paren;
			this.arguments = arguments;
		}

		 @Override
		 <R> R accept(Visitor<R> visitor) {
			return visitor.visitCallExpr(this);
		}

		final Expr callee;
		final Token paren;
		final List<Expr> arguments;
	}

	 abstract <R> R accept(Visitor<R> visitor);
}