/*  _____                    _
 * |  ___| __ __ _ _ __ ___ (_) __ _ _ __
 * | |_ | '__/ _` | '_ ` _ \| |/ _` | '_ \
 * |  _|| | | (_| | | | | | | | (_| | | | |
 * |_|  |_|  \__,_|_| |_| |_|_|\__,_|_| |_|
 *
 * Copyright 2014 Pellucid Analytics
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package framian

sealed trait Cols[K, A] extends AxisSelectionLike[K, A, Cols] {
  def toRows: Rows[K, A] = this match {
    case Cols.All(e) => Rows.All(e)
    case Cols.Pick(keys, e) => Rows.Pick(keys, e)
    case Cols.OrElse(fst, snd, f) => Rows.OrElse(fst.toRows, snd.toRows, f)
  }
}

object Cols extends AxisSelectionCompanion[Cols] {
  case class All[K, A](extractor: RowExtractor[A, K, Variable]) extends Cols[K, A] with AllAxisSelection[K, A]
  object All extends AllCompanion

  case class Pick[K, S <: Size, A](keys: List[K], extractor: RowExtractor[A, K, S]) extends Cols[K, A] with PickAxisSelection[K, S, A]
  object Pick extends PickCompanion

  case class OrElse[K, A, B](fst: Cols[K, A], snd: Cols[K, A], k: Cell[A] => Cell[B]) extends Cols[K, B] with OrElseAxisSelection[K, A, B]
  object OrElse extends OrElseCompanion
}
