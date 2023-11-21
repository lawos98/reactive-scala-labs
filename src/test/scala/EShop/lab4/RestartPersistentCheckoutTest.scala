package EShop.lab4

import EShop.lab2.TypedCartActor
import EShop.lab3.OrderManager
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.SerializationSettings
import akka.persistence.typed.PersistenceId
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.concurrent.duration._
import scala.util.Random

class RestartPersistentCheckoutTest
  extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config)
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with BeforeAndAfterEach {

  override def afterAll: Unit = testKit.shutdownTestKit()

  import EShop.lab2.TypedCheckout._

  private val cartActorProbe = testKit.createTestProbe[TypedCartActor.Command]()

  private val orderManagerProbe = testKit.createTestProbe[OrderManager.Command]

  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[Command, Event, State](
      system,
      new PersistentCheckout {
        override val timerDuration: FiniteDuration = 1.second
      }.apply(cartActorProbe.ref, generatePersistenceId),
      SerializationSettings.disabled
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  val deliveryMethod = "post"
  val paymentMethod  = "paypal"

  def generatePersistenceId: PersistenceId = PersistenceId.ofUniqueId(Random.alphanumeric.take(256).mkString)

  it should "be in selectingDelivery state after checkout start after restart" in {
    eventSourcedTestKit.runCommand(StartCheckout)
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true
  }

  it should "be in cancelled state after cancel message received in selectingDelivery State after restart" in {
    eventSourcedTestKit.runCommand(StartCheckout)
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    eventSourcedTestKit.runCommand(CancelCheckout)
    val resultCancelCheckout = eventSourcedTestKit.restart()

    resultCancelCheckout.state shouldBe Cancelled
  }

  it should "be in cancelled state after expire checkout timeout in selectingDelivery state after restart" in {
    eventSourcedTestKit.runCommand(StartCheckout)
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    Thread.sleep(2000)

    eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))
    val resultSelectDelivery = eventSourcedTestKit.restart()

    resultSelectDelivery.state shouldBe Cancelled
  }

  it should "be in selectingPayment state after delivery method selected after restart" in {
    eventSourcedTestKit.runCommand(StartCheckout)
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))
    val resultSelectDelivery = eventSourcedTestKit.restart()

    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true
  }

  it should "be in cancelled state after cancel message received in selectingPayment State after restart" in {
    eventSourcedTestKit.runCommand(StartCheckout)
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))
    val resultSelectDelivery = eventSourcedTestKit.restart()

    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true

    eventSourcedTestKit.runCommand(CancelCheckout)
    val resultCancelCheckout = eventSourcedTestKit.restart()

    resultCancelCheckout.state shouldBe Cancelled
  }

  it should "be in cancelled state after expire checkout timeout in selectingPayment state after restart" in {
    eventSourcedTestKit.runCommand(StartCheckout)
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))
    val resultSelectDelivery = eventSourcedTestKit.restart()

    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true

    Thread.sleep(2000)

    eventSourcedTestKit.runCommand(SelectPayment(paymentMethod, orderManagerProbe.ref))
    val resultSelectPayment = eventSourcedTestKit.restart()

    resultSelectPayment.state shouldBe Cancelled
  }

  it should "be in processingPayment state after payment selected after restart" in {
    eventSourcedTestKit.runCommand(StartCheckout)
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))
    val resultSelectDelivery = eventSourcedTestKit.restart()

    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true

    eventSourcedTestKit.runCommand(SelectPayment(paymentMethod, orderManagerProbe.ref))
    val resultSelectPayment = eventSourcedTestKit.restart()

    resultSelectPayment.state.isInstanceOf[ProcessingPayment] shouldBe true
  }

  it should "be in cancelled state after cancel message received in processingPayment State after restart" in {
    eventSourcedTestKit.runCommand(StartCheckout)
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))
    val resultSelectDelivery = eventSourcedTestKit.restart()

    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true

    eventSourcedTestKit.runCommand(SelectPayment(paymentMethod, orderManagerProbe.ref))
    val resultSelectPayment = eventSourcedTestKit.restart()

    resultSelectPayment.state.isInstanceOf[ProcessingPayment] shouldBe true

    eventSourcedTestKit.runCommand(CancelCheckout)
    val resultCancelCheckout = eventSourcedTestKit.restart()

    resultCancelCheckout.state shouldBe Cancelled
  }

  it should "be in cancelled state after expire checkout timeout in processingPayment state after restart" in {
    eventSourcedTestKit.runCommand(StartCheckout)
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))
    val resultSelectDelivery = eventSourcedTestKit.restart()

    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true

    eventSourcedTestKit.runCommand(SelectPayment(paymentMethod, orderManagerProbe.ref))
    val resultSelectPayment = eventSourcedTestKit.restart()

    resultSelectPayment.state.isInstanceOf[ProcessingPayment] shouldBe true

    Thread.sleep(2000)

    eventSourcedTestKit.runCommand(ConfirmPaymentReceived)
    val resultReceivePayment = eventSourcedTestKit.restart()

    resultReceivePayment.state shouldBe Cancelled
  }

  it should "be in closed state after payment completed after restart" in {
    eventSourcedTestKit.runCommand(StartCheckout)
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))
    val resultSelectDelivery = eventSourcedTestKit.restart()

    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true

    eventSourcedTestKit.runCommand(SelectPayment(paymentMethod, orderManagerProbe.ref))
    val resultSelectPayment = eventSourcedTestKit.restart()

    resultSelectPayment.state.isInstanceOf[ProcessingPayment] shouldBe true

    eventSourcedTestKit.runCommand(ConfirmPaymentReceived)
    val resultReceivePayment = eventSourcedTestKit.restart()

    resultReceivePayment.state shouldBe Closed
  }

  it should "not change state after cancel msg in completed state after restart" in {
    eventSourcedTestKit.runCommand(StartCheckout)
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))
    val resultSelectDelivery = eventSourcedTestKit.restart()

    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true

    eventSourcedTestKit.runCommand(SelectPayment(paymentMethod, orderManagerProbe.ref))
    val resultSelectPayment = eventSourcedTestKit.restart()

    resultSelectPayment.state.isInstanceOf[ProcessingPayment] shouldBe true

    eventSourcedTestKit.runCommand(ConfirmPaymentReceived)
    val resultReceivePayment = eventSourcedTestKit.restart()

    resultReceivePayment.state shouldBe Closed

    eventSourcedTestKit.runCommand(CancelCheckout)
    val resultCancelCheckout = eventSourcedTestKit.restart()

    resultCancelCheckout.state shouldBe Closed
  }
}
