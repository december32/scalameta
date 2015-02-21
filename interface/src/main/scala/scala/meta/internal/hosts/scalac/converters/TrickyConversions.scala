package scala.meta
package internal.hosts.scalac
package converters

import org.scalameta.meta.{Toolkit => MetaToolkit}
import org.scalameta.reflection._
import org.scalameta.invariants._
import org.scalameta.unreachable
import scala.{Seq => _}
import scala.collection.immutable.Seq
import scala.tools.nsc.{Global => ScalaGlobal}
import scala.meta.internal.{ast => m}

// This module provides utilities to do tricky conversions solely within the scala.meta tree hierarchy:
// 1) Constructor calls <-> Types
// 2) Pattern types <-> Types
// These conversions need to account for metadata that we've attached or about to attach to scala.meta trees,
// so we can't move them to `org.scalameta.meta` and need this module to be part of the SemanticContext cake.
// TODO: this file should go away, because theoretically all these methods now have equivalents in semantic/Api.scala
trait TrickyConversions extends GlobalToolkit with MetaToolkit {
  self: Api =>

  protected implicit class RichCtorRefTree(mtpe: m.Type) {
    def ctorRef(gctor: g.Symbol): m.Term = {
      object mTypes {
        def unapply(mtpes: Seq[m.Type.Arg]): Option[Seq[m.Type]] = {
          if (mtpes.forall(_.isInstanceOf[m.Type])) Some(mtpes.map(_.require[m.Type]))
          else None
        }
      }
      val result = mtpe match {
        case m.Type.Name(mvalue) => m.Ctor.Name(mvalue).withDenot(gctor)
        case m.Type.Select(mqual, mname) => m.Ctor.Ref.Select(mqual, m.Ctor.Name(mname.value).withDenot(mqual.originalTpe.requireGet, gctor))
        case m.Type.Project(mqual, mname) => m.Ctor.Ref.Project(mqual, m.Ctor.Name(mname.value).withDenot(mqual.originalTpe.requireGet, gctor))
        case m.Type.Function(mTypes(mparams), mret) => m.Term.ApplyType(m.Ctor.Ref.Function(m.Ctor.Name("=>").withDenot(gctor)), mparams :+ mret)
        case m.Type.Annotate(mtpe, mannots) => m.Term.Annotate(mtpe.ctorRef(gctor), mannots)
        case m.Type.Apply(mtpe, margs) => m.Term.ApplyType(mtpe.ctorRef(gctor), margs)
        case _ => unreachable
      }
      val goriginal = mtpe.scratchpad.collect{case ScratchpadDatum.Original(goriginal) => goriginal}.head
      result.appendScratchpad(ScratchpadDatum.Original(goriginal))
    }
  }

  protected implicit class RichCtorTpeTerm(mtree: m.Term) {
    def ctorTpe: m.Type = {
      def loop(mtree: m.Term): m.Type = {
        val result = mtree match {
          case m.Ctor.Name(mvalue) => m.Type.Name(mvalue).withDenot(mtree.originalPre.requireGet, mtree.originalSym.requireGet)
          case m.Ctor.Ref.Select(mqual, mname) => m.Type.Select(mqual, loop(mname).require[m.Type.Name])
          case m.Ctor.Ref.Project(mqual, mname) => m.Type.Project(mqual, loop(mname).require[m.Type.Name])
          case m.Ctor.Ref.Function(_) => unreachable
          case m.Term.ApplyType(m.Ctor.Ref.Function(_), mtargs) => m.Type.Function(mtargs.init, mtargs.last)
          case m.Term.ApplyType(mcallee, mtargs) => m.Type.Apply(loop(mcallee), mtargs)
          case m.Term.Annotate(mannottee, mannots) => m.Type.Annotate(loop(mannottee), mannots)
          case _ => unreachable
        }
        result.withOriginal(mtree.originalTree.requireGet)
      }
      mtree match {
        case m.Term.Apply(mcallee, _) => mcallee.ctorTpe
        case _ => loop(mtree)
      }
    }
    def ctorArgss: Seq[Seq[m.Term.Arg]] = {
      mtree match {
        case _: m.Ctor.Ref => Nil
        case m.Term.ApplyType(mcallee, _) => mcallee.ctorArgss
        case m.Term.Apply(mcallee, args) => mcallee.ctorArgss :+ args
        case m.Term.Annotate(mannottee, _) => mannottee.ctorArgss
        case _ => unreachable
      }
    }
  }
}