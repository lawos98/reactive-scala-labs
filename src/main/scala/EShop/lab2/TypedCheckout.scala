package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import scala.language.postfixOps

import scala.concurrent.duration._
import EShop.lab3.{OrderManager, Payment}

object TypedCheckout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                                                                  extends Command
  case class SelectDeliveryMethod(method: String)                                            extends Command
  case object CancelCheckout                                                                 extends Command
  case object ExpireCheckout                                                                 extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ExpirePayment                                                                  extends Command
  case object ConfirmPaymentReceived                                                         extends Command

  sealed trait Event
  case object CheckOutClosed                           extends Event
  case class PaymentStarted(paymentRef: ActorRef[Any]) extends Event
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case StartCheckout => selectingDelivery(context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout))
        case _ => Behaviors.unhandled
      }
    }
  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case SelectDeliveryMethod(_) => selectingPaymentMethod(context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout));
        case ExpireCheckout => cancelled
        case CancelCheckout =>
          timer.cancel()
          cancelled
        case _ => Behaviors.unhandled
      }
    }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case SelectPayment(payment, orderManager) =>
          timer.cancel()
          orderManager ! OrderManager.ConfirmPaymentStarted(
            context.spawn(new Payment(payment, orderManager, context.self).start, "payment")
          )
          processingPayment(context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment))
        case CancelCheckout =>
          timer.cancel()
          cancelled
        case ExpireCheckout => cancelled
        case _ => Behaviors.unhandled
      }
    }

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receive { (_, message) =>
      message match {
        case ConfirmPaymentReceived =>
          timer.cancel()
          cartActor ! TypedCartActor.ConfirmCheckoutClosed
          closed
        case CancelCheckout =>
          timer.cancel()
          cancelled
        case ExpireCheckout => cancelled
        case _ => Behaviors.unhandled
      }
    }

  def cancelled: Behavior[TypedCheckout.Command] =
    Behaviors.receive { (_, message) =>
      message match {
        case _ => Behaviors.stopped
      }
    }

  def closed: Behavior[TypedCheckout.Command] =
    Behaviors.receive { (_, message) =>
      message match {
        case _ => Behaviors.stopped
      }
    }

}
