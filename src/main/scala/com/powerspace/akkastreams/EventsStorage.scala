package com.powerspace.akkastreams

import scala.concurrent.Future

// any message should have a key
case class KeyedMessage[T](key: String, data: T)

trait EventsStorage[T] {
  def name: String
}

trait PublishableStorage[T] { self: EventsStorage[T] =>
  def publish(event: T)
}

trait TrashableStorage[T] { self: EventsStorage[T] =>
  def trash(event: T)
}

trait PublishableWithTrashStorage[T] extends EventsStorage[T] with PublishableStorage[T] with TrashableStorage[T]

/**
  * A storage you can consume messages from.
  *
  * 2 methods: one to get the events typed, the other, just as bytes when you don't
  * care of the typed form and you just want to forward the bytes.
  */
trait ConsumableStorage[T] { self: EventsStorage[T] =>
  def consumeAsBytes(): Future[List[KeyedMessage[Array[Byte]]]]
  def consumeAsEvents(): Future[List[KeyedMessage[T]]]
}

trait AckableStorage[T] { self: EventsStorage[T] =>
  def ack(ids: Seq[String]): Future[Unit]
}

trait AckConsumableStorage[T] extends EventsStorage[T] with ConsumableStorage[T] with AckableStorage[T]
