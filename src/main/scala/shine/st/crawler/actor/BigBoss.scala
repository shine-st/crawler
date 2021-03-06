package shine.st.crawler.actor

import akka.actor.{ActorRef, Props}
import shine.st.crawler.model.Message.{Check, Wait}

/**
  * Created by  on 2016/7/20.
  */
class BigBoss(val watchActor: ActorRef) extends BaseActor {

  override def adapterReceive: Receive = {
    case Check =>
      watchActor ! Check

    case Wait(seconds) =>
      Thread.sleep(seconds * 1000)
      watchActor ! Check
  }
}

object BigBoss {
  def apply(watchActor: ActorRef): Props = Props(classOf[BigBoss], watchActor)
}
