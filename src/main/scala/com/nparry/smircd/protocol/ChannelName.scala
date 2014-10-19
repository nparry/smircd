package com.nparry.smircd.protocol

object ChannelName {

  def list[E](s: String, f: (IllegalArgumentException) => E): Either[E, List[ChannelName]] = {
    try
      Right(s.split(",").toList.map(ChannelName.apply _))
    catch {
      case e: IllegalArgumentException => Left(f(e))
    }
  }

  def listOfValid[E](s: String, f: (String) => E): List[Either[E, ChannelName]] = {
    s.split(",").toList.map { name =>
      if (isValidChannelName(name))
        Right(ChannelName(name))
      else
        Left(f(name))
    }
  }

  def single[E](s: String, f: (IllegalArgumentException) => E): Either[E, ChannelName] = {
    try
      Right(ChannelName(s))
    catch {
      case e: IllegalArgumentException => Left(f(e))
    }
  }

  def keyList(s: String) = s.split(",").toList

  def isValidChannelName(name: String) = {
    if (!(name.startsWith("#") || name.startsWith("&")))
      false
    else if (name.contains(" ") || name.contains(",") || name.isEmpty())
      false
    else
      true
  }
}

case class ChannelName(name: String) extends Ordered[ChannelName] {
  if (!ChannelName.isValidChannelName(name)) throw new IllegalArgumentException(name)
  def compare(that: ChannelName) =  name.compare(that.name)
}

