package com.nparry.smircd.protocol

object NickName {

  def list[E](s: String, f: (IllegalArgumentException) => E): Either[E, List[NickName]] = {
    try
      Right(s.split(",").toList.map(NickName.apply _))
    catch {
      case e: IllegalArgumentException => Left(f(e))
    }
  }

  def listOfValid[E](s: String, f: (String) => E): List[Either[E, NickName]] = {
    s.split(",").toList.map { name =>
      if (isValidNickName(name))
        Right(NickName(name))
      else
        Left(f(name))
    }
  }

  def single[E](s: String, f: (IllegalArgumentException) => E): Either[E, NickName] = {
    try
      Right(NickName(s))
    catch {
      case e: IllegalArgumentException => Left(f(e))
    }
  }

  def isValidNickName(name: String): Boolean = {
    return !(name.isEmpty() || name.contains(" "))
  }

  case class Normalized(normalized: String)
  def normalize(s: String): Normalized = {
    return Normalized(s.toLowerCase())
  }
}

case class NickName(name: String) {
  if  (!NickName.isValidNickName(name)) throw new IllegalArgumentException(name)
  val normalized = NickName.normalize(name)
}

