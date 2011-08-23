package ee.ut.bpstruct2;

public class FullVisitorFactory implements VisitorFactory {
	public Visitor createVisitor(Helper helper) {
		return new RestructurerVisitor(helper);
	}

}
