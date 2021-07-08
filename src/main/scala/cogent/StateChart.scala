package cogent

import scala.annotation.showAsInfix

class StateChart(
        val root : Node,
        val nodes : Set[Node],
        val edges : Set[Edge],
        val parentMap : Map[Node, Node]
    ) :

    def show : String =
        val rootString = root.show
        val nodesString = nodes.map( _.getName )
                                .fold("{ ")( (x,y) => s"$x\n  $y") + " }\n"
        val edgesString = edges.map( (x) => x.toString )
                                .fold("{ ")( (x,y) => s"$x\n  $y") + " }"
        val parentString = parentMap.map( (k,v) => s"Parent of ${k.getName} is ${v.getName}" )
                                .fold("")( (x,y) => x + "\n" + y )
        return s"StateChart\n$rootString\n" +
                s"$nodesString\n$edgesString\n$parentString\n"
    end show
end StateChart





