package org.jetlinks.reactor.ql.feature;

import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.arithmetic.Concat;
import net.sf.jsqlparser.expression.operators.relational.IsBooleanExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SubSelect;
import org.apache.commons.collections.CollectionUtils;
import org.jetlinks.reactor.ql.ReactorQLMetadata;
import org.jetlinks.reactor.ql.supports.ReactorQLContext;
import org.jetlinks.reactor.ql.utils.CastUtils;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 值转换支持,用来创建数据转换函数
 *
 * @author zhouhao
 * @since 1.0
 */
public interface ValueMapFeature extends Feature {

    Function<ReactorQLContext, ? extends Publisher<?>> createMapper(Expression expression, ReactorQLMetadata metadata);

    static Function<ReactorQLContext, ? extends Publisher<?>> createMapperNow(Expression expr, ReactorQLMetadata metadata) {
        return createMapperByExpression(expr, metadata).orElseThrow(() -> new UnsupportedOperationException("不支持的操作:" + expr));
    }

    static Optional<Function<ReactorQLContext, ? extends Publisher<?>>> createMapperByExpression(Expression expr, ReactorQLMetadata metadata) {

        AtomicReference<Function<ReactorQLContext, ? extends Publisher<?>>> ref = new AtomicReference<>();

        expr.accept(new org.jetlinks.reactor.ql.supports.ExpressionVisitorAdapter() {
            @Override
            public void visit(net.sf.jsqlparser.expression.Function function) {
                metadata.getFeature(FeatureId.ValueMap.of(function.getName()))
                        .ifPresent(feature -> ref.set(feature.createMapper(function, metadata)));
            }

            @Override
            public void visit(SubSelect subSelect) {
                ref.set(metadata.getFeatureNow(FeatureId.ValueMap.select, expr::toString).createMapper(subSelect, metadata));
            }

            @Override
            public void visit(Parenthesis value) {
                createMapperByExpression(value.getExpression(), metadata).ifPresent(ref::set);
            }

            @Override
            public void visit(CaseExpression expr) {
                ref.set(metadata.getFeatureNow(FeatureId.ValueMap.caseWhen, expr::toString).createMapper(expr, metadata));
            }

            @Override
            public void visit(CastExpression expr) {
                ref.set(metadata.getFeatureNow(FeatureId.ValueMap.cast, expr::toString).createMapper(expr, metadata));
            }

            @Override
            public void visit(Column column) {
                ref.set(metadata.getFeatureNow(FeatureId.ValueMap.property, column::toString).createMapper(column, metadata));
            }

            @Override
            public void visit(StringValue value) {
                Object val = value.getValue();
                ref.set((v) -> Mono.just(val));
            }

            @Override
            public void visit(LongValue value) {
                Object val = value.getValue();
                ref.set((v) -> Mono.just(val));
            }

            @Override
            public void visit(DoubleValue value) {
                Object val = value.getValue();
                ref.set((v) -> Mono.just(val));
            }

            @Override
            public void visit(DateValue value) {
                Object val = value.getValue();
                ref.set((v) -> Mono.just(val));
            }

            @Override
            public void visit(HexValue hexValue) {
                Object val = hexValue.getValue();
                ref.set((v) -> Mono.just(val));
            }

            @Override
            public void visit(SignedExpression expr) {
                char sign = expr.getSign();
                Function<ReactorQLContext, ? extends Publisher<?>> mapper = createMapperNow(expr.getExpression(), metadata);
                Function<Number, Number> doSign;
                switch (sign) {
                    case '+':
                        doSign = n -> +n.doubleValue();
                        break;
                    case '-':
                        doSign = n -> -n.doubleValue();
                        break;
                    case '~':
                        doSign = n -> ~n.longValue();
                        break;
                    default:
                        doSign = Function.identity();
                }
                ref.set(ctx -> Mono.from(mapper.apply(ctx))
                        .map(CastUtils::castNumber)
                        .map(doSign));
            }

            @Override
            public void visit(TimestampValue value) {
                Object val = value.getValue();
                ref.set((v) -> Mono.just(val));
            }

            @Override
            public void visit(BinaryExpression jsonExpr) {
                metadata.getFeature(FeatureId.ValueMap.of(jsonExpr.getStringExpression()))
                        .map(feature -> feature.createMapper(expr, metadata))
                        .ifPresent(ref::set);
                if (ref.get() == null) {
                    FilterFeature
                            .createPredicateByExpression(expr, metadata)
                            .<Function<ReactorQLContext, ? extends Publisher<?>>>
                                    map(predicate -> ((ctx) -> predicate.apply(ctx, ctx.getRecord())))
                            .ifPresent(ref::set);
                }
            }
        });

        return Optional.ofNullable(ref.get());
    }

    static Tuple2<Function<ReactorQLContext, ? extends Publisher<?>>, Function<ReactorQLContext, ? extends Publisher<?>>> createBinaryMapper(Expression expression, ReactorQLMetadata metadata) {
        Expression left;
        Expression right;
        if (expression instanceof net.sf.jsqlparser.expression.Function) {
            net.sf.jsqlparser.expression.Function function = ((net.sf.jsqlparser.expression.Function) expression);
            List<Expression> expressions;
            if (function.getParameters() == null || CollectionUtils.isEmpty(expressions = function.getParameters().getExpressions()) || expressions.size() != 2) {
                throw new UnsupportedOperationException("参数数量只能为2:" + expression);
            }
            left = expressions.get(0);
            right = expressions.get(1);
        } else if (expression instanceof BinaryExpression) {
            BinaryExpression bie = ((BinaryExpression) expression);
            left = bie.getLeftExpression();
            right = bie.getRightExpression();
        } else {
            throw new UnsupportedOperationException("不支持的表达式:" + expression);
        }
        Function<ReactorQLContext, ? extends Publisher<?>> leftMapper = createMapperNow(left, metadata);
        Function<ReactorQLContext, ? extends Publisher<?>> rightMapper = createMapperNow(right, metadata);
        return Tuples.of(leftMapper, rightMapper);
    }
}
