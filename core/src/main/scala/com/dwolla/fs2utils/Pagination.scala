package com.jfrontz.fs2utils

import cats.Applicative
import cats.data.Kleisli
import cats.implicits._
import fs2._

object Pagination {
  private sealed trait PageIndicator[S]
  private case class FirstPage[S]() extends PageIndicator[S]
  private case class NextPage[S](token: S) extends PageIndicator[S]
  private case class NoMorePages[S]() extends PageIndicator[S]

  def offsetUnfoldChunkEval[F[_], S, O](f: Option[S] => F[(Chunk[O], Option[S])])
                                         (implicit F: Applicative[F]): Stream[F, O] = {
    def fetchPage(maybeNextPageToken: Option[S]): F[Option[(Chunk[O], PageIndicator[S])]] = {
      f(maybeNextPageToken).map {
        case (chunk, Some(nextToken)) => Option((chunk, NextPage(nextToken)))
        case (chunk, None) => Option((chunk, NoMorePages[S]()))
      }
    }

    Stream.unfoldChunkEval[F, PageIndicator[S], O](FirstPage[S]()) {
      case FirstPage() => fetchPage(None)
      case NextPage(token) => fetchPage(Some(token))
      case NoMorePages() => F.pure(None)
    }
  }

  def offsetUnfoldEval[F[_] : Applicative, S, O](f: Option[S] => F[(O, Option[S])]): Stream[F, O] =
    offsetUnfoldChunkEval(Kleisli(f).map(tuple => (Chunk(tuple._1), tuple._2)).run)
}
