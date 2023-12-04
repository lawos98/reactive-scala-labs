package EShop.lab2

import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                                             extends Command
  case class RemoveItem(item: Any)                                          extends Command
  case object ExpireCart                                                    extends Command
  case class StartCheckout(orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ConfirmCheckoutCancelled                                      extends Command
  case object ConfirmCheckoutClosed                                         extends Command
  case class GetItems(sender: ActorRef[Cart])                               extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
  case class ItemAdded(item: Any)                                          extends Event
  case class ItemRemoved(item: Any)                                        extends Event
  case object CartEmptied                                                  extends Event
  case object CartExpired                                                  extends Event
  case object CheckoutClosed                                               extends Event
  case object CheckoutCancelled                                            extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable]) {
    def cart: Cart
  }
  case object Empty extends State(None) {
    def cart: Cart = Cart.empty
  }
  case class NonEmpty(cart: Cart, timer: Cancellable) extends State(Some(timer))
  case class InCheckout(cart: Cart)                   extends State(None)
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable = {
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)
  }

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case AddItem(item) =>
          nonEmpty(Cart(Seq(item)), scheduleTimer(context))
        case GetItems(sender) =>
          sender ! Cart.empty
          Behaviors.same
        case _ =>
          Behaviors.unhandled
      }
    }

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case AddItem(item) =>
          nonEmpty(cart.addItem(item), timer)
        case RemoveItem(item) if cart.contains(item) && cart.size == 1 =>
          timer.cancel()
          empty
        case RemoveItem(item) if cart.contains(item) =>
          nonEmpty(cart.removeItem(item), timer)
        case StartCheckout(orderManager) =>
          val checkout = context.spawn(new TypedCheckout(context.self).start, "checkout")
          orderManager ! OrderManager.ConfirmCheckoutStarted(checkout)
          checkout ! TypedCheckout.StartCheckout
          inCheckout(cart)
        case ExpireCart =>
          empty
        case GetItems(sender) =>
          sender ! cart
          Behaviors.same
        case _ =>
          Behaviors.unhandled
      }
    }

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) => msg match {
      case ConfirmCheckoutCancelled => nonEmpty(cart, scheduleTimer(context))
      case ConfirmCheckoutClosed => empty
      case _ => Behaviors.unhandled
    }
  )

}
