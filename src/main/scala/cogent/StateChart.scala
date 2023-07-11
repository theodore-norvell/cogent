package cogent

class StateChart(
        val name : String,
        val location : String,
        val root : Node,
        val nodeSet : Set[Node],
        val edgeSet : Set[Edge],
        val parentMap : Map[Node, Node],
        val isFirst : Boolean
    ) :
    
    assert( root.isOrState )

    val nodes : Seq[Node] = nodeSet.toSeq.sortBy( _.getFullName )

    val edges : Seq[Edge] = edgeSet.toSeq.sortBy( _.toString )

    val description = s"${if isFirst then "Statechart" else "Submachine"} $name at $location"

    def show : String =
        val rootString = root.show
        val nodesString = nodes.map( _.getFullName )
                                .fold("{ ")( (x,y) => s"$x\n  $y") + " }\n"
        val edgesString = edges.map( (x) => x.toString )
                                .fold("{ ")( (x,y) => s"$x\n  $y") + " }"
        val parentString = parentMap.map( (k,v) => s"Parent of ${k.getFullName} is ${v.getFullName}" )
                                .fold("")( (x,y) => x + "\n" + y )
        return s"StateChart at $location\n$rootString\n" +
                s"$nodesString\n$edgesString\n$parentString\n"
    end show

    def parentOf( node : Node ) : Node = parentMap( node ) 

    def leastCommonOrOf( source : Node, target : Node ) : Node =
        assert( nodes contains source)
        assert( nodes contains target)
        assert( source != root )
        assert( target != root )
        var p = source
        var q = target
        // Start with the parents
        p = parentMap(p) 
        q = parentMap(q)
        // Climb up from the deeper one until p and q are at the same depth.
        while( p.getDepth > q.getDepth ) p = parentMap(p)
        while( q.getDepth > p.getDepth ) q = parentMap(q)
        assert( p.getDepth == q.getDepth )
        // Keep climbing until we have a common ancestor that is an Or.
        // Since the root is an or state, this must exist.
        while( p != q || ! p.isOrState ) { p = parentMap(p) ; q = parentMap(q) }
        p
    end leastCommonOrOf
end StateChart





