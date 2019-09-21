package com.urdnot.iot.processor

object DataObjects {
  final case class beerReading(
                                     timeStamp: Option[Long],
                                     beer: Option[Int],
                                     pour: Option[Double],
                                     ticks: Option[Int],
                                     temperatureFarenheit: Option[Double],
                                     temperatureCelcius: Option[Double]
                                   )
}

trait utils {
  private def round(d: Double) = BigDecimal(d).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
  def celsiusToFahrenheit(d: Double) = round(d * 9 / 5 + 32)
  def fahrenheitToCelsius(d: Double) = round((d - 32) * 5 / 9)
}
