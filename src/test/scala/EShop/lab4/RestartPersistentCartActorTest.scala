package EShop.lab4

import EShop.lab2.Cart
import EShop.lab3.OrderManager
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.SerializationSettings
import akka.persistence.typed.PersistenceId
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.concurrent.duration._
import scala.util.Random

class RestartPersistentCartActorTest
  extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config)
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with BeforeAndAfterEach {

  override def afterAll: Unit = testKit.shutdownTestKit()

  import EShop.lab2.TypedCartActor._

  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[Command, Event, State](
      system,
      new PersistentCartActor {
        override val cartTimerDuration: FiniteDuration = 1.second
      }.apply(generatePersistenceId),
      SerializationSettings.disabled
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  def generatePersistenceId: PersistenceId = PersistenceId.ofUniqueId(Random.alphanumeric.take(256).mkString)

  it should "add one item after restart after restart" in {
    val result = eventSourcedTestKit.runCommand(AddItem("Hamlet"))

    result.event.isInstanceOf[ItemAdded] shouldBe true
    result.state.isInstanceOf[NonEmpty] shouldBe true

    val restartResult = eventSourcedTestKit.restart()

    restartResult.state.isInstanceOf[NonEmpty] shouldBe true
    restartResult.state.cart shouldEqual Cart.empty.addItem("Hamlet")
  }

  it should "be empty after adding new item and removing it after restart after restart" in {
    val resultAdd = eventSourcedTestKit.runCommand(AddItem("Storm"))

    resultAdd.event.isInstanceOf[ItemAdded] shouldBe true
    resultAdd.state.isInstanceOf[NonEmpty] shouldBe true

    eventSourcedTestKit.runCommand(RemoveItem("Storm"))
    val resultRemove = eventSourcedTestKit.restart()

    resultRemove.state shouldBe Empty
  }

  it should "contain one item after adding new item and removing not existing one after restart after restart" in {
    val resultAdd = eventSourcedTestKit.runCommand(AddItem("Romeo & Juliet"))

    resultAdd.event.isInstanceOf[ItemAdded] shouldBe true
    resultAdd.state.isInstanceOf[NonEmpty] shouldBe true

    eventSourcedTestKit.runCommand(RemoveItem("Macbeth"))
    val resultRemove = eventSourcedTestKit.restart()

    resultRemove.state.isInstanceOf[NonEmpty] shouldBe true
  }

  it should "change state to inCheckout from nonEmpty after restart" in {
    val resultAdd = eventSourcedTestKit.runCommand(AddItem("Romeo & Juliet"))

    resultAdd.event.isInstanceOf[ItemAdded] shouldBe true
    resultAdd.state.isInstanceOf[NonEmpty] shouldBe true

    eventSourcedTestKit.runCommand(StartCheckout(testKit.createTestProbe[OrderManager.Command]().ref))
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[InCheckout] shouldBe true
  }

  it should "cancel checkout properly after restart" in {
    val resultAdd = eventSourcedTestKit.runCommand(AddItem("Cymbelin"))

    resultAdd.event.isInstanceOf[ItemAdded] shouldBe true
    resultAdd.state.isInstanceOf[NonEmpty] shouldBe true

    eventSourcedTestKit.runCommand(StartCheckout(testKit.createTestProbe[OrderManager.Command]().ref))
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[InCheckout] shouldBe true

    eventSourcedTestKit.runCommand(ConfirmCheckoutCancelled)
    val resultCancelCheckout = eventSourcedTestKit.restart()

    resultCancelCheckout.state.isInstanceOf[NonEmpty] shouldBe true
  }

  it should "close checkout properly after restart" in {
    val resultAdd = eventSourcedTestKit.runCommand(AddItem("Cymbelin"))

    resultAdd.event.isInstanceOf[ItemAdded] shouldBe true
    resultAdd.state.isInstanceOf[NonEmpty] shouldBe true

    eventSourcedTestKit.runCommand(StartCheckout(testKit.createTestProbe[OrderManager.Command]().ref))
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[InCheckout] shouldBe true

    eventSourcedTestKit.runCommand(ConfirmCheckoutClosed)
    val resultCloseCheckout = eventSourcedTestKit.restart()

    resultCloseCheckout.state shouldBe Empty
  }

  it should "not add items when in checkout after restart" in {
    val resultAdd = eventSourcedTestKit.runCommand(AddItem("Cymbelin"))

    resultAdd.event.isInstanceOf[ItemAdded] shouldBe true
    resultAdd.state.isInstanceOf[NonEmpty] shouldBe true

    eventSourcedTestKit.runCommand(StartCheckout(testKit.createTestProbe[OrderManager.Command]().ref))
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state.isInstanceOf[InCheckout] shouldBe true

    eventSourcedTestKit.runCommand(AddItem("Henryk V"))
    val resultAdd2 = eventSourcedTestKit.restart()

    resultAdd2.state.isInstanceOf[InCheckout] shouldBe true
  }

  it should "not change state to inCheckout from empty after restart" in {
    eventSourcedTestKit.runCommand(StartCheckout(testKit.createTestProbe[OrderManager.Command]().ref))
    val resultStartCheckout = eventSourcedTestKit.restart()

    resultStartCheckout.state shouldBe Empty
  }

  it should "expire and back to empty state after given time after restart" in {
    eventSourcedTestKit.runCommand(AddItem("King Lear"))
    val resultAdd = eventSourcedTestKit.restart()

    resultAdd.state.isInstanceOf[NonEmpty] shouldBe true

    Thread.sleep(1500)

    eventSourcedTestKit.runCommand(RemoveItem("King Lear"))
    val resultAdd2 = eventSourcedTestKit.restart()

    resultAdd2.state shouldBe Empty
  }
}
