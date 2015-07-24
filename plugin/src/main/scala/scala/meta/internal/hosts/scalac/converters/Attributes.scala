package scala.meta
package internal.hosts.scalac
package converters

import org.scalameta.meta.{Toolkit => MetaToolkit}
import org.scalameta.reflection._
import org.scalameta.invariants._
import org.scalameta.unreachable
import scala.{Seq => _}
import scala.collection.immutable.Seq
import scala.reflect.ClassTag
import scala.tools.nsc.{Global => ScalaGlobal}
import scala.{meta => mapi}
import scala.meta.internal.{ast => m}
import scala.meta.internal.{semantic => s}
import scala.reflect.internal.Flags._

// This module provides functionality for scala.reflect -> scala.meta conversions
// to keep track of scala.reflect attributes.
// By the way, not all semantic information can be expressed with scala.meta's denotations (yet?),
// so sometimes we have to save the original convertees to help us later.
trait Attributes extends GlobalToolkit with MetaToolkit {
  self: Api =>

  protected trait CanHaveDenot[T <: Tree] {
    def withDenot(tree: T, denot: s.Denotation): T
  }
  protected object CanHaveDenot {
    implicit def Name[T <: mapi.Name]: CanHaveDenot[T] = new CanHaveDenot[T] {
      def withDenot(tree: T, denot: s.Denotation): T = tree.require[m.Name].withDenot(denot).asInstanceOf[T]
    }
  }

  protected trait CanHaveTyping[T <: Tree] {
    def withTyping(tree: T, typing: s.Typing): T
  }
  protected object CanHaveTyping {
    implicit def Term[T <: mapi.Term]: CanHaveTyping[T] = new CanHaveTyping[T] {
      def withTyping(tree: T, typing: s.Typing): T = tree.require[m.Term].withTyping(typing).asInstanceOf[T]
    }
    implicit def TermParam[T <: mapi.Term.Param]: CanHaveTyping[T] = new CanHaveTyping[T] {
      // TODO: uncomment this once Term.Param correctly gets the withTyping method
      // def withTyping(tree: T, typing: s.Typing): T = tree.require[m.Term.Param].withTyping(typing).asInstanceOf[T]
      def withTyping(tree: T, typing: s.Typing): T = tree
    }
  }

  protected implicit class RichAttributesTree[T <: m.Tree : ClassTag](mtree: T) {
    private def denot(gpre: g.Type, lsym: l.Symbol): s.Denotation = {
      require(gpre != g.NoType)
      val hpre = {
        if (gpre == g.NoPrefix) s.Prefix.Zero
        else s.Prefix.Type(gpre.toMtype)
      }
      val ssym = symbolTable.convert(lsym)
      s.Denotation.Single(hpre, ssym)
    }
    def withDenot(gsym: g.Symbol)(implicit ev: CanHaveDenot[T]): T = {
      mtree.withDenot(gsym.toLogical)
    }
    def tryDenot(gsym: g.Symbol)(implicit ev: CanHaveDenot[T]): T = {
      if (gsym == null || gsym == g.NoSymbol) mtree
      else mtree.withDenot(gsym)
    }
    def withDenot(lsym: l.Symbol)(implicit ev: CanHaveDenot[T]): T = {
      mtree.withDenot(lsym.gsymbol.prefix, lsym)
    }
    def withDenot(gpre: g.Type, gsym: g.Symbol)(implicit ev: CanHaveDenot[T]): T = {
      mtree.withDenot(gpre, gsym.toLogical)
    }
    def tryDenot(gpre: g.Type, gsym: g.Symbol)(implicit ev: CanHaveDenot[T]): T = {
      if (gpre == null || gpre == g.NoType) mtree
      else if (gsym == null || gsym == g.NoSymbol) mtree
      else mtree.withDenot(gsym)
    }
    def withDenot(gpre: g.Type, lsym: l.Symbol)(implicit ev: CanHaveDenot[T]): T = {
      require(((lsym == l.None) ==> (mtree.isInstanceOf[m.Name.Anonymous])) && debug(mtree, gpre, lsym))
      ev.withDenot(mtree, denot(gpre, lsym))
    }
    def withDenot(ldenot: l.Denotation)(implicit ev: CanHaveDenot[T]): T = {
      require(ldenot.nonEmpty)
      mtree.withDenot(ldenot.pre, ldenot.sym)
    }
    def tryDenot(ldenot: l.Denotation)(implicit ev: CanHaveDenot[T]): T = {
      if (ldenot.isEmpty) mtree
      else mtree.withDenot(ldenot)
    }
    private def typing(gtpe: g.Type): s.Typing = {
      s.Typing.Known(gtpe.toMtypeArg)
    }
    def withTyping(gtpe: g.Type)(implicit ev: CanHaveTyping[T]): T = {
      require(gtpe != null && gtpe != g.NoType)
      ev.withTyping(mtree, typing(gtpe))
    }
    def tryTyping(gtpe: g.Type)(implicit ev: CanHaveTyping[T]): T = {
      if (gtpe == null || gtpe == g.NoType) mtree
      else mtree.withTyping(gtpe)
    }
  }
}