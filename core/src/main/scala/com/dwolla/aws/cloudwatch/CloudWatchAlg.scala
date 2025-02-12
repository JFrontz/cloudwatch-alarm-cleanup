package com.jfrontz.aws.cloudwatch

import aws.AWS
import aws.AWS.CloudWatch
import aws.AWSXRay.captureAWSClient
import aws.cloudwatch._
import com.jfrontz.awssdk.cloudwatch._
import cats._
import cats.effect._
import com.jfrontz.awssdk.ExecuteVia._
import com.jfrontz.awssdk.PaginatedAwsClient._
import fs2._

trait CloudWatchAlg[F[_]] {
  def listAllCloudWatchAlarms(): Stream[F, MetricAlarm]
  //noinspection MutatorLikeMethodIsParameterless
  def removeAlarms: Pipe[F, MetricAlarm, DeleteAlarmsOutput]
  def clearAlarm(metricAlarm: MetricAlarm): F[Unit]
}

object CloudWatchAlg {
  val deleteAlarmsParallelismFactor = 5
  val maximumNumberOfAlarmsToDeleteAtOnce = 100

  private def acquireCloudWatchClient[F[_] : Sync] = Sync[F].delay(captureAWSClient(new AWS.CloudWatch()))
  private def shutdown[F[_] : Applicative]: CloudWatch => F[Unit] = _ => Applicative[F].unit

  def apply[F[_]](implicit C: CloudWatchAlg[F]): CloudWatchAlg[F] = C

  def resource[F[_] : ConcurrentEffect]: Resource[F, CloudWatchAlg[F]] =
    Resource.make(acquireCloudWatchClient[F])(shutdown[F]).map(new CloudWatchAlgImpl[F](_))

  private[cloudwatch] class CloudWatchAlgImpl[F[_] : ConcurrentEffect](client: CloudWatch) extends CloudWatchAlg[F] {
    override def listAllCloudWatchAlarms(): Stream[F, MetricAlarm] =
      (() => DescribeAlarmsInput())
        .fetchAll[F](client.describeAlarms)(_.MetricAlarms.toSeq)

    override def removeAlarms: Pipe[F, MetricAlarm, DeleteAlarmsOutput] =
      _
        .map(_.AlarmName)
        .chunkN(maximumNumberOfAlarmsToDeleteAtOnce, allowFewer = true)
        .map(_.toList)
        .map(DeleteAlarmsInput(_: _*))
        .map(_.executeVia[F](client.deleteAlarms))
        .map(Stream.eval)
        .parJoin(deleteAlarmsParallelismFactor)

    override def clearAlarm(metricAlarm: MetricAlarm): F[Unit] = {
      val input = SetAlarmStateInput()
      input.AlarmName = metricAlarm.AlarmName
      input.StateValue = "OK"
      input.StateReason = "Marking OK before deletion"
      input.executeVia[F](client.setAlarmState)
    }
  }

}
