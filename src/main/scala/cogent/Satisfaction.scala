package cogent

import cogent.Logger
import scala.compiletime.ops.int

object Satisfaction {
    def findAtoms(logger : Logger, guard : Guard ) : Set[Guard] = {
        guard match {
            case Guard.ElseGuard() =>
                logger.never( "Internal error: else guard in tautology checking")
                Set()
            case Guard.OKGuard( ) =>
                Set( guard )
            case Guard.InGuard( name : String ) =>
                Set( guard )
            case Guard.NamedGuard( name : String ) =>
                Set( guard )
            case Guard.RawGuard( rawCCode : String ) =>
                Set( guard )
            case Guard.NotGuard( operand : Guard ) =>
                findAtoms( logger, operand )
            case Guard.AndGuard( left : Guard, right : Guard ) =>
                findAtoms(logger,  left ) union findAtoms(logger, right) 
            case Guard.OrGuard( left: Guard, right : Guard ) =>
                findAtoms(logger,  left ) union findAtoms(logger, right) 
            case Guard.ImpliesGuard( left : Guard, right : Guard ) =>
                findAtoms(logger,  left ) union findAtoms(logger, right) 
        }
    }

    def evaluate( guard : Guard, sigma : Map[Guard,Boolean] ) : Boolean = {
        guard match {
            case Guard.ElseGuard( ) =>
                false
            case Guard.OKGuard( ) =>
                sigma(guard)
            case Guard.InGuard( name : String ) =>
                sigma(guard)
            case Guard.NamedGuard( name : String ) =>
                sigma(guard)
            case Guard.RawGuard( rawCCode : String ) =>
                sigma(guard)
            case Guard.NotGuard( operand : Guard ) =>
                !evaluate(operand, sigma)
            case Guard.AndGuard( left : Guard, right : Guard ) =>
                evaluate(left, sigma) && evaluate(right, sigma)
            case Guard.OrGuard( left: Guard, right : Guard ) =>
                evaluate(left, sigma) || evaluate(right, sigma)
            case Guard.ImpliesGuard( left : Guard, right : Guard ) =>
                !evaluate(left, sigma) || evaluate(right, sigma)
        }
    }

    def is_satisfiable( logger : Logger, guard : Guard ) : Boolean = {
        val atoms = findAtoms( logger, guard )
        val atomList = List.from(atoms)
        val n = atomList.size
        val interps : LazyList[Map[Guard,Boolean]] = 
            for i <- LazyList.range(0,(1<<n)) yield (
                Map.from( for j <- 0 until n yield
                    (atomList(j), 0 != ((1<<j)&i) )))
        interps.exists( (sigma) => evaluate(guard, sigma))
    }

    def is_tautology( logger : Logger, g0 : Guard ) : Boolean = {
        ! is_satisfiable( logger, Guard.NotGuard(g0) )
    }

    def ambiguity( logger : Logger, g0 : Guard, g1 : Guard) : Boolean = {
        is_satisfiable( logger, Guard.AndGuard(g0, g1) )
    }

    def possibly_none( logger: Logger, gs : Iterable[Guard] ) : Boolean = {
        if gs.isEmpty then
            true
        else
            val disjunction = gs.reduce((g0, g1) => Guard.OrGuard(g0, g1))
            is_satisfiable( logger, Guard.NotGuard(disjunction) )
    }

    def definitely_at_least_one( logger: Logger, gs : Iterable[Guard] ) : Boolean = {
        if gs.isEmpty then
            false
        else
            val disjunction = gs.reduce((g0, g1) => Guard.OrGuard(g0, g1))
            is_tautology( logger, disjunction )
    }

    def entails( logger : Logger, g0 : Guard, g1 : Guard ) : Boolean = {
        is_tautology( logger, Guard.ImpliesGuard(g0, g1) )
    }

    def sort_by_entailment( logger : Logger, es : Seq[Edge] ) = {
        import scala.util.Sorting
        Sorting.stableSort(es, (e0, e1) => entails( logger, e0.guardOpt.head, e1.guardOpt.head ) )
    }
}