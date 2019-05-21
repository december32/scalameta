package org.scalameta.internal

import scala.collection.immutable
import scala.collection.mutable.ArrayBuffer

trait MacroCompat

object ScalaCompat {
  type IndexedSeqOptimized[+A] = scala.collection.IndexedSeqOptimized[A, immutable.IndexedSeq[A]]
  implicit class XtensionScala213ToSeq[T](seq: collection.Seq[T]) {
    def toScalaSeq: Seq[T] = seq
  }
}
