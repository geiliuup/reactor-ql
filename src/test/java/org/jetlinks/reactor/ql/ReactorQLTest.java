package org.jetlinks.reactor.ql;

import org.hswebframework.utils.time.DateFormatter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class ReactorQLTest {

    @Test
    void testLimit() {

        ReactorQL.builder()
                .sql("select * from test limit 0,10")
                .build()
                .start(Flux.range(0, 20))
                .as(StepVerifier::create)
                .expectNextCount(10)
                .verifyComplete();

    }

    @Test
    void testCount() {

        ReactorQL.builder()
                .sql("select count(1) total from test")
                .build()
                .start(Flux.range(0, 100))
                .as(StepVerifier::create)
                .expectNext(Collections.singletonMap("total", 100L))
                .verifyComplete();

    }

    @Test
    void testWhere() {

        ReactorQL.builder()
                .sql("select count(1) total from test where (this > 10 and this > 10.0) and (this <=90 or this >95) and this is not null")
                .build()
                .start(Flux.range(1, 100))
                .as(StepVerifier::create)
                .expectNext(Collections.singletonMap("total", 85L))
                .verifyComplete();

    }

    @Test
    void testDataSource() {
        ReactorQL.builder()
                .sql("select (select this name from a) a,(select this name from v) v from t")
                .build()
                .start(name -> Flux.just("t_" + name))
                .as(StepVerifier::create)
                .expectNext(new HashMap<String, Object>() {{
                    put("a", Collections.singletonMap("name", "t_a"));
                    put("v", Collections.singletonMap("name", "t_v"));
                }})
                .verifyComplete();
    }

    @Test
    void testBetween() {

        ReactorQL.builder()
                .sql("select count(1) total from test where this between 1 and 20 and {ts '2020-02-04 00:00:00'} between {d '2010-01-01'} and now()")
                .build()
                .start(Flux.range(1, 100))
                .as(StepVerifier::create)
                .expectNext(Collections.singletonMap("total", 20L))
                .verifyComplete();

    }

    @Test
    void testIn() {

        ReactorQL.builder()
                .sql("select this from test where this in (1,2,3,4)")
                .build()
                .start(Flux.range(0, 20))
                .as(StepVerifier::create)
                .expectNextCount(4)
                .verifyComplete();

        ReactorQL.builder()
                .sql("select this v from test where 10 in (1,2,3,list)")
                .build()
                .start(Flux.just(Collections.singletonMap("list", Arrays.asList(1, 2, 3)),
                        Collections.singletonMap("list", Arrays.asList(10, 20, 30))))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();

    }

    @Test
    void testCalculate() {

        ReactorQL.builder()
                .sql(
                        "select math.plus(this,10) plus,",
                        "math.sub(this,10) sub1,",
                        "math.mul(this,10) mul1,",
                        "math.divi(this,10) div1,",
                        "math.mod(this,10) mod1,",
                        "this + 10 \"v\",",
                        "this-10 sub,",
                        "this*10 mul,",
                        "this/10 divi,",
                        "math.max(1,2) mx,",
                        "math.min(1,3) min,",

                        "this%2 mod from test"
                )
                .build()
                .start(Flux.range(0, 100))
                .doOnNext(System.out::println)
                .cast(Map.class)
                .map(map -> map.get("v"))
                .cast(Long.class)
                .reduce(Math::addExact)
                .as(StepVerifier::create)
                .expectNext(5950L)
                .verifyComplete();

    }

    @Test
    void testMax() {

        ReactorQL.builder()
                .sql("select max(this) val from test")
                .build()
                .start(Flux.range(1, 100))
                .as(StepVerifier::create)
                .expectNext(Collections.singletonMap("val", 100))
                .verifyComplete();

    }

    @Test
    void testMin() {

        ReactorQL.builder()
                .sql("select min(this) val from test")
                .build()
                .start(Flux.range(1, 100))
                .as(StepVerifier::create)
                .expectNext(Collections.singletonMap("val", 1))
                .verifyComplete();

    }

    @Test
    void testAvg() {

        ReactorQL.builder()
                .sql("select avg(this) val from test")
                .build()
                .start(Flux.range(1, 100))
                .as(StepVerifier::create)
                .expectNext(Collections.singletonMap("val", 50.5D))
                .verifyComplete();

    }

    @Test
    void testSumAndCount() {

        ReactorQL.builder()
                .sql("select sum(val) total,count(1) count from test")
                .build()
                .start(Flux.range(1, 100).map(v -> Collections.singletonMap("val", v)))
                .as(StepVerifier::create)
                .expectNext(new HashMap<String, Object>() {
                    {
                        put("total", 5050D);
                        put("count", 100L);
                    }
                })
                .verifyComplete();

    }


    @Test
    void testGroup() {

        ReactorQL.builder()
                .sql("select count(1) total,type from test group by type")
                .build()
                .start(Flux.range(0, 100).map(v -> Collections.singletonMap("type", v / 10)))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNextCount(10)
                .verifyComplete();

    }


    @Test
    void testGroupWhere() {

        ReactorQL.builder()
                .sql("select count(1) total,type from test where type=1 group by type")
                .build()
                .start(Flux.range(0, 100).map(v -> Collections.singletonMap("type", v / 10)))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();

    }

    @Test
    void testGroupByTime() {

        ReactorQL.builder()
                .sql("select avg(this) total from test group by interval('1s')")
                .build()
                .start(Flux.range(0, 10).delayElements(Duration.ofMillis(500)))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNextCount(6)
                .verifyComplete();

    }


    @Test
    void testGroupByBinary() {
        ReactorQL.builder()
                .sql("select avg(this) total from test group by this/2")
                .build()
                .start(Flux.range(0, 10))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNextCount(5)
                .verifyComplete();
    }

    @Test
    void testGroupByWindow() {

        ReactorQL.builder()
                .sql("select avg(this) total from test group by _window(2)")
                .build()
                .start(Flux.range(0, 10).delayElements(Duration.ofMillis(100)))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNextCount(5)
                .verifyComplete();
        System.out.println();

        ReactorQL.builder()
                .sql("select avg(this) total from test group by _window('200S','2s')")
                .build()
                .start(Flux.range(0, 10).delayElements(Duration.ofMillis(100)))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNextCount(6)
                .verifyComplete();
        System.out.println();

        ReactorQL.builder()
                .sql("select avg(this) total from test where this > 0 group by _window( eq(this/2,0) )")
                .build()
                .start(Flux.range(0, 10).delayElements(Duration.ofMillis(100)))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNextCount(2)
                .verifyComplete();


    }

    @Test
    void testGroupByColumns() {

        ReactorQL.builder()
                .sql("select productId,deviceId,count(1) total from test group by productId,deviceId")
                .build()
                .start(Flux.range(0, 10).map(v ->
                        new HashMap<String, Object>() {
                            {
                                put("val", v);
                                put("deviceId", "dev-" + v / 2);
                                put("productId", "prod-" + v / 4);
                            }
                        }))
                .doOnNext(System.out::println)
                .cast(Map.class)
                .map(map -> map.get("total"))
                .cast(Long.class)
                .reduce(Math::addExact)
                .as(StepVerifier::create)
                .expectNext(10L)
                .verifyComplete();

    }

    @Test
    void testGroupByTimeHaving() {

        ReactorQL.builder()
                .sql("select avg(this) total from test group by interval('1s') having total > 2")
                .build()
                .start(Flux.range(0, 10).delayElements(Duration.ofMillis(500)))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNextCount(4)
                .verifyComplete();

    }

    @Test
    void testCase() {

        ReactorQL.builder()
                .sql("select case this when 1 then '一' when 2 then '二' when 2+1 then '三' else this end type from test")
                .build()
                .start(Flux.range(0, 4))
                .doOnNext(System.out::println)
                .cast(Map.class)
                .map(map -> map.get("type"))
                .as(StepVerifier::create)
                .expectNext(0, "一", "二", "三")
                .verifyComplete();

    }


    @Test
    void testSimpleMap() {

        ReactorQL.builder()
                .sql("select _name name from test")
                .build()
                .start(Flux.just(Collections.singletonMap("_name", "test")))
                .as(StepVerifier::create)
                .expectNext(Collections.singletonMap("name", "test"))
                .verifyComplete();

    }

    @Test
    void testNow() {
        ReactorQL.builder()
                .sql("select now('yyyy-MM-dd') now from dual")
                .build()
                .start(Flux.just(1))
                .as(StepVerifier::create)
                .expectNext(Collections.singletonMap("now", DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now())))
                .verifyComplete();
    }


    @Test
    void testNestGroup() {
        ReactorQL.builder()
                .sql(
                        "select sum(val+val2) v,avg(val+val2) avg,val2/2 g from (",

                        "select ",
                        "10 val,",
                        "this val2",
                        "from dual",

                        ") group by val2/2"
                )
                .build()
                .start(Flux.range(0, 10))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNextCount(5)
                .verifyComplete();

    }

    @Test
    void testCalculatePriority() {
        ReactorQL.builder()
                .sql(
                        "select ",
                        "3+2-5*0 val",
                        ",3+2*2 val2",
                        ",2*(3+2) val3",
                        ",math.floor(math.log(32.2)) log",
                        ",math.max(1,2) max",
                        "from dual"
                )
                .build()
                .start(Flux.just(1))
                .as(StepVerifier::create)
                .expectNext(new HashMap<String, Object>() {{
                    put("val", 5L);
                    put("val2", 7L);
                    put("val3", 10L);
                    put("log", 3.0);
                    put("max", 2L);
                }})
                .verifyComplete();
    }

    @Test
    void testCast() {
        ReactorQL.builder()
                .sql(
                        "select ",
                        "cast(this as string) val",
                        ",cast('y' as boolean) bool1",
                        ",cast('1' as bool) bool2",
                        ",cast('false' as bool) bool3",
                        ",cast(100.2 as int) int",
                        ",cast('100.3' as double) d",
                        ",cast(101.3-this as float) float",
                        ",cast('2020-01-01' as date) date",
                        ",cast('1.0E32' as decimal) decimal",

                        "from dual"
                )
                .build()
                .start(Flux.just(1))
                .as(StepVerifier::create)
                .expectNext(new HashMap<String, Object>() {{
                    put("val", "1");
                    put("int", 100);
                    put("d", 100.3D);
                    put("float", 100.3F);
                    put("bool1", true);
                    put("bool2", true);
                    put("bool3", false);
                    put("date", DateFormatter.fromString("2020-01-01"));
                    put("decimal", new BigDecimal("1.0E32"));

                }})
                .verifyComplete();
    }

    @Test
    void testDateFormat() {
        ReactorQL.builder()
                .sql("select date_format(this,'yyyy-MM-dd','Asia/Shanghai') now from dual")
                .build()
                .start(Flux.just(System.currentTimeMillis()))
                .as(StepVerifier::create)
                .expectNext(Collections.singletonMap("now", DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))))
                .verifyComplete();
    }

    @Test
    void testNestQuery() {
        ReactorQL.builder()
                .sql("select (select date_format(n.t,'yyyy-MM-dd','Asia/Shanghai') now from (select now() t) n) now_obj")
                .build()
                .start(Flux.just(System.currentTimeMillis()))
                .as(StepVerifier::create)
                .expectNext(Collections.singletonMap("now_obj", Collections.singletonMap("now", DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))))
                .verifyComplete();
    }

    @Test
    void testConcat() {
        ReactorQL.builder()
                .sql("select concat(1,2,3,4) v, 1||2 v2 ,concat(row_to_array((select 1 a1))) v3 from dual")
                .build()
                .start(Flux.just(System.currentTimeMillis()))
                .as(StepVerifier::create)
                .expectNext(new HashMap<String, Object>() {{
                    put("v", "1234");
                    put("v2", "12");
                    put("v3", "1");
                }})
                .verifyComplete();
    }


    @Test
    void testBit() {
        ReactorQL.builder()
                .sql("select ",
                        "1^3", ",bit_mutex(2,4)",
                        ",1&3", ",bit_and(3,8)",
                        ",1|3", ",bit_or(3,9)",
                        ",1<<3", ",bit_left_shift(1,3)",
                        ",1>>3", ",bit_right_shift(1,3)",
                        ",bit_unsigned_shift(1,3)",//1 >>>3
                        ",~3,-3,-(-3)", //sign
                        ",bit_not(3)",
                        ",bit_count(30)",
                        " from dual")
                .build()
                .start(Flux.just(System.currentTimeMillis()))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();
    }


    @Test
    void testNewMap() {
        ReactorQL.builder()
                .sql("select new_map('1',1,'2',2) v from dual")
                .build()
                .start(Flux.just(System.currentTimeMillis()))
                .as(StepVerifier::create)
                .expectNext(new HashMap<String, Object>() {{
                    put("v", new HashMap<Object, Object>() {
                        {
                            put("1", 1L);
                            put("2", 2L);
                        }
                    });
                }})
                .verifyComplete();
    }

    @Test
    void testNewArray() {
        ReactorQL.builder()
                .sql("select new_array(1,2,3,4) v from dual")
                .build()
                .start(Flux.just(System.currentTimeMillis()))
                .as(StepVerifier::create)
                .expectNext(new HashMap<String, Object>() {{
                    put("v", Arrays.asList(1L, 2L, 3L, 4L));
                }})
                .verifyComplete();
    }

    @Test
    void testJoin() {
        ReactorQL.builder()
                .sql("select t1.name,t2.name from t1,t2")
                .build()
                .start(t -> {
                    return Flux.just(Collections.singletonMap("name", t));
                })
                .as(StepVerifier::create)
                .expectNext(new HashMap<String, Object>() {{
                    put("t1.name", "t1");
                    put("t2.name", "t2");
                }})
                .verifyComplete();
    }

    @Test
    void testWhereCase() {
        ReactorQL.builder()
                .sql("select t.key\n" +
                        "from t\n" +
                        "where (\n" +
                        "          case \n" +
                        "              when t.key = '1' then t.value = '2'\n" +
                        "              when t.key = '2' then t.value = '3'\n" +
                        "              end\n" +
                        "          )")
                .build()
                .start(Flux.just(
                        new HashMap<String, Object>() {
                            {
                                put("key", "1");
                                put("value", "2");
                            }
                        },
                        new HashMap<String, Object>() {
                            {
                                put("key", "2");
                                put("value", "2");
                            }
                        }
                ))
                .as(StepVerifier::create)
                .expectNext(new HashMap<String, Object>() {{
                    put("t.key", "1");
                }})
                .verifyComplete();
    }


    @Test
    void testRightJoin() {
        ReactorQL.builder()
                .sql(
                        "select t1.name,t2.name,t1.v,t2.v from t1 ",
                        "right join t2 on t1.v=t2.v"
                )
                .build()
                .start(t -> Flux.range(0, 2)
                        .map(v -> new HashMap<String, Object>() {
                            {
                                put("name", t);
                                put("v", v);
                            }
                        }))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNextCount(4)
                .verifyComplete();
    }

    @Test
    void testLeftJoin() {
        ReactorQL.builder()
                .sql(
                        "select t1.name,t2.name,t1.v,t2.v from t1 ",
                        "left join t2 on t1.v=t2.v"
                )
                .build()
                .start(t -> Flux.range(0, t.equals("t1") ? 3 : 2)
                        .map(v -> new HashMap<String, Object>() {
                            {
                                put("name", t);
                                put("v", v);
                            }
                        }))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    void testSubJoin() {
        ReactorQL.builder()
                .sql(
                        "select t1.name,t2.name,t1.v,t2.v from t1 ",
                        "left join (select this.v v ,name from t2 ) t2 on t1.v=t2.v"
                )
                .build()
                .start(t -> Flux.range(0, 2)
                        .map(v -> new HashMap<String, Object>() {
                            {
                                put("name", t);
                                put("v", v);
                            }
                        }))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNext(new HashMap<String, Object>() {{
                    put("t1.name", "t1");
                    put("t2.name", "t2");
                    put("t1.v", 0);
                    put("t2.v", 0);

                }}, new HashMap<String, Object>() {{
                    put("t1.name", "t1");
                    put("t2.name", "t2");
                    put("t1.v", 1);
                    put("t2.v", 1);
                }})
                .verifyComplete();
    }

    @Test
    void testJoinWhere() {
        ReactorQL.builder()
                .sql("select t1.name,t2.name,t1.v,t2.v from t1,t2 where t1.v=t2.v")
                .build()
                .start(t -> Flux.range(0, 2)
                        .map(v -> new HashMap<String, Object>() {
                            {
                                put("name", t);
                                put("v", v);
                            }
                        }))
                .doOnNext(System.out::println)
                .as(StepVerifier::create)
                .expectNext(new HashMap<String, Object>() {{
                    put("t1.name", "t1");
                    put("t2.name", "t2");
                    put("t1.v", 0);
                    put("t2.v", 0);

                }}, new HashMap<String, Object>() {{
                    put("t1.name", "t1");
                    put("t2.name", "t2");
                    put("t1.v", 1);
                    put("t2.v", 1);
                }})
                .verifyComplete();
    }


}