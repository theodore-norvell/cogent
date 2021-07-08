package cogent

import java.io.File 
import java.io.IOException
import net.sourceforge.plantuml.SourceFileReader
import net.sourceforge.plantuml.BlockUml
import net.sourceforge.plantuml.core.Diagram
import scala.collection.JavaConverters._

@main def main : Unit =
    println( "Hello" )
    val f : File = new File("foo.puml")
    if ! f.exists() then
        println( s"File ${f} does not exist.")
        return ()

    val sfr : SourceFileReader = 
        try
            new SourceFileReader( f )
        catch (e : Throwable) =>
                println( s"Exception making SourceFileReader ${e.getMessage()} ${e}" )
                return ()
    var blockList : java.util.List[BlockUml] =
        try
            sfr.getBlocks()
        catch 
            case (e : IOException) =>
                println( s"IOException getting blocklist ${e.getMessage()} ${e}" )
                return ()
            case (e : Throwable) =>
                println( s"Exception getting blocklist ${e.getMessage()} ${e}" )
                return ()
    println( s"There are ${blockList.size()} blocks" )
    blockList.forEach { (block : BlockUml) => printBlock( block ) }
    val blocks = blockList.asScala
    MiddleEnd.processBlocks( blocks )
end main


import net.sourceforge.plantuml.core.Diagram
import net.sourceforge.plantuml.statediagram.StateDiagram 
import net.sourceforge.plantuml.cucadiagram.IEntity 
import net.sourceforge.plantuml.cucadiagram.IGroup
import net.sourceforge.plantuml.cucadiagram.ILeaf
import net.sourceforge.plantuml.cucadiagram.Link
import net.sourceforge.plantuml.cucadiagram.Display

def printBlock( block : BlockUml ) : Unit =
    val diagram : Diagram = 
        try block.getDiagram() 
        catch (e : Throwable) =>
            println( s"Exception getting the diagram ${e.getMessage()} ${e}" )
            return ()
    val description = diagram.getDescription().toString() ;
    println( s"This diagram is a ${description}")
    diagram match
        case ( stateDiagram : StateDiagram ) => 
            println( "This is a state diagram")
            val rootGroup : IGroup = stateDiagram.getRootGroup() 
            printEntity( rootGroup )
            val links = stateDiagram.getLinks() 
            printLinks( links )
        case _ => println( "This is not a state diagram")

def printLinks( links : java.util.List[Link] ) = 
    links.forEach( (link : Link) =>
        println( s"link of type ${link.getType().toString()}")
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
        println( " "*lenSource + "  " + labelAsString)
        println( s"$sourceAsString--${"-"*lenLabel}->$targetAsString" ) )

var indentLevel = 0

def printEntity( entity : IEntity ) : Unit =
    val indent1 = "|   "*(indentLevel) + "+--"
    val indent  = "|   "*(indentLevel) + "|  "
    println( indent1 + s"codeName: ${entity.getCode().getName()}")
    println( indent + s"ident : ${entity.getIdent().toString()}")
    try println( indent + s"codeLine is ${entity.getCodeLine()} ")
    catch ( _ => println( indent + "no codeLine"))
    try println( indent + s"stereotype is ${entity.getStereotype()} ")
    catch ( _ => println( indent + "no stereotype"))
    val bodier = try entity.getBodier()
                    catch _ => null
    if( bodier != null)
        val display : Display =
            try bodier.getFieldsToDisplay() catch ( _ => null)
        if display == null then
            println( indent + "Fields is null or exception")
        else
            println( indent + s"Fields is ${display}" )
        val labels = bodier.getRawBody() ;
        if( labels == null ) 
            println( indent + "null labels")
        else
            labels.forEach( (charSeq) => 
                println( indent + s"label is [${charSeq.toString()}]")
            )
    else
        println( indent + "Could not get bodier" ) ;
    if( entity.isGroup() )
        entity match 
            case (group : IGroup) =>
                println( indent + "It's a group." )
                println( indent + s"Group type is ${group.getGroupType()}" )
                indentLevel += 1
                val leaves = group.getLeafsDirect()
                leaves.forEach {(leaf) => printEntity( leaf ) }
                val groups = group.getChildren() 
                groups.forEach {(child) => printEntity(child)}
                indentLevel -= 1
            case _ => println( indent + "It's a group that isn't an IGroup!" )
    else 
        entity match 
            case (leaf : ILeaf) =>
                println( indent + "It's a leaf." )
                println( indent + s"Leaf type is ${leaf.getLeafType().toString()}" )
            case _ => println( indent + "It's a non-group that isn't an ILeaf!" )
