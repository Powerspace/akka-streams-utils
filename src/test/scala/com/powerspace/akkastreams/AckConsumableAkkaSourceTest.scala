package com.powerspace.akkastreams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Source}
import akka.stream.testkit.scaladsl.TestSink
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, GivenWhenThen, Matchers}

import scala.collection.mutable
import scala.concurrent.Future

class AckConsumableAkkaSourceTest extends FlatSpec with Matchers with GivenWhenThen {

  implicit val system = ActorSystem("test", ConfigFactory.parseString("""akka.stream.materializer.debug.fuzzing-mode = on"""))
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher

  class TestStorage(events: Iterator[List[Int]]) extends AckConsumableStorage[Int] {
    val acknowledged = mutable.ListBuffer[String]()

    override def consumeAsBytes(): Future[List[KeyedMessage[Array[Byte]]]] = ???
    override def consumeAsEvents(): Future[List[KeyedMessage[Int]]] = {
      Future { events.next().map(i => KeyedMessage("key" + i, i)) }
    }
    override def name: String = "storage-test"
    override def ack(ids: Seq[String]): Future[Unit] = {
      acknowledged ++= ids
      Future.successful[Void](null)
    }
  }

  it should "consume and ack properly small chunks" in {
    val testEvents = Iterable(List(1, 2, 3), List(4, 5, 6)).iterator
    val storage = new TestStorage(testEvents)

    val (_, probe) = Source.fromGraph(new AckConsumableAkkaSource(storage, ackMaxSize = 3))
      .toMat(TestSink.probe[Int])(Keep.both)
      .run

    probe.request(4)
    probe.expectNextN(4)
    probe.request(2)
    probe.expectNextN(2)

    storage.acknowledged should contain allOf ("key1", "key2", "key3", "key4", "key5", "key6")
  }

  it should "ack automatically if no new messages are coming" in {
    val testEvents = Iterable(List(1, 2, 3), List(4, 5, 6)).iterator
    val storage = new TestStorage(testEvents)
    import scala.concurrent.duration._

    val (_, probe) = Source.fromGraph(new AckConsumableAkkaSource(storage, ackMaxSize = 100, ackPeriod = 2 seconds))
      .toMat(TestSink.probe[Int])(Keep.both)
      .run

    probe.request(6)
    probe.expectNextN(6)

    storage.acknowledged should be (empty)
    Thread.sleep(2200)
    storage.acknowledged should contain allOf("key1", "key2", "key3", "key4", "key5", "key6")
  }

  it should "consume and ack properly huge collection" in {
    val MAX_MESSAGES_PER_BATCH = 5000

    val testEvents = new Iterator[List[Int]] {
      private val autoincrement = Iterator.from(0)
      override def hasNext: Boolean = true

      override def next(): List[Int] = {
        var length = ((Math.random() * MAX_MESSAGES_PER_BATCH) + 102).toInt
        (0 to length).map(_ => autoincrement.next()).toList
      }
    }

    val storage = new TestStorage(testEvents)

    val (_, probe) = Source.fromGraph(new AckConsumableAkkaSource(storage, ackMaxSize = 10000))
      .async
      .toMat(TestSink.probe[Int])(Keep.both)
      .run

    probe.request(1221321)
    probe.expectNextN(1221321)

    storage.acknowledged should have size 1220000
  }

}
