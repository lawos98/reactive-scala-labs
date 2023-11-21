package EShop.lab4

import EShop.lab2.TypedCartActor
import EShop.lab3.{OrderManager, Payment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCheckout {

  import EShop.lab2.TypedCheckout._

  val timerDuration: FiniteDuration = 1.seconds

  def schedule(context: ActorContext[Command],command : Command): Cancellable =
    context.scheduleOnce(timerDuration, context.self, command)

  def apply(cartActor: ActorRef[TypedCartActor.Command], persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        WaitingForStart,
        commandHandler(context, cartActor),
        eventHandler(context)
      )
    }

  def commandHandler(
    context: ActorContext[Command],
    cartActor: ActorRef[TypedCartActor.Command]
  ): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case WaitingForStart =>
        command match {
          case StartCheckout =>
            Effect.persist(CheckoutStarted)
          case _ =>
            Effect.unhandled
        }

      case SelectingDelivery(_) =>
        command match {
          case SelectDeliveryMethod(method) =>
            Effect.persist(DeliveryMethodSelected(method))
          case ExpireCheckout =>
            Effect.persist(CheckoutCancelled)
          case CancelCheckout =>
            Effect.persist(CheckoutCancelled)
          case _ =>
            Effect.unhandled
        }

      case SelectingPaymentMethod(_) =>
        command match {
          case SelectPayment(payment, orderManager) =>
            val paymentActor = context.spawn(new Payment(payment, orderManager, context.self).start, "payment")
            Effect.persist(PaymentStarted(paymentActor)).thenRun { _ =>
              orderManager ! OrderManager.ConfirmPaymentStarted(paymentActor)
            }
          case ExpireCheckout =>
            Effect.persist(CheckoutCancelled)
          case CancelCheckout =>
            Effect.persist(CheckoutCancelled)
          case _ =>
            Effect.unhandled
        }

      case ProcessingPayment(_) =>
        command match {
          case ConfirmPaymentReceived =>
            Effect.persist(CheckOutClosed).thenRun { _ =>
              cartActor ! TypedCartActor.ConfirmCheckoutClosed
            }
          case ExpirePayment =>
            Effect.persist(CheckoutCancelled)
          case CancelCheckout =>
            Effect.persist(CheckoutCancelled)
          case _ =>
            Effect.unhandled
        }

      case Cancelled =>
        Effect.none

      case Closed =>
        Effect.none
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    event match {
      case CheckoutStarted           => SelectingDelivery(schedule(context,ExpireCheckout))
      case DeliveryMethodSelected(_) => SelectingPaymentMethod(state.timerOpt.get)
      case PaymentStarted(_)         =>
        state.timerOpt.get.cancel()
        ProcessingPayment(schedule(context,ExpirePayment))
      case CheckOutClosed            =>
        state.timerOpt.get.cancel()
        Closed
      case CheckoutCancelled         =>
        state.timerOpt.get.cancel()
        Cancelled
    }
  }
}
