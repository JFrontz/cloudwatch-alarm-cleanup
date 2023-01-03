package com.jfrontz.aws.cloudwatch

import aws.cloudwatch._
import com.jfrontz.aws.ec2.InstanceId

object AlarmsForInstance {
  def byInstanceId(ec2InstanceId: InstanceId)(metricAlarm: MetricAlarm): Boolean =
    metricAlarm.Dimensions.exists { dimension =>
      dimension.Name == "InstanceId" && dimension.Value == ec2InstanceId
    }
}
