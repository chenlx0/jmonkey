package com.github.chenlx0.parser;

import com.github.chenlx0.ast.Expression;
import com.github.chenlx0.ast.Statement;
import com.github.chenlx0.ast.impl.*;
import com.github.chenlx0.lexer.Lexer;
import com.github.chenlx0.lexer.Token;
import com.github.chenlx0.lexer.Token.TokenType;
import com.github.chenlx0.util.Consts;
import com.github.chenlx0.util.MonkeyException;
import com.github.chenlx0.util.ParseException;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class Parser {

    private Lexer lexer;

    private Token curToken, peekToken;

    public Parser(Lexer lexer) {
        this.lexer = lexer;
        nextToken();
        nextToken();
    }

    private void expect(TokenType type) throws MonkeyException {
        if (!peekToken.isToken(type)) {
            throw new ParseException("Except " + type.name() + " after " + curToken.getVal());
        }
    }

    private void nextToken() {
        curToken = peekToken;
        peekToken = lexer.nextToken();
    }

    public Program parseProgram() throws MonkeyException {
        Program program = new Program();
        Statement tmpStatement;

        while ((tmpStatement = parseStatement()) != null) {
            program.add(tmpStatement);
        }

        return program;
    }

    private Statement parseStatement() {
        if (curToken.isToken(TokenType.SEMI)) {
            nextToken();
        }
        switch (curToken.getType()) {
            case EOF:
                return null;
            case LET:
                return parseLetStatement();
            case RET:
                return parseReturnStatement();
        }
        if (curToken.isToken(TokenType.SEMI)) {
            nextToken();
        }
        return parseExpressionStatement();
    }

    private short infixPrecedence(TokenType tokenType) {
        Short result = Consts.InfixPrecedence.get(tokenType);
        return result == null ? Consts.LOWEST : result;
    }

    private Expression parsePrefix() {
        switch (curToken.getType()) {
            case NUMBER:
                return parseNumberLiteral();
            case STRING:
                return parseStringLiteral();
            case VAR:
                return parseVariableLiteral();
            case TRUE: case FALSE:
                return parseBooleanLiteral();
            case MINUS: case EXCL:
                return parseOperatorPrefix();
            case LSQB:
                return parseArrayLiteral();
            case LBRACE:
                return parseDictLiteral();
            case LPAR:
                return parseParenthese();
            case FUNC:
                return parseFunctionLiteral();
            case IF: case WHILE:
                return parseConditionLiteral();
            default:
                throw new ParseException("can not parse prefix token: " + curToken.getVal());
        }
    }

    private Expression parseInfix(Expression leftExp) {
        switch (curToken.getType()) {
            case PLUS: case MINUS: case MULTI: case DIV: case NOTEQUAL:
            case LESS: case GREATER: case GREATEQ: case LESSEQ: case EQEQUAL:
                return parseNormalInfixOperator(leftExp);
            case LSQB:
                return parseIndex(leftExp);
            default:
                return null;
        }
    }

    private BlockStatements parseBlockStatements() {
        Statement st;
        List<Statement> result = new LinkedList<>();
        nextToken();
        while (!peekToken.isToken(TokenType.RBRACE) && (st = parseStatement()) != null) {
            result.add(st);
        }
        nextToken(); nextToken();
        return new BlockStatements(result);
    }

    private Expression parseExpression() {
        return parseExpression(Consts.LOWEST);
    }

    private Expression parseExpression(short precedence) {
        Expression leftExp = parsePrefix();

        while (!peekToken.isToken(TokenType.SEMI) && precedence < infixPrecedence(peekToken.getType())) {
            nextToken();
            Expression nextExp = parseInfix(leftExp);
            if (nextExp == null) {
                return leftExp;
            }

            leftExp = nextExp;
        }

        return leftExp;
    }

    private Statement parseLetStatement() {
        expect(TokenType.VAR);
        nextToken();
        Token varToken = curToken;
        expect(TokenType.ASSIGN);
        nextToken();
        nextToken();
        Expression signExpression = parseExpression();
        if (peekToken.isToken(TokenType.SEMI)) {
            nextToken();
        }
        VariableExpression var = new VariableExpression(varToken);
        return new LetStatement(var, signExpression);
    }

    private Statement parseReturnStatement() {
        Token retToken = curToken;
        nextToken();
        Expression retExpression = parseExpression();
        if (peekToken.isToken(TokenType.SEMI)) {
            nextToken();
        }
        return new ReturnStatement(retExpression);
    }

    private Statement parseExpressionStatement() {
        Token start = curToken;
        Expression exp = parseExpression();
        Statement stmt = new ExpressionStatement(exp);
        if (peekToken.isToken(TokenType.SEMI)) {
            nextToken();
        }
        return stmt;
    }

    private Expression parseNumberLiteral() {
        try {
            if (curToken.getVal().contains(".")) {
                Double val = Double.valueOf(curToken.getVal());
                return new NumberExpression(val);
            } else {
                Integer val = Integer.valueOf(curToken.getVal());
                return new NumberExpression(val);
            }
        } catch (NumberFormatException e) {
            throw new ParseException("Parse number: '" + curToken.getVal() + "' failed.");
        }
    }

    private Expression parseStringLiteral() {
        return new StringExpression(curToken);
    }

    private Expression parseBooleanLiteral() {
        Expression result;
        if (curToken.isToken(TokenType.TRUE)) {
            result = new BooleanExpression(true);
        } else {
            result = new BooleanExpression(false);
        }
        return result;
    }

    private Expression parseVariableLiteral() {
        Expression result = new VariableExpression(curToken);
        return result;
    }

    private Expression parseOperatorPrefix() {
        nextToken();
        Expression nextExpression = parseExpression(Consts.PREFIX);
        return new OperatorPrefixExpression(nextExpression);
    }

    private Expression parseArrayLiteral() {
        Token bracket = curToken;
        List<Expression> members = new LinkedList<>();

        if (peekToken.isToken(TokenType.RSQB)) {
            nextToken();
            return new ArrayExpression(members);
        }

        nextToken();
        members.add(parseExpression());
        nextToken();

        while (curToken.isToken(TokenType.COMMA)) {
            nextToken();
            members.add(parseExpression());
            nextToken();
        }

        if (!curToken.isToken(TokenType.RSQB)) {
            throw new ParseException("Array should end with ']'");
        }

        nextToken();

        return new ArrayExpression(members);
    }

    private Expression parseDictLiteral() {
        Map<String, Expression> dict = new HashMap<>();
        return null;
    }

    private Expression parseNormalInfixOperator(Expression leftExp) {
        Token infixOp = curToken;
        nextToken();
        Expression rightExp = parseExpression(infixPrecedence(infixOp.getType()));
        return new InfixExpression(leftExp, rightExp);
    }

    private Expression parseConditionLiteral() {
        Token condToken = curToken;
        expect(TokenType.LPAR);
        nextToken(); nextToken();
        Expression conditionExp = parseExpression();

        expect(TokenType.RPAR);
        nextToken();
        expect(TokenType.LBRACE);
        nextToken();
        BlockStatements blockStatements = parseBlockStatements();

        if (condToken.isToken(TokenType.IF)) {
            return new IfExpression(conditionExp, blockStatements);
        } else if (condToken.isToken(TokenType.WHILE)) {
            return new WhileExpression(conditionExp, blockStatements);
        } else {
            throw new ParseException("unknown condition");
        }
    }

    private Expression parseFunctionLiteral() {
        expect(TokenType.LPAR);
        nextToken();

        // parse parameters
        List<VariableExpression> parameters = new LinkedList<>();
        while (!curToken.isToken(TokenType.RPAR)) {
            expect(TokenType.VAR);
            nextToken();
            parameters.add((VariableExpression) parseVariableLiteral());
            if (peekToken.isToken(TokenType.RPAR)) break;
            expect(TokenType.COMMA);
            nextToken();
        }

        nextToken();
        expect(TokenType.LBRACE);
        nextToken();
        BlockStatements statements = parseBlockStatements();
        return new FunctionExpression(parameters, statements);
    }

    private Expression parseIndex(Expression leftExp) {
        Expression index;
        // index must be number or string
        if (peekToken.isToken(TokenType.NUMBER)) {
            nextToken();
            index = parseNumberLiteral();
        } else if (peekToken.isToken(TokenType.STRING)) {
            nextToken();
            index = parseStringLiteral();
        } else {
            throw new ParseException("index must be number or string");
        }
        expect(TokenType.RSQB);
        nextToken();

        return new IndexExpression(leftExp, index);
    }

    private Expression parseParenthese() {
        nextToken();
        Expression exp = parseExpression();
        expect(TokenType.RPAR);
        nextToken();
        return exp;
    }
}
