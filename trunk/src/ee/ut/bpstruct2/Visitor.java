package ee.ut.bpstruct2;

import java.util.Set;

import de.hpi.bpt.process.Node;
import de.hpi.bpt.process.Process;
import ee.ut.bpstruct.CannotStructureException;
import ee.ut.bpstruct2.jbpt.Pair;

public interface Visitor {

	void visitPolygon(Process proc, Set<Pair> ledges, Set<Node> lvertices,
			Node entry, Node exit);

	void visitBond(Process proc, Set<Pair> ledges, Set<Node> lvertices,
			Node entry, Node exit);

	void visitRigid(Process proc, Set<Pair> ledges, Set<Node> lvertices,
			Node entry, Node exit) throws CannotStructureException;

}
