package com.nparry.smircd.protocol

case class ParsedCommand(
  prefix: Option[String],
  command: String,
  params: List[String]
) {

  def raw = {
    List[Iterable[String]](
      prefix.map { ":" + _ },
      Some(command),
      rawParams).flatten.mkString(" ")
  }

  private lazy val rawParams = params.zipWithIndex.map { x =>
    if (x._2 != params.size - 1) x._1
    else
      if (x._1.isEmpty()) ":"
      else if (x._1.contains(" ")) ":" + x._1
      else x._1
  }

}

