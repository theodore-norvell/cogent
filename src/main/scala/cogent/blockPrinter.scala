package cogent

object blockPrinter {
    import net.sourceforge.plantuml.BlockUml
    import net.sourceforge.plantuml.core.Diagram
    import net.sourceforge.plantuml.statediagram.StateDiagram 
    import net.sourceforge.plantuml.cucadiagram.IEntity 
    import net.sourceforge.plantuml.cucadiagram.IGroup
    import net.sourceforge.plantuml.cucadiagram.ILeaf
    import net.sourceforge.plantuml.cucadiagram.Link
    import net.sourceforge.plantuml.cucadiagram.Display
    import scala.jdk.CollectionConverters._

    import Logger.Level._

    def printBlock( block : BlockUml )(using logger : Logger) : Unit =
        val diagram : Diagram = 
            try block.getDiagram() 
            catch (e : Throwable) =>
                logger.log( Fatal, s"Exception getting the diagram ${e.getMessage()} ${e}" )
                return ()
        diagram match
            case ( stateDiagram : StateDiagram ) => 
                logger.log( Debug, "This is a state diagram")
                val rootGroup : IGroup = stateDiagram.getRootGroup() 
                printEntity( rootGroup )
                val links = stateDiagram.getLinks() 
                printLinks( links )
            case _ => logger.log( Debug, "This is not a state diagram")

    def printLinks( links : java.util.List[Link] )(using logger : Logger) = 
        links.forEach( (link : Link) =>
            logger.log( Debug, s"link of type ${link.getType().toString()}")
            val source = link.getEntity1()
            val target = link.getEntity2()
            val label = link.getLabel()
            val sourceAsString = source.getCode().getName() 
            val targetAsString = target.getCode().getName() 
            val labelAsString =
                if( label != null ) label.toString() 
                else "null"
            val lenSource = sourceAsString.length() 
            val lenLabel = labelAsString.length()
            logger.log( Debug, " "*lenSource + "  " + labelAsString)
            logger.log( Debug, s"$sourceAsString--${"-"*lenLabel}->$targetAsString" ) )

    var indentLevel = 0

    def printEntity( entity : IEntity )(using logger : Logger) : Unit =
        val indent1 = "|   "*(indentLevel) + "+--"
        val indent  = "|   "*(indentLevel) + "|  "
        logger.log( Debug, indent1 + s"codeName: ${entity.getCode().getName()}")
        logger.log( Debug, indent + s"ident : ${entity.getIdent().toString()}")
        try logger.log( Debug, indent + s"codeLine is ${entity.getCodeLine()} ")
        catch ( _ => logger.log( Debug, indent + "no codeLine"))
        try logger.log( Debug, indent + s"stereotype is ${entity.getStereotype()} ")
        catch ( _ => logger.log( Debug, indent + "no stereotype"))
        val bodier = try entity.getBodier()
                        catch _ => null
        if( bodier != null)
            val display : Display =
                try bodier.getFieldsToDisplay() catch ( _ => null)
            if display == null then
                logger.log( Debug, indent + "Fields is null or exception")
            else
                logger.log( Debug, indent + s"Fields is ${display}" )
            val labels = bodier.getRawBody() ;
            if( labels == null ) 
                logger.log( Debug, indent + "null labels")
            else
                labels.forEach( (charSeq) => 
                    logger.log( Debug, indent + s"label is [${charSeq.toString()}]")
                )
        else
            logger.log( Debug, indent + "Could not get bodier" ) ;
        if( entity.isGroup() )
            entity match 
                case (group : IGroup) =>
                    logger.log( Debug, indent + "It's a group." )
                    logger.log( Debug, indent + s"Group type is ${group.getGroupType()}" )
                    indentLevel += 1
                    val leaves = group.getLeafsDirect()
                    leaves.forEach {(leaf) => printEntity( leaf ) }
                    val groups = group.getChildren() 
                    groups.forEach {(child) => printEntity(child)}
                    indentLevel -= 1
                case _ => logger.log( Debug, indent + "It's a group that isn't an IGroup!" )
        else 
            entity match 
                case (leaf : ILeaf) =>
                    logger.log( Debug, indent + "It's a leaf." )
                    logger.log( Debug, indent + s"Leaf type is ${leaf.getLeafType().toString()}" )
                case _ => logger.log( Debug, indent + "It's a non-group that isn't an ILeaf!" )
}
