package controllers

object OutputType extends Enumeration {
  val html: Value = Value("html")
  val pdf: Value = Value("pdf")
  val excel: Value = Value("excel")
}