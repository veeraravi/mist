package mist.api

trait FromAny[A] {
  def apply(a: Any): Option[A]
}

//TODO: double, float, date?
trait FromAnyInstances {

  def createInst[A](f: Any => Option[A]): FromAny[A] = new FromAny[A] {
    override def apply(a: Any): Option[A] = f(a)
  }

  implicit val forInt: FromAny[Int] = createInst {
    case i: Int => Some(i)
    case _ => None
  }

  implicit val forString: FromAny[String] = createInst {
    case s: String => Some(s)
    case _ => None
  }

  implicit def forSeq[A](implicit underlying: FromAny[A]): FromAny[Seq[A]] = createInst {
    case seq: Seq[_] =>
      val optA = seq.map(a => underlying.apply(a))
      if (optA.exists(_.isEmpty)) {
        None
      } else {
        Some(optA.map(_.get))
      }
    case x => None
  }

}

object FromAnyInstances extends FromAnyInstances

