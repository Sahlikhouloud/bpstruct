package ee.ut.bpstruct2;

public class RestrictedVisitorFactory implements VisitorFactory {
	public Visitor createVisitor(Helper helper) {
		return new RestrictedRestructurerVisitor(helper);
	}

}
