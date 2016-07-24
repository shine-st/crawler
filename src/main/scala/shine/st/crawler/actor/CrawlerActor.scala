package shine.st.crawler.actor

import akka.actor.Props
import com.sksamuel.elastic4s.RichSearchResponse
import org.joda.time.DateTime
import shine.st.common.akka.Message.{Check, End, EndComplete, Wait}
import shine.st.crawler.Dump
import shine.st.crawler.actor.MovieCrawlerActor.{Complete, CrawlerDate, DailyList}
import shine.st.crawler.model.CrawlerModel.{DailyInfo, MovieInfo}
import shine.st.crawler.model.MovieModel
import shine.st.crawler.search.MovieUtils

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by shinest on 2016/7/12.
  */

class MovieCrawlerActor extends CommonActor {
  val queryActor = context.actorOf(WebQueryActor.props, "queryActor")
  val cleanActor = context.actorOf(DataCleanActor.props, "cleanActor")
  val searchActor = context.actorOf(SearchActor.props, "searchActor")

  var endPointConut = 0
  var completeCount = 0

  var endPointDateCount = 0
  var completeDateCount = 0

  var hasEnded = 0


  override def realReceive: Receive = {
    case CrawlerDate(dateList) =>
      Dump.logger.debug("movie query start...")
      endPointDateCount = dateList.size
      dateList.foreach { date => queryActor ! WebQueryActor.Daily(date) }

    case DailyList(list) =>
      endPointConut += (list.size * 2)
      completeDateCount += 1

      list.foreach { dailyInfo =>
        cleanActor ! dailyInfo
        val movie = MovieUtils.findMovie(dailyInfo.movieInfo.title)
        movie.onSuccess { case movie: RichSearchResponse => if (movie.totalHits == 0) {
          queryActor ! WebQueryActor.Movie(dailyInfo.movieInfo)
        } else {
          self ! Complete(1)
        }
        }
      }

    case movieInfo: MovieInfo =>
      cleanActor ! movieInfo

    case d: MovieModel.Daily =>
      searchActor ! d

    case m: MovieModel.Movie =>
      Dump.logger.debug(s"movie: '${m.movieName}' to crate")
      searchActor ! m

    case EndComplete =>
      hasEnded += 1

      if (hasEnded == 4) {
        Dump.logger.debug("shutdown movie crawler system...")
        context.system.shutdown()
      }

    case Check =>
      Dump.logger.debug(s"end point date count ($endPointDateCount : $completeDateCount), end point count ($endPointConut : $completeCount)")
      if (endPointDateCount == completeDateCount && endPointConut == completeCount) {
        Dump.logger.debug("graceful stop actor start..")
        queryActor ! End
        cleanActor ! End
        searchActor ! End
        sender ! End
      } else {
        sender ! Wait(10)
      }


    case Complete(r) =>
      completeCount += r

    case _ => Dump.logger.debug("error")
  }

}

object MovieCrawlerActor {
  def props(): Props = Props[MovieCrawlerActor]

  case class CrawlerDate(d: List[DateTime])

  case class DailyList(list: List[DailyInfo])

  case class Complete(r: Int)

}