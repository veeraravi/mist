import io.hydrosphere.mist.lib.{MQTTPublisher, MistJob, StreamingSupport}
import org.apache.spark.rdd.RDD

import scala.collection.mutable

object SimpleSparkStreamingLong extends MistJob with MQTTPublisher with StreamingSupport{
  /** Contains implementation of spark job with ordinary [[org.apache.spark.SparkContext]]
    * Abstract method must be overridden
    *
    * @param parameters user parameters
    * @return result of the job
    */
  override def doStuff(parameters: Map[String, Any]): Map[String, Any] = {

    val ssc = createStreamingContext

    val rddQueue = new mutable.Queue[RDD[Int]]()

    val inputStream = ssc.queueStream(rddQueue)
    val mappedStream = inputStream.map(x => (x % 10, 1))
    val reducedStream = mappedStream.reduceByKey(_ + _)

    reducedStream.print()

    reducedStream.foreachRDD{ (rdd, time) =>
      publish(Map(
        "time" -> time,
        "length" -> rdd.collect().length,
        "collection" -> rdd.collect().toList.toString
      ))
    }

    ssc.start()

    for (i <- 1 to 3000) {
      rddQueue.synchronized {
        rddQueue += ssc.sparkContext.makeRDD(1 to 1000, 10)
      }
      Thread.sleep(100)
    }
    ssc.stop(false)

    Map.empty[String, Any]
  }
}