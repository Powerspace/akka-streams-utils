# akka-streams-utils [![Build Status](https://travis-ci.org/Powerspace/akka-streams-utils.svg?branch=master)](https://travis-ci.org/Powerspace/akka-streams-utils)

Here, we can find some Akka Streams components Powerspace is using across its projects.

# AckConsumableAkkaSource

It's a generic source that pulls a source and can ack the messages in the background according to certain thresholds (time and count). 

Example:
```scala
val source = Source.fromGraph(new AckConsumableAkkaSource(consumer,
                                                          ackMaxSize = 1000,
                                                          ackPeriod = 2 seconds))
source.map(_ + 1).runForeach(println)
```

Every 1000 items or 2 seconds (if less than 1000 items), the consumer will be _acked_.

The consumer has to respect a given interface `AckConsumableStorage` defined as:

```scala
trait AckConsumableStorage[T] extends EventsStorage[T] with ConsumableStorage[T] with AckableStorage[T]

// any message should must a distinct key to be ackable
case class KeyedMessage[T](key: String, data: T)

trait EventsStorage[T] {
  def name: String
}

trait ConsumableStorage[T] { self: EventsStorage[T] =>
  def consumeAsBytes(): Future[List[KeyedMessage[Array[Byte]]]]
  def consumeAsEvents(): Future[List[KeyedMessage[T]]]
}

trait AckableStorage[T] { self: EventsStorage[T] =>
  def ack(ids: Seq[String]): Future[Unit]
}
```

It's particularly useful when we are dealing with Google PubSub for instance where we need to ack every messages in batch.

Note: the `consumeAsBytes` may not be necessary and may be removed in the future.

# TODO

- Add the publish piece into build.sbt
- Add PubSub consumer
