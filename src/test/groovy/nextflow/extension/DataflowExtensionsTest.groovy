/*
 * Copyright (c) 2013-2014, Centre for Genomic Regulation (CRG).
 * Copyright (c) 2013-2014, Paolo Di Tommaso and the respective authors.
 *
 *   This file is part of 'Nextflow'.
 *
 *   Nextflow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Nextflow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Nextflow.  If not, see <http://www.gnu.org/licenses/>.
 */

package nextflow.extension

import java.nio.file.Paths

import groovyx.gpars.dataflow.DataflowQueue
import groovyx.gpars.dataflow.DataflowVariable
import nextflow.Channel
import nextflow.Session
import spock.lang.Specification
import spock.lang.Timeout

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DataflowExtensionsTest extends Specification {

    def testHandlerNames() {

        when:
        DataflowExtensions.checkSubscribeHandlers( [:] )
        then:
        thrown(IllegalArgumentException)

        when:
        DataflowExtensions.checkSubscribeHandlers( [ onNext:{}] )
        then:
        true

        when:
        DataflowExtensions.checkSubscribeHandlers( [ onNext:{}, xxx:{}] )
        then:
        thrown(IllegalArgumentException)

        when:
        DataflowExtensions.checkSubscribeHandlers( [ xxx:{}] )
        then:
        thrown(IllegalArgumentException)
    }

    def testFilter() {

        when:
        def c1 = Channel.from(1,2,3,4,5).filter { it > 3 }
        then:
        c1.val == 4
        c1.val == 5
        c1.val == Channel.STOP

        when:
        def c2 = Channel.from('hola','hello','cioa','miao').filter { it =~ /^h.*/ }
        then:
        c2.val == 'hola'
        c2.val == 'hello'
        c2.val == Channel.STOP

        when:
        def c3 = Channel.from('hola','hello','cioa','miao').filter { it ==~ /^h.*/ }
        then:
        c3.val == 'hola'
        c3.val == 'hello'
        c3.val == Channel.STOP

        when:
        def c4 = Channel.from('hola','hello','cioa','miao').filter( ~/^h.*/ )
        then:
        c4.val == 'hola'
        c4.val == 'hello'
        c4.val == Channel.STOP

        when:
        def c5 = Channel.from('hola',1,'cioa',2,3).filter( Number )
        then:
        c5.val == 1
        c5.val == 2
        c5.val == 3
        c5.val == Channel.STOP

        expect:
        Channel.from(1,2,4,2,4,5,6,7,4).filter(1) .count().val == 1
        Channel.from(1,2,4,2,4,5,6,7,4).filter(2) .count().val == 2
        Channel.from(1,2,4,2,4,5,6,7,4).filter(4) .count().val == 3

    }

    def testSubscribe() {

        when:
        def channel = Channel.create()
        int count = 0
        channel.subscribe { count++; } << 1 << 2 << 3
        sleep(100)
        then:
        count == 3

        when:
        count = 0
        channel = Channel.from(1,2,3,4)
        channel.subscribe { count++; }
        sleep(100)
        then:
        count == 4


    }

    def testSubscribe1() {

        when:
        def count = 0
        def done = false
        Channel.from(1,2,3).subscribe onNext:  { count++ }, onComplete: { done = true }
        sleep 100
        then:
        done
        count == 3

    }

    def testSubscribe2() {

        when:
        def count = 0
        def done = false
        Channel.just(1).subscribe onNext:  { count++ }, onComplete: { done = true }
        sleep 100
        then:
        done
        count == 1

    }

    def testSubscribeError() {

        when:
        int next=0
        int error=0
        int complete=0
        Channel
                .from( 2,1,0,3,3 )
                .subscribe onNext: { println it/it; next++ }, onError: { error++ }, onComplete: { complete++ }
        sleep 100

        then:
        // on the third iteration raise an exception
        // next have to be equals to two
        next == 2
        // error have to be invoked one time
        error == 1
        // complete never
        complete == 0

    }


    def testMap() {
        when:
        def result = Channel.from(1,2,3).map { "Hello $it" }
        then:
        result.val == 'Hello 1'
        result.val == 'Hello 2'
        result.val == 'Hello 3'
        result.val == Channel.STOP
    }

    def testMapWithVariable() {
        given:
        def variable = Channel.just('Hello')
        when:
        def result = variable.map { it.reverse() }
        then:
        result.val == 'olleH'
        result.val == 'olleH'
        result.val == 'olleH'
    }

    def testMapParamExpanding () {

        when:
        def result = Channel.from(1,2,3).map { [it, it] }.map { x, y -> x+y }
        then:
        result.val == 2
        result.val == 4
        result.val == 6
        result.val == Channel.STOP
    }

    def testSkip() {

        when:
        def result = Channel.from(1,2,3).map { it == 2 ? Channel.VOID : "Hello $it" }
        then:
        result.val == 'Hello 1'
        result.val == 'Hello 3'
        result.val == Channel.STOP

    }


    def testMapMany () {

        when:
        def result = Channel.from(1,2,3).flatMap { it -> [it, it*2] }
        then:
        result.val == 1
        result.val == 2
        result.val == 2
        result.val == 4
        result.val == 3
        result.val == 6
        result.val == Channel.STOP
    }

    @Timeout(1)
    def testMapManyWithSingleton() {

        when:
        def result = Channel.value([1,2,3]).flatMap()
        then:
        result.val == 1
        result.val == 2
        result.val == 3
        result.val == Channel.STOP

    }

    def testMapManyWithTuples () {

        when:
        def result = Channel.from( [1,2], ['a','b'] ).flatMap { it -> [it, it.reverse()] }
        then:
        result.val == [1,2]
        result.val == [2,1]
        result.val == ['a','b']
        result.val == ['b','a']
        result.val == Channel.STOP
    }

    def testMapManyWithHashArray () {

        when:
        def result = Channel.from(1,2,3).flatMap { it -> [ k: it, v: it*2] }
        then:
        result.val == new MapEntry('k',1)
        result.val == new MapEntry('v',2)
        result.val == new MapEntry('k',2)
        result.val == new MapEntry('v',4)
        result.val == new MapEntry('k',3)
        result.val == new MapEntry('v',6)
        result.val == Channel.STOP

    }



    def testReduce() {

        when:
        def channel = Channel.create()
        def result = channel.reduce { a, e -> a += e }
        channel << 1 << 2 << 3 << 4 << 5 << Channel.STOP
        then:
        result.getVal() == 15


        when:
        channel = Channel.from(1,2,3,4,5)
        result = channel.reduce { a, e -> a += e }
        then:
        result.getVal() == 15

        when:
        channel = Channel.create()
        result = channel.reduce { a, e -> a += e }
        channel << 99 << Channel.STOP
        then:
        result.getVal() == 99

        when:
        channel = Channel.create()
        result = channel.reduce { a, e -> a += e }
        channel << Channel.STOP
        then:
        result.getVal() == null

        when:
        result = Channel.from(6,5,4,3,2,1).reduce { a, e -> Channel.STOP }
        then:
        result.val == 6

    }


    def testReduceWithSeed() {

        when:
        def channel = Channel.create()
        def result = channel.reduce (1) { a, e -> a += e }
        channel << 1 << 2 << 3 << 4 << 5 << Channel.STOP
        then:
        result.getVal() == 16

        when:
        channel = Channel.create()
        result = channel.reduce (10) { a, e -> a += e }
        channel << Channel.STOP
        then:
        result.getVal() == 10

        when:
        result = Channel.from(6,5,4,3,2,1).reduce(0) { a, e -> a < 3 ? a+1 : Channel.STOP }
        then:
        result.val == 3

    }

    def testFirst() {

        expect:
        Channel.from(3,6,4,5,4,3,4).first().val == 3
    }


    def testFirstWithCondition() {

        expect:
        Channel.from(3,6,4,5,4,3,4).first { it % 2 == 0  } .val == 6
        Channel.from( 'a', 'b', 'c', 1, 2 ).first( Number ) .val == 1
        Channel.from( 'a', 'b', 1, 2, 'aaa', 'bbb' ).first( ~/aa.*/ ) .val == 'aaa'
        Channel.from( 'a', 'b', 1, 2, 'aaa', 'bbb' ).first( 1 ) .val == 1

    }


    def testTake() {

        when:
        def result = Channel.from(1,2,3,4,5,6).take(3)
        then:
        result.val == 1
        result.val == 2
        result.val == 3
        result.val == Channel.STOP

        when:
        result = Channel.from(1).take(3)
        then:
        result.val == 1
        result.val == Channel.STOP

        when:
        result = Channel.from(1,2,3).take(0)
        then:
        result.val == Channel.STOP

        when:
        result = Channel.from(1,2,3).take(-1)
        then:
        result.val == 1
        result.val == 2
        result.val == 3
        result.val == Channel.STOP

        when:
        result = Channel.from(1,2,3).take(3)
        then:
        result.val == 1
        result.val == 2
        result.val == 3
        result.val == Channel.STOP

    }

    def testLast() {

        expect:
        Channel.from(3,6,4,5,4,3,9).last().val == 9
        Channel.just('x').last().val == 'x'
    }

    def testInto() {
        when:
        def result = Channel.from(1,2,3,4)
        def (ch1, ch2) = result.into(2)

        then:
        ch1.val == 1
        ch1.val == 2
        ch1.val == 3
        ch1.val == 4
        ch1.val == Channel.STOP

        ch2.val == 1
        ch2.val == 2
        ch2.val == 3
        ch2.val == 4
        ch2.val == Channel.STOP
    }

    def testInto2() {
        when:
        def result = Channel.from('a','b',[1,2])
        def ch1 = Channel.create()
        def ch2 = Channel.create()
        def ch3 = Channel.create()
        result.into(ch1, ch2, ch3)

        then:
        ch1.val == 'a'
        ch1.val == 'b'
        ch1.val == [1,2]
        ch1.val == Channel.STOP

        ch2.val == 'a'
        ch2.val == 'b'
        ch2.val == [1,2]
        ch2.val == Channel.STOP

        ch3.val == 'a'
        ch3.val == 'b'
        ch3.val == [1,2]
        ch3.val == Channel.STOP
    }

    @Timeout(1)
    def testIntoWithSingleton() {

        when:
        def result = Channel.create()
        Channel.value('Hello').into(result)
        then:
        result.val == 'Hello'
        result.val == Channel.STOP

    }

    def 'should create new dataflow variables and forward item to them'  () {

        given:
        def session = new Session()

        when:
        Channel.from(10,2,30).into { alpha; gamma }
        then:
        session.binding.alpha.val == 10
        session.binding.alpha.val == 2
        session.binding.alpha.val == 30
        session.binding.alpha.val == Channel.STOP

        session.binding.gamma.val == 10
        session.binding.gamma.val == 2
        session.binding.gamma.val == 30
        session.binding.gamma.val == Channel.STOP

    }

    def 'should `tap` item to a new channel' () {
        given:
        def session = new Session()

        when:
        def result = Channel.from( 4,7,9 ) .tap { first }.map { it+1 }
        then:
        session.binding.first.val == 4
        session.binding.first.val == 7
        session.binding.first.val == 9
        session.binding.first.val == Channel.STOP

        result.val == 5
        result.val == 8
        result.val == 10
        result.val == Channel.STOP

    }


    def testMin() {

        expect:
        Channel.from(4,1,7,5).min().val == 1
        Channel.from("hello","hi","hey").min { it.size() } .val == "hi"
        Channel.from("hello","hi","hey").min { a,b -> a.size()<=>b.size() } .val == "hi"
        Channel.from("hello","hi","hey").min { a,b -> a.size()<=>b.size() } .val == "hi"
        Channel.from("hello","hi","hey").min ({ a,b -> a.size()<=>b.size() } as Comparator) .val == "hi"

    }

    def testMax() {
        expect:
        Channel.from(4,1,7,5).max().val == 7
        Channel.from("hello","hi","hey").max { it.size() } .val == "hello"
        Channel.from("hello","hi","hey").max { a,b -> a.size()<=>b.size() } .val == "hello"
        Channel.from("hello","hi","hey").max { a,b -> a.size()<=>b.size() } .val == "hello"
        Channel.from("hello","hi","hey").max ({ a,b -> a.size()<=>b.size() } as Comparator) .val == "hello"

    }

    def testSum() {
        expect:
        Channel.from(4,1,7,5).sum().val == 17
        Channel.from(4,1,7,5).sum { it * 2 } .val == 34
        Channel.from( [1,1,1], [0,1,2], [10,20,30] ). sum() .val == [ 11, 22, 33 ]
    }


    def testMean() {
        expect:
        Channel.from(10,20,30).mean().val == 20
        Channel.from(10,20,30).mean { it * 2 }.val == 40
        Channel.from( [10,20,30], [10, 10, 10 ], [10, 30, 50]).mean().val == [10, 20, 30]
    }

    def testCount() {
        expect:
        Channel.from(4,1,7,5).count().val == 4
        Channel.from(4,1,7,1,1).count(1).val == 3
        Channel.from('a','c','c','q','b').count ( ~/c/ ) .val == 2
    }

    def testCountBy() {
        expect:
        Channel.from('hello','ciao','hola', 'hi', 'bonjour').countBy { it[0] } .val == [c:1, b:1, h:3]
    }


    def testGroupBy() {

        def result

        when:
        result = Channel.from('hello','ciao','hola', 'hi', 'bonjour').groupBy { String str -> str[0] }
        then:
        result.val == [c:['ciao'], b:['bonjour'], h:['hello','hola','hi']]

        when:
        result = Channel.from( [id: 1, str:'one'], [id: 2, str:'two'], [id: 2, str:'dos'] ).groupBy()
        then:
        result.val == [ 1: [[id: 1, str:'one']], 2: [[id: 2, str:'two'], [id: 2, str:'dos']] ]

        when:
        result = Channel.from( [1, 'a' ], [2, 'b'], [1, 'c'], [1, 'd'], [3, 'z'] ).groupBy()
        then:
        result.val == [1: [[1,'a'], [1,'c'], [1,'d']], 2: [[2,'b']], 3: [[3,'z']]]
        result instanceof DataflowVariable

        when:
        result = Channel.from( [1, 'a' ], [2, 'b'], [3, 'a'], [4, 'c'], [5, 'a'] ).groupBy(1)
        then:
        result.val == [a: [[1,'a'], [3,'a'], [5,'a']], b: [[2,'b']], c: [[4,'c']]]
        result instanceof DataflowVariable

        when:
        result = Channel.from( [id: 1, str:'one'], [id: 2, str:'two'], [id: 2, str:'dos'] ).groupBy()
        then:
        result.val == [ 1: [[id: 1, str:'one']], 2: [[id: 2, str:'two'], [id: 2, str:'dos']] ]
        result instanceof DataflowVariable

        when:
        result = Channel.from('hello','ciao','hola', 'hi', 'bonjour').groupBy { String str -> str[0] }
        then:
        result.val == [c:['ciao'], b:['bonjour'], h:['hello','hola','hi']]
        result instanceof DataflowVariable

    }

    def testRouteBy() {

        when:
        def result = Channel.from('hello','ciao','hola', 'hi', 'bonjour').route { it[0] }
        result.subscribe {
          def (key, channel) = it
          channel.subscribe { println "group: $key >> $it" }
        }

        sleep 1000
        then:
        true

    }

    def testRouteByMap() {

        setup:
        def r1 = Channel.create()
        def r2 = Channel.create()
        def r3 = Channel.create()

        when:
        Channel.from('hello','ciao','hola', 'hi', 'x', 'bonjour').route ( b: r1, c: r2, h: r3 ) { it[0] }

        then:
        r1.val == 'bonjour'
        r1.val == Channel.STOP

        r2.val == 'ciao'
        r2.val == Channel.STOP

        r3.val == 'hello'
        r3.val == 'hola'
        r3.val == 'hi'
        r3.val == Channel.STOP

    }

    def testToList() {

        when:
        def channel = Channel.from(1,2,3)
        then:
        channel.toList().val == [1,2,3]

        when:
        channel = Channel.create()
        channel << Channel.STOP
        then:
        channel.toList().val == []

    }

    def testToSortedList() {

        when:
        def channel = Channel.from(3,1,4,2)
        then:
        channel.toSortedList().val == [1,2,3,4]

        when:
        channel = Channel.create()
        channel << Channel.STOP
        then:
        channel.toSortedList().val == []

        when:
        channel = Channel.from([1,'zeta'], [2,'gamma'], [3,'alpaha'], [4,'delta'])
        then:
        channel.toSortedList { it[1] } .val == [[3,'alpaha'], [4,'delta'], [2,'gamma'], [1,'zeta'] ]

    }


    def testUnique() {
        expect:
        Channel.from(1,1,1,5,7,7,7,3,3).unique().toList().val == [1,5,7,3]
        Channel.from(1,3,4,5).unique { it%2 } .toList().val == [1,4]
    }

    def testDistinct() {
        expect:
        Channel.from(1,1,2,2,2,3,1,1,2,2,3).distinct().toList().val == [1,2,3,1,2,3]
        Channel.from(1,1,2,2,2,3,1,1,2,4,6).distinct { it%2 } .toList().val == [1,2,3,2]
    }


    def testSeparate() {

        when:
        def str = 'abcdef'
        def (ch1, ch2) = Channel.from(0..3).separate(2) { [it, str[it]] }
        then:
        ch1.val == 0
        ch1.val == 1
        ch1.val == 2
        ch1.val == 3
        ch1.val == Channel.STOP

        ch2.val == 'a'
        ch2.val == 'b'
        ch2.val == 'c'
        ch2.val == 'd'
        ch2.val == Channel.STOP
    }


    def testSeparate2() {


        when:
        def str2 = 'abcdef'
        def (ch3, ch4) = Channel.from(0..3).map { [it, it+1] } .separate(2)
        then:
        ch3.val == 0
        ch3.val == 1
        ch3.val == 2
        ch3.val == 3
        ch3.val == Channel.STOP

        ch4.val == 1
        ch4.val == 2
        ch4.val == 3
        ch4.val == 4
        ch4.val == Channel.STOP

    }

    def testSeparate3() {

        when:
        def s1 = Channel.create()
        def s2 = Channel.create()
        def s3 = Channel.create()

        Channel.from(1,2,3,4)
                .separate([s1,s2,s3]) { item -> [item+1, item*item, item-1] }

        then:
        s1.val == 2
        s1.val == 3
        s1.val == 4
        s1.val == 5
        s1.val == Channel.STOP
        s2.val == 1
        s2.val == 4
        s2.val == 9
        s2.val == 16
        s2.val == Channel.STOP
        s3.val == 0
        s3.val == 1
        s3.val == 2
        s3.val == 3
        s3.val == Channel.STOP

    }


    def testSeparate4() {
        when:
        def x = Channel.create()
        def y = Channel.create()
        def source = Channel.from([1,2], ['a','b'], ['p','q'])
        source.separate(x,y)
        then:
        x.val == 1
        x.val == 'a'
        x.val == 'p'
        x.val == Channel.STOP
        y.val == 2
        y.val == 'b'
        y.val == 'q'
        y.val == Channel.STOP

        when:
        def x2 = Channel.create()
        def y2 = Channel.create()
        def source2 = Channel.from([1,2], ['a','c','b'], 'z')
        source2.separate(x2,y2)
        then:
        x2.val == 1
        x2.val == 'a'
        x2.val == 'z'
        x2.val == Channel.STOP
        y2.val == 2
        y2.val == 'c'
        y2.val == null
        y2.val == Channel.STOP
    }

    def testSpread() {

        when:
        def r1 = Channel.from(1,2,3).spread(['a','b'])
        then:
        r1.val == [1, 'a']
        r1.val == [1, 'b']
        r1.val == [2, 'a']
        r1.val == [2, 'b']
        r1.val == [3, 'a']
        r1.val == [3, 'b']
        r1.val == Channel.STOP

        when:
        def str = Channel.from('a','b','c')
        def r2 = Channel.from(1,2).spread(str)
        then:
        r2.val == [1, 'a']
        r2.val == [1, 'b']
        r2.val == [1, 'c']
        r2.val == [2, 'a']
        r2.val == [2, 'b']
        r2.val == [2, 'c']
        r2.val == Channel.STOP

    }

    def testSpreadChained() {

        when:
        def str1 = Channel.from('a','b','c')
        def str2 = Channel.from('x','y')
        def result = Channel.from(1,2).spread(str1).spread(str2)
        then:
        result.val == [1,'a','x']
        result.val == [1,'a','y']
        result.val == [1,'b','x']
        result.val == [1,'b','y']
        result.val == [1,'c','x']
        result.val == [1,'c','y']
        result.val == [2,'a','x']
        result.val == [2,'a','y']
        result.val == [2,'b','x']
        result.val == [2,'b','y']
        result.val == [2,'c','x']
        result.val == [2,'c','y']
        result.val == Channel.STOP

    }


    def testSpreadTuple() {

        when:
        def result = Channel.from([1, 'x'], [2,'y'], [3, 'z']).spread( ['alpha','beta','gamma'] )

        then:
        result.val == [1, 'x', 'alpha']
        result.val == [1, 'x', 'beta']
        result.val == [1, 'x', 'gamma']

        result.val == [2, 'y', 'alpha']
        result.val == [2, 'y', 'beta']
        result.val == [2, 'y', 'gamma']

        result.val == [3, 'z', 'alpha']
        result.val == [3, 'z', 'beta']
        result.val == [3, 'z', 'gamma']

        result.val == Channel.STOP
    }

    def testSpreadMap() {

        when:
        def result = Channel.from([id:1, val:'x'], [id:2,val:'y'], [id:3, val:'z']).spread( ['alpha','beta','gamma'] )

        then:
        result.val == [[id:1, val:'x'], 'alpha']
        result.val == [[id:1, val:'x'], 'beta']
        result.val == [[id:1, val:'x'], 'gamma']

        result.val == [[id:2,val:'y'], 'alpha']
        result.val == [[id:2,val:'y'], 'beta']
        result.val == [[id:2,val:'y'], 'gamma']

        result.val == [[id:3, val:'z'], 'alpha']
        result.val == [[id:3, val:'z'], 'beta']
        result.val == [[id:3, val:'z'], 'gamma']

        result.val == Channel.STOP
    }

    @Timeout(1)
    def testSpreadWithSingleton() {
        when:
        def result = Channel.value(7).spread(['a','b','c'])
        then:
        result.val == [7, 'a']
        result.val == [7, 'b']
        result.val == [7, 'c']
        result.val == Channel.STOP
    }

    def testFlatten() {

        when:
        def r1 = Channel.from(1,2,3).flatten()
        then:
        r1.val == 1
        r1.val == 2
        r1.val == 3
        r1.val == Channel.STOP

        when:
        def r2 = Channel.from([1,'a'], [2,'b']).flatten()
        then:
        r2.val == 1
        r2.val == 'a'
        r2.val == 2
        r2.val == 'b'
        r2.val == Channel.STOP

        when:
        def r3 = Channel.from( [1,2] as Integer[], [3,4] as Integer[] ).flatten()
        then:
        r3.val == 1
        r3.val == 2
        r3.val == 3
        r3.val == 4
        r3.val == Channel.STOP

        when:
        def r4 = Channel.from( [1,[2,3]], 4, [5,[6]] ).flatten()
        then:
        r4.val == 1
        r4.val == 2
        r4.val == 3
        r4.val == 4
        r4.val == 5
        r4.val == 6
        r4.val == Channel.STOP

        when:
        def r5 = Channel.just( [1,2,3] ).flatten()
        then:
        r5.val == 1
        r5.val == 2
        r5.val == 3
        r5.val == Channel.STOP

    }

    @Timeout(1)
    def testFlattenWithSingleton() {
        when:
        def result = Channel.value([3,2,1]).flatten()
        then:
        result.val == 3
        result.val == 2
        result.val == 1
        result.val == Channel.STOP
    }

    def testCollate() {

        when:
        def r1 = Channel.from(1,2,3,1,2,3,1).collate( 2, false )
        then:
        r1.val == [1,2]
        r1.val == [3,1]
        r1.val == [2,3]
        r1.val == Channel.STOP

        when:
        def r2 = Channel.from(1,2,3,1,2,3,1).collate( 3 )
        then:
        r2.val == [1,2,3]
        r2.val == [1,2,3]
        r2.val == [1]
        r2.val == Channel.STOP

    }

    def testCollateWithStep() {

        when:
        def r1 = Channel.from(1,2,3,4).collate( 3, 1, false )
        then:
        r1.val == [1,2,3]
        r1.val == [2,3,4]
        r1.val == Channel.STOP

        when:
        def r2 = Channel.from(1,2,3,4).collate( 3, 1, true )
        then:
        r2.val == [1,2,3]
        r2.val == [2,3,4]
        r2.val == [3,4]
        r2.val == [4]
        r2.val == Channel.STOP

        when:
        def r3 = Channel.from(1,2,3,4).collate( 3, 1  )
        then:
        r3.val == [1,2,3]
        r3.val == [2,3,4]
        r3.val == [3,4]
        r3.val == [4]
        r3.val == Channel.STOP

        when:
        def r4 = Channel.from(1,2,3,4).collate( 4,4 )
        then:
        r4.val == [1,2,3,4]
        r4.val == Channel.STOP

        when:
        def r5 = Channel.from(1,2,3,4).collate( 6,6 )
        then:
        r5.val == [1,2,3,4]
        r5.val == Channel.STOP

        when:
        def r6 = Channel.from(1,2,3,4).collate( 6,6,false )
        then:
        r6.val == Channel.STOP

    }

    def testCollateIllegalArgs() {
        when:
        Channel.create().collate(0)
        then:
        thrown(IllegalArgumentException)

        when:
        Channel.create().collate(-1)
        then:
        thrown(IllegalArgumentException)

        when:
        Channel.create().collate(0,1)
        then:
        thrown(IllegalArgumentException)

        when:
        Channel.create().collate(1,0)
        then:
        thrown(IllegalArgumentException)

    }


    def testBufferClose() {

        when:
        def r1 = Channel.from(1,2,3,1,2,3).buffer({ it == 2 })
        then:
        r1.val == [1,2]
        r1.val == [3,1,2]
        r1.val == Channel.STOP

        when:
        def r2 = Channel.from('a','b','c','a','b','z').buffer(~/b/)
        then:
        r2.val == ['a','b']
        r2.val == ['c','a','b']
        r2.val == Channel.STOP

    }

    def testBufferWithCount() {

        when:
        def r1 = Channel.from(1,2,3,1,2,3,1).buffer( size:2 )
        then:
        r1.val == [1,2]
        r1.val == [3,1]
        r1.val == [2,3]
        r1.val == Channel.STOP

        when:
        r1 = Channel.from(1,2,3,1,2,3,1).buffer( size:2, remainder: true )
        then:
        r1.val == [1,2]
        r1.val == [3,1]
        r1.val == [2,3]
        r1.val == [1]
        r1.val == Channel.STOP


        when:
        def r2 = Channel.from(1,2,3,4,5,1,2,3,4,5,1,2,9).buffer( size:3, skip:2 )
        then:
        r2.val == [3,4,5]
        r2.val == [3,4,5]
        r2.val == Channel.STOP

        when:
        r2 = Channel.from(1,2,3,4,5,1,2,3,4,5,1,2,9).buffer( size:3, skip:2, remainder: true )
        then:
        r2.val == [3,4,5]
        r2.val == [3,4,5]
        r2.val == [9]
        r2.val == Channel.STOP

    }

    def testBufferInvalidArg() {

        when:
        Channel.create().buffer( xxx: true )

        then:
        IllegalArgumentException e = thrown()

    }


    def testBufferOpenClose() {

        when:
        def r1 = Channel.from(1,2,3,4,5,1,2,3,4,5,1,2).buffer( 2, 4 )
        then:
        r1.val == [2,3,4]
        r1.val == [2,3,4]
        r1.val == Channel.STOP

        when:
        def r2 = Channel.from('a','b','c','a','b','z').buffer(~/a/,~/b/)
        then:
        r2.val == ['a','b']
        r2.val == ['a','b']
        r2.val == Channel.STOP

    }

    def testMix() {
        when:
        def c1 = Channel.from( 1,2,3 )
        def c2 = Channel.from( 'a','b' )
        def c3 = Channel.just( 'z' )
        def result = c1.mix(c2,c3).toList().val

        then:
        1 in result
        2 in result
        3 in result
        'a' in result
        'b' in result
        'z' in result
        !('c' in result)

    }

    @Timeout(1)
    def testMixWithSingleton() {
        when:
        def result = Channel.value(1).mix( Channel.from([2,3])  )
        then:
        result.toList().val.sort() == [1,2,3]
    }


    def testPhaseImpl() {

        setup:
        def result = null
        def ch1 = new DataflowQueue()
        def ch2 = new DataflowQueue()
        def ch3 = new DataflowQueue()

        when:
        def map = [ : ]
        result = PhaseOp.phaseImpl(map, 2, 0, 'a', { it })
        then:
        result == null
        map == [ a:[0: ['a']] ]

        when:
        map = [ : ]
        result = PhaseOp.phaseImpl(map, 2, 0, 'a', { it })
        result = PhaseOp.phaseImpl(map, 2, 1, 'a', { it })
        then:
        result == ['a','a']
        map == [ a:[:] ]


        when:
        def r1
        def r2
        def r3
        map = [ : ]
        r1 = PhaseOp.phaseImpl(map, 3, 0, 'a', { it })
        r1 = PhaseOp.phaseImpl(map, 3, 1, 'a', { it })
        r1 = PhaseOp.phaseImpl(map, 3, 2, 'a', { it })

        r2 = PhaseOp.phaseImpl(map, 3, 0, 'b', { it })
        r2 = PhaseOp.phaseImpl(map, 3, 1, 'b', { it })
        r2 = PhaseOp.phaseImpl(map, 3, 2, 'b', { it })

        r3 = PhaseOp.phaseImpl(map, 3, 0, 'z', { it })
        r3 = PhaseOp.phaseImpl(map, 3, 1, 'z', { it })
        r3 = PhaseOp.phaseImpl(map, 3, 1, 'z', { it })
        r3 = PhaseOp.phaseImpl(map, 3, 2, 'z', { it })

        then:
        r1 == ['a','a','a']
        r2 == ['b','b','b']
        r3 == ['z','z','z']
        map == [ a:[:], b:[:], z:[ 1:['z']] ]

    }

    def testDefaultMappingClosure() {

        expect:
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [7,8,9] ) == 7
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [7,8,9], 2 ) == 9
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [] ) == null
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [], 2 ) == null

        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [7,8,9] as Object[] ) == 7
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [7,8,9] as Object[], 1 ) == 8
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( ['alpha','beta'] as String[] ) == 'alpha'
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( ['alpha','beta'] as String[], 1 ) == 'beta'

        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [ 6,7,8,9 ] as LinkedHashSet ) == 6
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [ 6,7,8,9 ] as LinkedHashSet, 1 ) == 7
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [ 6,7,8,9 ] as LinkedHashSet, 2 ) == 8
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [ 6,7,8,9 ] as LinkedHashSet, 5 ) == null

        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [a:1, b:2, z:9] ) == 1
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [a:1, b:2, z:9], 1 ) == 2
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [a:1, b:2, z:9], 2 ) == 9
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [a:1, b:2, z:9], 3 ) == null

        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [a:1, b:2, z:9].entrySet().getAt(0) ) == 'a'
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [a:1, b:2, z:9].entrySet().getAt(0), 1 ) == 1
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [a:1, b:2, z:9].entrySet().getAt(0), 2 ) == null

        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [a:1, b:2, z:9].entrySet().getAt(1) ) == 'b'
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [a:1, b:2, z:9].entrySet().getAt(1), 1 ) == 2
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [a:1, b:2, z:9].entrySet().getAt(1), 2 ) == null

        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( [:] ) == null

        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( 99 ) == 99
        DataflowExtensions.DEFAULT_MAPPING_CLOSURE.call( 99, 2 ) == null

    }

    def testPhase() {

        setup:
        def ch1 = Channel.from( 1,2,3 )
        def ch2 = Channel.from( 1,0,0,2,7,8,9,3 )

        when:
        def result = ch1.phase(ch2)
        then:
        result.val == [1,1]
        result.val == [2,2]
        result.val == [3,3]

        result.val == Channel.STOP


        when:
        ch1 = Channel.from( [sequence: 'aaaaaa', key: 1], [sequence: 'bbbbbb', key: 2] )
        ch2 = Channel.from( [val: 'zzzz', id: 3], [val: 'xxxxx', id: 1], [val: 'yyyyy', id: 2])
        result = ch1.phase(ch2) { Map it ->
            if( it.containsKey('key') ) {
                return it.key
            }
            else if( it.containsKey('id') ) {
                return it.id
            }
            return null
        }
        then:

        result.val == [ [sequence: 'aaaaaa', key: 1], [val: 'xxxxx', id: 1] ]
        result.val == [ [sequence: 'bbbbbb', key: 2], [val: 'yyyyy', id: 2] ]
        result.val == Channel.STOP

    }

    def testPhaseWithRemainder() {

        def ch1
        def ch2
        def result

        when:
        ch1 = Channel.from( 1,2,3 )
        ch2 = Channel.from( 1,0,0,2,7,8,9,3 )
        result = ch1.phase(ch2, remainder: true)

        then:
        result.val == [1,1]
        result.val == [2,2]
        result.val == [3,3]
        result.val == [null,0]
        result.val == [null,0]
        result.val == [null,7]
        result.val == [null,8]
        result.val == [null,9]
        result.val == Channel.STOP


        when:
        ch1 = Channel.from( 1,0,0,2,7,8,9,3 )
        ch2 = Channel.from( 1,2,3 )
        result = ch1.phase(ch2, remainder: true)

        then:
        result.val == [1,1]
        result.val == [2,2]
        result.val == [3,3]
        result.val == [0,null]
        result.val == [0,null]
        result.val == [7,null]
        result.val == [8,null]
        result.val == [9,null]
        result.val == Channel.STOP
    }


    def testCross() {

        setup:
        def ch1 = Channel.from(  [1, 'x'], [2,'y'], [3,'z'] )
        def ch2 = Channel.from( [1,11], [1,13], [2,21],[2,22], [2,23], [4,1], [4,2]  )

        when:
        def result = ch1.cross(ch2)

        then:
        result.val == [ [1, 'x'], [1,11] ]
        result.val == [ [1, 'x'], [1,13] ]
        result.val == [ [2, 'y'], [2,21] ]
        result.val == [ [2, 'y'], [2,22] ]
        result.val == [ [2, 'y'], [2,23] ]
        result.val == Channel.STOP

    }

    def testCross2() {

        setup:
        def ch1 = Channel.create()
        def ch2 = Channel.from ( ['PF00006', 'PF00006_mafft.aln'], ['PF00006', 'PF00006_clustalo.aln'])

        when:
        Thread.start {  sleep 100;   ch1 << ['PF00006', 'PF00006.sp_lib'] << Channel.STOP }
        def result = ch1.cross(ch2)

        then:
        result.val == [ ['PF00006', 'PF00006.sp_lib'], ['PF00006', 'PF00006_mafft.aln'] ]
        result.val == [ ['PF00006', 'PF00006.sp_lib'], ['PF00006', 'PF00006_clustalo.aln'] ]
        result.val == Channel.STOP

    }


    def testCross3() {

        setup:
        def ch1 = Channel.from([['PF00006', 'PF00006.sp_lib'] ])
        def ch2 = Channel.create ( )

        when:
        Thread.start {  sleep 100;  ch2 << ['PF00006', 'PF00006_mafft.aln'] <<  ['PF00006', 'PF00006_clustalo.aln']<< Channel.STOP }
        def result = ch1.cross(ch2)

        then:
        result.val == [ ['PF00006', 'PF00006.sp_lib'], ['PF00006', 'PF00006_mafft.aln'] ]
        result.val == [ ['PF00006', 'PF00006.sp_lib'], ['PF00006', 'PF00006_clustalo.aln'] ]
        result.val == Channel.STOP

    }


    def testConcat() {

        when:
        def c1 = Channel.from(1,2,3)
        def c2 = Channel.from('a','b','c')
        def all = c1.concat(c2)
        then:
        all.val == 1
        all.val == 2
        all.val == 3
        all.val == 'a'
        all.val == 'b'
        all.val == 'c'
        all.val == Channel.STOP

        when:
        def d1 = Channel.create()
        def d2 = Channel.from('a','b','c')
        def d3 = Channel.create()
        def result = d1.concat(d2,d3)

        Thread.start { sleep 20; d3 << 'p' << 'q' << Channel.STOP }
        Thread.start { sleep 100; d1 << 1 << 2 << Channel.STOP }

        then:
        result.val == 1
        result.val == 2
        result.val == 'a'
        result.val == 'b'
        result.val == 'c'
        result.val == 'p'
        result.val == 'q'
        result.val == Channel.STOP

    }

    @Timeout(1)
    def testContactWithSingleton() {
        when:
        def result = Channel.value(1).concat( Channel.from(2,3) )
        then:
        result.val == 1
        result.val == 2
        result.val == 3
        result.val == Channel.STOP
    }


    def testDataflowSeparateWithOpenArray() {

        when:
        def s1 = Channel.create()
        def s2 = Channel.create()
        def s3 = Channel.create()

        Channel.from(1,2,3,4)
                .separate(s1,s2,s3) { item -> [item+1, item*item, item-1] }

        then:
        s1.val == 2
        s1.val == 3
        s1.val == 4
        s1.val == 5
        s1.val == Channel.STOP
        s2.val == 1
        s2.val == 4
        s2.val == 9
        s2.val == 16
        s2.val == Channel.STOP
        s3.val == 0
        s3.val == 1
        s3.val == 2
        s3.val == 3
        s3.val == Channel.STOP

    }

    def testDataflowChoiceWithOpenArray() {

        when:
        def source = Channel.from 'Hello world', 'Hola', 'Hello John'
        def queue1 = Channel.create()
        def queue2 = Channel.create()

        source.choice( queue1, queue2 ) { a -> a =~ /^Hello.*/ ? 0 : 1 }

        then:
        queue1.val == 'Hello world'
        queue1.val == 'Hello John'
        queue1.val == Channel.STOP
        queue2.val == 'Hola'
        queue2.val == Channel.STOP

    }

    def testDataflowMergeWithOpenArray() {

        when:
        def alpha = Channel.from(1, 3, 5);
        def beta = Channel.from(2, 4, 6);
        def delta = Channel.from(7,8,1);

        def result = alpha.merge( beta, delta ) { a,b,c -> [a,b,c] }

        then:
        result.val == [1,2,7]
        result.val == [3,4,8]
        result.val == [5,6,1]
        result.val == Channel.STOP
    }


    def testGroupTuple() {

        when:
        def result = Channel
                        .from([1,'a'], [1,'b'], [2,'x'], [3, 'q'], [1,'c'], [2, 'y'], [3, 'q'])
                        .groupTuple()

        then:
        result.val == [1, ['a', 'b','c'] ]
        result.val == [2, ['x', 'y'] ]
        result.val == [3, ['q', 'q'] ]
        result.val == Channel.STOP

    }

    def testGroupTupleWithCount() {

        when:
        def result = Channel
                .from([1,'a'], [1,'b'], [2,'x'], [3, 'q'], [1,'d'], [1,'c'], [2, 'y'], [1,'f'])
                .groupTuple(size: 2)

        then:
        result.val == [1, ['a', 'b'] ]
        result.val == [1, ['d', 'c'] ]
        result.val == [2, ['x', 'y'] ]
        result.val == Channel.STOP

        when:
        result = Channel
                .from([1,'a'], [1,'b'], [2,'x'], [3, 'q'], [1,'d'], [1,'c'], [2, 'y'], [1,'f'])
                .groupTuple(size: 2, remainder: true)

        then:
        result.val == [1, ['a', 'b'] ]
        result.val == [1, ['d', 'c'] ]
        result.val == [2, ['x', 'y'] ]
        result.val == [3, ['q']]
        result.val == [1, ['f']]
        result.val == Channel.STOP

    }

    def testGroupTupleWithSortNatural() {

        when:
        def result = Channel
                .from([1,'z'], [1,'w'], [1,'a'], [1,'b'], [2, 'y'], [2,'x'], [3, 'q'], [1,'c'], [3, 'p'])
                .groupTuple(sort: true)

        then:
        result.val == [1, ['a', 'b','c','w','z'] ]
        result.val == [2, ['x','y'] ]
        result.val == [3, ['p', 'q'] ]
        result.val == Channel.STOP

        when:
        result = Channel
                .from([1,'z'], [1,'w'], [1,'a'], [1,'b'], [2, 'y'], [2,'x'], [3, 'q'], [1,'c'], [3, 'p'])
                .groupTuple(sort: 'natural')

        then:
        result.val == [1, ['a', 'b','c','w','z'] ]
        result.val == [2, ['x','y'] ]
        result.val == [3, ['p', 'q'] ]
        result.val == Channel.STOP

    }


    def testGroupTupleWithSortHash() {

        when:
        def result = Channel
                .from([1,'z'], [1,'w'], [1,'a'], [1,'b'], [2, 'y'], [2,'x'], [3, 'q'], [1,'c'], [3, 'p'])
                .groupTuple(sort: 'hash')

        then:
        result.val == [1, ['a', 'c','z','b','w'] ]
        result.val == [2, ['y','x'] ]
        result.val == [3, ['p', 'q'] ]
        result.val == Channel.STOP

    }

    def testGroupTupleWithComparator() {

        when:
        def result = Channel
                .from([1,'z'], [1,'w'], [1,'a'], [1,'b'], [2, 'y'], [2,'x'], [3, 'q'], [1,'c'], [3, 'p'])
                .groupTuple(sort: { o1, o2 -> o2<=>o1 } as Comparator )

        then:
        result.val == [1, ['z','w','c','b','a'] ]
        result.val == [2, ['y','x'] ]
        result.val == [3, ['q','p'] ]
        result.val == Channel.STOP

    }

    def testGroupTupleWithClosure() {

        when:
        def result = Channel
                .from([1,'z'], [1,'w'], [1,'a'], [1,'b'], [2, 'y'], [2,'x'], [3, 'q'], [1,'c'], [3, 'p'])
                .groupTuple(sort: { it -> it } )

        then:
        result.val == [1, ['a', 'b','c','w','z'] ]
        result.val == [2, ['x','y'] ]
        result.val == [3, ['p', 'q'] ]
        result.val == Channel.STOP

    }


    def testGroupTupleWithIndex () {

        given:
        def file1 = Paths.get('/path/file_1')
        def file2 = Paths.get('/path/file_2')
        def file3 = Paths.get('/path/file_3')

        when:
        def result = Channel
                .from([1,'a', file1], [1,'b',file2], [2,'x',file2], [3, 'q',file1], [1,'c',file3], [2, 'y',file3], [3, 'q',file1])
                .groupTuple(by: 2)

        then:
        result.val == [ [1,3,3], ['a','q','q'], file1 ]
        result.val == [ [1,2], ['b','x'], file2 ]
        result.val == [ [1,2], ['c','y'], file3 ]
        result.val == Channel.STOP


        when:
        result = Channel
                .from([1,'a', file1], [1,'b',file2], [2,'x',file2], [3, 'q',file1], [1,'c',file3], [2, 'y',file3], [3, 'q',file1])
                .groupTuple(by: [2])

        then:
        result.val == [ [1,3,3], ['a','q','q'], file1 ]
        result.val == [ [1,2], ['b','x'], file2 ]
        result.val == [ [1,2], ['c','y'], file3 ]
        result.val == Channel.STOP


        when:
        result = Channel
                .from([1,'a', file1], [1,'b',file2], [2,'x',file2], [1, 'q',file1], [3, 'y', file3], [1,'c',file2], [2, 'y',file2], [3, 'q',file1], [1, 'z', file2], [3, 'c', file3])
                .groupTuple(by: [0,2])

        then:
        result.val == [ 1, ['a','q'], file1 ]
        result.val == [ 1, ['b','c','z'], file2 ]
        result.val == [ 2, ['x','y'], file2 ]
        result.val == [ 3, ['y','c'], file3 ]
        result.val == [ 3, ['q'], file1 ]
        result.val == Channel.STOP

    }

    def testChannelIfEmpty() {

        def result

        when:
        result = Channel.from(1,2,3).ifEmpty(100)
        then:
        result.val == 1
        result.val == 2
        result.val == 3
        result.val == Channel.STOP

        when:
        result = Channel.empty().ifEmpty(100)
        then:
        result.val == 100
        result.val == Channel.STOP

        when:
        result = Channel.empty().ifEmpty { 1+2  }
        then:
        result.val == 3
        result.val == Channel.STOP

    }

    def 'should create a channel given a list'() {

        when:
        def result = [10,20,30].channel()
        then:
        result.val == 10
        result.val == 20
        result.val == 30
        result.val == Channel.STOP

    }

    def 'should close the dataflow channel' () {

        given:
        def source = Channel.create()
        source << 10
        source << 20
        source << 30
        def result = source.close()

        expect:
        result.is source
        result.val == 10
        result.val == 20
        result.val == 30
        result.val == Channel.STOP
    }

    def 'should assign a channel to new variable' () {
        given:
        def session = new Session()

        when:
        Channel.from(10,20,30)
                .map { it +2 }
                .set { result }

        then:
        session.binding.result.val == 12
        session.binding.result.val == 22
        session.binding.result.val == 32
        session.binding.result.val == Channel.STOP

    }


    def 'should assign singleton channel to a new variable' () {
        given:
        def session = new Session()

        when:
        Channel.value('Hello').set { result }

        then:
        session.binding.result.val == 'Hello'
        session.binding.result.val == 'Hello'
        session.binding.result.val == 'Hello'

    }

    def 'should always the same value' () {

        when:
        def x = Channel.value('Hello')
        then:
        x.val == 'Hello'
        x.val == 'Hello'
        x.val == 'Hello'

    }


    def 'should emit channel items until the condition is verified' () {

        when:
        def result = Channel.from(1,2,3,4).until { it == 3 }
        then:
        result.val == 1
        result.val == 2
        result.val == Channel.STOP

        when:
        result = Channel.from(1,2,3).until { it == 5 }
        then:
        result.val == 1
        result.val == 2
        result.val == 3
        result.val == Channel.STOP

    }

}