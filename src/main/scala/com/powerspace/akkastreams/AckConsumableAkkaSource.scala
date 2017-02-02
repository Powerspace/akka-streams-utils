package com.powerspace.akkastreams

import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler, TimerGraphStageLogic}
import akka.stream.{Attributes, Outlet, SourceShape}
import com.typesafe.scalalogging.LazyLogging
import kamon.Kamon

import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.Try
import scala.language.postfixOps

/**
  * Pull data from the given consumer and periodically ack the messages to provide at-least-once behavior.
  */
class AckConsumableAkkaSource[T](consumer: AckConsumableStorage[T],
                                 ackPeriod: FiniteDuration = 5 seconds,
                                 ackMaxSize: Int = 1000,
                                 poolTimeout: FiniteDuration = 10 seconds)(implicit ec: ExecutionContext)
  extends GraphStage[SourceShape[T]] with LazyLogging {

  val ackBufferSizeMetrics = Kamon.metrics.counter("ack-buffer-size")
  val ackCount = Kamon.metrics.counter("ack-count")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new TimerGraphStageLogic(shape) {

      private val buffer = mutable.Queue.empty[KeyedMessage[T]]
      private val idsToAck = mutable.Queue.empty[String]

      def ack() = {
        restartTimer()
        if (idsToAck.nonEmpty) {
          logger.debug(s"Acking ${idsToAck.length} messages")
          consumer.ack(idsToAck).foreach(_ => logger.debug("Acked!"))
          idsToAck.clear()
          ackCount.increment()
        } else {
          logger.debug("No message to ack, ignoring...")
        }
      }

      def restartTimer() = {
        schedulePeriodicallyWithInitialDelay("ack", ackPeriod, ackPeriod)
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          // pull messages only if we don't have any in our buffer
          while (buffer.isEmpty && !isClosed(out)) {
            val data = Try(Await.result(consumer.consumeAsEvents(), poolTimeout))
            data.foreach(buffer.enqueue(_: _*))

            if (data.isFailure) {
              logger.error(s"Could not retrieve data from the consumer", data.failed.get)
              failStage(data.failed.get)
            } else {
              val messages = data.get
              if (messages.isEmpty) {
                val delay = (1 second)
                logger.debug(s"No events, retrying in $delay...")
                // wait a bit before pulling again
                Thread.sleep(delay.toMillis)
                ack() // the scheduler does not trigger its "onTimer" thread (probably because we are blocking here)
              } else {
                logger.debug(s"Retrieve ${messages.length} events")
              }
            }
          }

          // we must always have a message here (we must push something) otherwise the graph will "stop"
          val oneMessage = buffer.dequeue()
          logger.debug(s"Emit message to the output port (message id:${oneMessage.key})")
          push(out, oneMessage.data)
          idsToAck += oneMessage.key

          ackBufferSizeMetrics.increment()
          if (idsToAck.length >= ackMaxSize) {
            ack()
          }
        }

        override def onDownstreamFinish(): Unit = {
          logger.debug("Downstream has finished, acking the leftover messages...")
          ack()
          super.onDownstreamFinish()
        }
      })

      final protected override def onTimer(timerKey: Any) = ack()

      override def preStart(): Unit = {
        restartTimer()
      }

      override def postStop(): Unit = {
        ack()
      }
    }
  }


  val out = Outlet[T](s"pubsub-source-${consumer.name}")

  override def shape: SourceShape[T] = SourceShape(out)
}
